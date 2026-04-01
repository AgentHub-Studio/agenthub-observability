package writer_test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/AgentHub-Studio/agenthub-observability/internal/consumer"
	"github.com/AgentHub-Studio/agenthub-observability/internal/writer"
)

func TestBulkInsertExecutions_EmptySlice(t *testing.T) {
	// When the events slice is empty, BulkInsertExecutions returns nil without
	// touching the ClickHouse connection (connection can safely be nil here).
	w := writer.NewWriter(nil)
	err := w.BulkInsertExecutions(context.Background(), []consumer.ExecutionEvent{})
	require.NoError(t, err)
}

func TestNewWriter_NotNil(t *testing.T) {
	w := writer.NewWriter(nil)
	assert.NotNil(t, w)
}
