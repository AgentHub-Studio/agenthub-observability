// Package consumer provides a RabbitMQ consumer that ingests execution events
// and forwards them in batches to a configurable flush handler.
package consumer

import (
	"context"
	"fmt"
	"log/slog"
	"sync/atomic"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

const (
	exchangeName   = "agenthub.orchestrator.events"
	queueName      = "agenthub.observability.events"
	dlxName        = "agenthub.observability.dlx"
	dlqName        = "agenthub.observability.dead"
	prefetchCount  = 10
	batchSize      = 100
	flushInterval  = 60 * time.Second
	backoffInitial = 1 * time.Second
	backoffMax     = 60 * time.Second
)

// Consumer reads execution and node events from RabbitMQ and routes them to
// typed batchers via a Processor.
type Consumer struct {
	url  string
	proc *Processor

	// Internal metrics counters — read via Metrics().
	eventsProcessed atomic.Int64
	eventsErrored   atomic.Int64
}

// Metrics holds a snapshot of consumer operation counters.
type Metrics struct {
	EventsProcessed int64
	EventsErrored   int64
}

// Metrics returns a current snapshot of consumer counters.
func (c *Consumer) Metrics() Metrics {
	return Metrics{
		EventsProcessed: c.eventsProcessed.Load(),
		EventsErrored:   c.eventsErrored.Load(),
	}
}

// New creates a Consumer for the given RabbitMQ URL.
// execFlush receives batches of ExecutionEvent; nodeFlush receives NodeEvent.
func New(url string, execFlush FlushFunc[ExecutionEvent], nodeFlush FlushFunc[NodeEvent]) *Consumer {
	return &Consumer{
		url:  url,
		proc: NewProcessor(execFlush, nodeFlush),
	}
}

// Run starts consuming until ctx is cancelled.  Reconnects automatically with
// exponential backoff (1 s → 2 s → 4 s … capped at 30 s).
func (c *Consumer) Run(ctx context.Context) {
	// Start periodic flush goroutines for all batchers.
	go c.proc.Run(ctx)

	backoff := backoffInitial
	for {
		if err := c.consume(ctx); err != nil {
			slog.Error("consumer error, reconnecting", "err", err, "backoff", backoff)
		}

		select {
		case <-ctx.Done():
			return
		case <-time.After(backoff):
		}

		backoff *= 2
		if backoff > backoffMax {
			backoff = backoffMax
		}
	}
}

// consume opens one connection/channel session.  Returns when the channel
// closes or ctx is cancelled.
func (c *Consumer) consume(ctx context.Context) error {
	conn, err := amqp.Dial(c.url)
	if err != nil {
		return err
	}
	defer func() { _ = conn.Close() }()

	ch, err := conn.Channel()
	if err != nil {
		return err
	}
	defer func() { _ = ch.Close() }()

	if err := ch.Qos(prefetchCount, 0, false); err != nil {
		return err
	}

	// Declare DLX and dead-letter queue.
	if err := ch.ExchangeDeclare(dlxName, "fanout", true, false, false, false, nil); err != nil {
		return fmt.Errorf("consumer: declare DLX: %w", err)
	}
	if _, err := ch.QueueDeclare(dlqName, true, false, false, false, nil); err != nil {
		return fmt.Errorf("consumer: declare DLQ: %w", err)
	}
	if err := ch.QueueBind(dlqName, "", dlxName, false, nil); err != nil {
		return fmt.Errorf("consumer: bind DLQ: %w", err)
	}

	if _, err := ch.QueueDeclare(
		queueName,
		true,  // durable
		false, // autoDelete
		false, // exclusive
		false, // noWait
		amqp.Table{"x-dead-letter-exchange": dlxName},
	); err != nil {
		return err
	}

	// Declare the orchestrator exchange and bind with routing keys.
	if err := ch.ExchangeDeclare(exchangeName, "topic", true, false, false, false, nil); err != nil {
		return fmt.Errorf("consumer: declare exchange: %w", err)
	}
	for _, key := range []string{"execution.*", "node.*"} {
		if err := ch.QueueBind(queueName, key, exchangeName, false, nil); err != nil {
			return fmt.Errorf("consumer: bind key %s: %w", key, err)
		}
	}

	msgs, err := ch.Consume(
		queueName,
		"",    // consumer tag — broker assigns
		false, // autoAck
		false, // exclusive
		false, // noLocal
		false, // noWait
		nil,
	)
	if err != nil {
		return err
	}

	connClosed := conn.NotifyClose(make(chan *amqp.Error, 1))

	slog.Info("consumer: started", "queue", queueName)

	for {
		select {
		case <-ctx.Done():
			c.proc.Flush(context.Background())
			return nil

		case err := <-connClosed:
			c.proc.Flush(context.Background())
			if err != nil {
				return err
			}
			return nil

		case msg, ok := <-msgs:
			if !ok {
				c.proc.Flush(context.Background())
				return nil
			}

			if err := c.proc.Dispatch(ctx, msg.RoutingKey, msg.Body); err != nil {
				slog.Warn("consumer: failed to dispatch event, nacking",
					"routingKey", msg.RoutingKey, "err", err)
				c.eventsErrored.Add(1)
				if nackErr := msg.Nack(false, false); nackErr != nil {
					slog.Error("consumer: nack failed", "routingKey", msg.RoutingKey, "err", nackErr)
				}
				continue
			}

			c.eventsProcessed.Add(1)
			if ackErr := msg.Ack(false); ackErr != nil {
				c.eventsErrored.Add(1)
				slog.Error("consumer: ack failed", "routingKey", msg.RoutingKey, "err", ackErr)
			}
		}
	}
}
