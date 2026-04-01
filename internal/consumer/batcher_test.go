package consumer

import (
	"context"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestBatcher_FlushBySize(t *testing.T) {
	var flushed [][]int
	b := NewBatcher[int](3, 10*time.Second, func(_ context.Context, items []int) error {
		flushed = append(flushed, items)
		return nil
	})

	ctx := context.Background()
	b.Add(ctx, 1)
	b.Add(ctx, 2)
	b.Add(ctx, 3) // triggers flush

	assert.Len(t, flushed, 1)
	assert.Equal(t, []int{1, 2, 3}, flushed[0])
}

func TestBatcher_FlushByTime(t *testing.T) {
	var count atomic.Int32
	b := NewBatcher[string](100, 50*time.Millisecond, func(_ context.Context, items []string) error {
		count.Add(int32(len(items)))
		return nil
	})

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go b.Run(ctx)

	b.Add(ctx, "a")
	b.Add(ctx, "b")

	time.Sleep(120 * time.Millisecond) // wait for timer flush
	assert.GreaterOrEqual(t, count.Load(), int32(2))
}

func TestBatcher_FlushOnShutdown(t *testing.T) {
	var flushed []string
	b := NewBatcher[string](100, 10*time.Second, func(_ context.Context, items []string) error {
		flushed = append(flushed, items...)
		return nil
	})

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() {
		b.Run(ctx)
		close(done)
	}()

	b.Add(ctx, "x")
	b.Add(ctx, "y")

	cancel()
	<-done

	assert.ElementsMatch(t, []string{"x", "y"}, flushed)
}

func TestBatcher_ExplicitFlush(t *testing.T) {
	var flushed []int
	b := NewBatcher[int](10, 10*time.Second, func(_ context.Context, items []int) error {
		flushed = append(flushed, items...)
		return nil
	})

	ctx := context.Background()
	b.Add(ctx, 1)
	b.Flush(ctx)

	assert.Equal(t, []int{1}, flushed)
}

func TestBatcher_EmptyFlushNoOp(t *testing.T) {
	calls := 0
	b := NewBatcher[int](10, 10*time.Second, func(_ context.Context, items []int) error {
		calls++
		return nil
	})

	b.Flush(context.Background())
	assert.Equal(t, 0, calls)
}

func TestBatcher_MultipleBatches(t *testing.T) {
	var total int
	b := NewBatcher[int](2, 10*time.Second, func(_ context.Context, items []int) error {
		total += len(items)
		return nil
	})

	ctx := context.Background()
	for i := 0; i < 6; i++ {
		b.Add(ctx, i)
	}

	assert.Equal(t, 6, total)
}
