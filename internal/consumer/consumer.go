// Package consumer provides a RabbitMQ consumer that ingests execution events
// and forwards them in batches to a configurable flush handler.
package consumer

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

const (
	exchangeName   = "executions"
	queueName      = "observability.executions"
	prefetchCount  = 100
	batchSize      = 100
	flushInterval  = 5 * time.Second
	backoffInitial = 1 * time.Second
	backoffMax     = 30 * time.Second
)

// Consumer reads execution events from RabbitMQ and flushes them in batches.
type Consumer struct {
	url     string
	batcher *Batcher[ExecutionEvent]
}

// New creates a Consumer for the given RabbitMQ URL.
func New(url string, flush FlushFunc[ExecutionEvent]) *Consumer {
	return &Consumer{
		url:     url,
		batcher: NewBatcher[ExecutionEvent](batchSize, flushInterval, flush),
	}
}

// Run starts consuming until ctx is cancelled.  Reconnects automatically with
// exponential backoff (1 s → 2 s → 4 s … capped at 30 s).
func (c *Consumer) Run(ctx context.Context) {
	// Start periodic flush in background.
	go c.batcher.Run(ctx)

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
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		return err
	}
	defer ch.Close()

	if err := ch.Qos(prefetchCount, 0, false); err != nil {
		return err
	}

	if _, err := ch.QueueDeclare(
		queueName,
		true,  // durable
		false, // autoDelete
		false, // exclusive
		false, // noWait
		nil,
	); err != nil {
		return err
	}

	// Bind to the executions exchange so messages published there are routed
	// to our queue.  If the exchange does not exist yet we skip the bind and
	// rely on direct queue publishing.
	if err := ch.ExchangeDeclarePassive(exchangeName, "topic", true, false, false, false, nil); err == nil {
		// Re-open channel after passive declare (it may have been closed on error)
		ch.Close() //nolint:errcheck
		ch, err = conn.Channel()
		if err != nil {
			return err
		}
		if bindErr := ch.QueueBind(queueName, "#", exchangeName, false, nil); bindErr != nil {
			slog.Warn("consumer: queue bind failed, consuming directly from queue", "err", bindErr)
		}
	} else {
		slog.Warn("consumer: exchange not found, consuming directly from queue", "exchange", exchangeName)
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
			c.batcher.Flush(context.Background())
			return nil

		case err := <-connClosed:
			c.batcher.Flush(context.Background())
			if err != nil {
				return err
			}
			return nil

		case msg, ok := <-msgs:
			if !ok {
				c.batcher.Flush(context.Background())
				return nil
			}

			var event ExecutionEvent
			if err := json.Unmarshal(msg.Body, &event); err != nil {
				slog.Warn("consumer: failed to unmarshal event, nacking", "err", err)
				msg.Nack(false, false) //nolint:errcheck
				continue
			}

			c.batcher.Add(ctx, event)
			msg.Ack(false) //nolint:errcheck
		}
	}
}
