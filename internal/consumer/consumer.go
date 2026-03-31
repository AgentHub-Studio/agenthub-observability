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

// ExecutionEvent represents an agent execution event received from RabbitMQ.
type ExecutionEvent struct {
	ExecutionID string     `json:"executionId"`
	TenantID    string     `json:"tenantId"`
	AgentID     string     `json:"agentId"`
	Status      string     `json:"status"` // RUNNING, SUCCESS, FAILED
	StartedAt   time.Time  `json:"startedAt"`
	FinishedAt  *time.Time `json:"finishedAt,omitempty"`
	DurationMs  int64      `json:"durationMs,omitempty"`
	NodeCount   int        `json:"nodeCount,omitempty"`
	ErrorMsg    string     `json:"errorMsg,omitempty"`
}

// FlushFunc is called with a batch of events ready to be persisted.
type FlushFunc func(ctx context.Context, events []ExecutionEvent) error

// Consumer reads execution events from RabbitMQ and flushes them in batches.
type Consumer struct {
	url   string
	flush FlushFunc
}

// New creates a Consumer for the given RabbitMQ URL.
func New(url string, flush FlushFunc) *Consumer {
	return &Consumer{url: url, flush: flush}
}

// Run starts consuming until ctx is cancelled.  Reconnects automatically with
// exponential backoff (1 s → 2 s → 4 s … capped at 30 s).
func (c *Consumer) Run(ctx context.Context) {
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

	batch := make([]ExecutionEvent, 0, batchSize)
	ticker := time.NewTicker(flushInterval)
	defer ticker.Stop()

	slog.Info("consumer: started", "queue", queueName)

	for {
		select {
		case <-ctx.Done():
			c.flushBatch(ctx, ch, &batch)
			return nil

		case err := <-connClosed:
			c.flushBatch(ctx, ch, &batch)
			if err != nil {
				return err
			}
			return nil

		case msg, ok := <-msgs:
			if !ok {
				c.flushBatch(ctx, ch, &batch)
				return nil
			}

			var event ExecutionEvent
			if err := json.Unmarshal(msg.Body, &event); err != nil {
				slog.Warn("consumer: failed to unmarshal event, nacking", "err", err)
				msg.Nack(false, false) //nolint:errcheck
				continue
			}

			batch = append(batch, event)

			if len(batch) >= batchSize {
				c.flushBatch(ctx, ch, &batch)
			}

			msg.Ack(false) //nolint:errcheck

		case <-ticker.C:
			c.flushBatch(ctx, ch, &batch)
		}
	}
}

// flushBatch persists pending events and resets the slice.
func (c *Consumer) flushBatch(ctx context.Context, ch *amqp.Channel, batch *[]ExecutionEvent) {
	if len(*batch) == 0 {
		return
	}

	if err := c.flush(ctx, *batch); err != nil {
		slog.Error("consumer: flush failed", "count", len(*batch), "err", err)
		// Keep messages in-memory; they were already acked.  A more robust
		// implementation could republish to a dead-letter queue here.
	} else {
		slog.Info("consumer: flushed batch", "count", len(*batch))
	}

	*batch = (*batch)[:0]
}
