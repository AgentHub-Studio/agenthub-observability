package consumer

import (
	"context"
	"log/slog"
	"sync"
	"time"
)

// FlushFunc is called when a batch is ready to be persisted.
type FlushFunc[T any] func(ctx context.Context, items []T) error

// Batcher accumulates items and flushes when the batch reaches maxSize
// or the flushInterval elapses, whichever comes first.
// Batcher is safe for concurrent use.
type Batcher[T any] struct {
	mu            sync.Mutex
	buf           []T
	maxSize       int
	flushInterval time.Duration
	flush         FlushFunc[T]
}

// NewBatcher creates a Batcher with the given configuration.
func NewBatcher[T any](maxSize int, flushInterval time.Duration, flush FlushFunc[T]) *Batcher[T] {
	return &Batcher[T]{
		buf:           make([]T, 0, maxSize),
		maxSize:       maxSize,
		flushInterval: flushInterval,
		flush:         flush,
	}
}

// Add appends an item to the batch. If the batch is full, it is flushed
// synchronously before adding the next item.
func (b *Batcher[T]) Add(ctx context.Context, item T) {
	b.mu.Lock()
	b.buf = append(b.buf, item)
	full := len(b.buf) >= b.maxSize
	b.mu.Unlock()

	if full {
		b.Flush(ctx)
	}
}

// Flush sends the current batch to the flush function and resets the buffer.
func (b *Batcher[T]) Flush(ctx context.Context) {
	b.mu.Lock()
	if len(b.buf) == 0 {
		b.mu.Unlock()
		return
	}
	batch := make([]T, len(b.buf))
	copy(batch, b.buf)
	b.buf = b.buf[:0]
	b.mu.Unlock()

	if err := b.flush(ctx, batch); err != nil {
		slog.Error("batcher: flush failed", "count", len(batch), "err", err)
	} else {
		slog.Info("batcher: flushed batch", "count", len(batch))
	}
}

// Run starts the periodic flush ticker. It blocks until ctx is cancelled,
// then flushes any remaining items.
func (b *Batcher[T]) Run(ctx context.Context) {
	ticker := time.NewTicker(b.flushInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			b.Flush(ctx)
		case <-ctx.Done():
			b.Flush(context.Background()) // flush remaining on shutdown
			return
		}
	}
}
