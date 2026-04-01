package consumer

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"strings"
)

// Processor routes incoming AMQP messages to the correct typed batcher
// based on the message routing key prefix.
type Processor struct {
	execBatcher *Batcher[ExecutionEvent]
	nodeBatcher *Batcher[NodeEvent]
}

// NewProcessor creates a Processor wiring execution and node event flush functions.
func NewProcessor(
	execFlush FlushFunc[ExecutionEvent],
	nodeFlush FlushFunc[NodeEvent],
) *Processor {
	return &Processor{
		execBatcher: NewBatcher[ExecutionEvent](batchSize, flushInterval, execFlush),
		nodeBatcher: NewBatcher[NodeEvent](batchSize, flushInterval, nodeFlush),
	}
}

// Run starts the periodic flush goroutines for all batchers.
// It blocks until ctx is cancelled.
func (p *Processor) Run(ctx context.Context) {
	execDone := make(chan struct{})
	nodeDone := make(chan struct{})
	go func() { p.execBatcher.Run(ctx); close(execDone) }()
	go func() { p.nodeBatcher.Run(ctx); close(nodeDone) }()
	<-execDone
	<-nodeDone
}

// Dispatch deserialises and routes a single AMQP message body to the
// appropriate batcher based on routingKey prefix.
//
//   - "execution.*" → ExecutionEvent → execBatcher
//   - "node.*"      → NodeEvent      → nodeBatcher
//
// Unknown routing keys are logged and discarded.
func (p *Processor) Dispatch(ctx context.Context, routingKey string, body []byte) error {
	switch {
	case strings.HasPrefix(routingKey, "execution."):
		var e ExecutionEvent
		if err := json.Unmarshal(body, &e); err != nil {
			return fmt.Errorf("processor: unmarshal execution event: %w", err)
		}
		p.execBatcher.Add(ctx, e)

	case strings.HasPrefix(routingKey, "node."):
		var e NodeEvent
		if err := json.Unmarshal(body, &e); err != nil {
			return fmt.Errorf("processor: unmarshal node event: %w", err)
		}
		p.nodeBatcher.Add(ctx, e)

	default:
		slog.Warn("processor: unknown routing key, discarding", "key", routingKey)
	}
	return nil
}

// Flush forces an immediate flush of all pending batches.
func (p *Processor) Flush(ctx context.Context) {
	p.execBatcher.Flush(ctx)
	p.nodeBatcher.Flush(ctx)
}
