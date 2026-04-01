package consumer

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestProcessor_Dispatch_ExecutionEvent(t *testing.T) {
	var capturedExec []ExecutionEvent
	var capturedNode []NodeEvent

	p := NewProcessor(
		func(_ context.Context, batch []ExecutionEvent) error { capturedExec = append(capturedExec, batch...); return nil },
		func(_ context.Context, batch []NodeEvent) error { capturedNode = append(capturedNode, batch...); return nil },
	)

	body, err := json.Marshal(ExecutionEvent{
		ExecutionID: "exec-1",
		TenantID:    "tenant-a",
		AgentID:     "agent-1",
		Status:      "SUCCESS",
		StartedAt:   time.Now(),
	})
	require.NoError(t, err)

	err = p.Dispatch(context.Background(), "execution.completed", body)
	require.NoError(t, err)

	p.Flush(context.Background())

	assert.Len(t, capturedExec, 1)
	assert.Equal(t, "exec-1", capturedExec[0].ExecutionID)
	assert.Empty(t, capturedNode)
}

func TestProcessor_Dispatch_NodeEvent(t *testing.T) {
	var capturedExec []ExecutionEvent
	var capturedNode []NodeEvent

	p := NewProcessor(
		func(_ context.Context, batch []ExecutionEvent) error { capturedExec = append(capturedExec, batch...); return nil },
		func(_ context.Context, batch []NodeEvent) error { capturedNode = append(capturedNode, batch...); return nil },
	)

	body, err := json.Marshal(NodeEvent{
		NodeExecutionID: "node-exec-1",
		ExecutionID:     "exec-1",
		TenantID:        "tenant-a",
		NodeID:          "node-1",
		NodeType:        "LLM",
		Status:          "SUCCESS",
		StartedAt:       time.Now(),
	})
	require.NoError(t, err)

	err = p.Dispatch(context.Background(), "node.completed", body)
	require.NoError(t, err)

	p.Flush(context.Background())

	assert.Empty(t, capturedExec)
	assert.Len(t, capturedNode, 1)
	assert.Equal(t, "node-exec-1", capturedNode[0].NodeExecutionID)
}

func TestProcessor_Dispatch_UnknownRoutingKey(t *testing.T) {
	p := NewProcessor(
		func(_ context.Context, _ []ExecutionEvent) error { return nil },
		func(_ context.Context, _ []NodeEvent) error { return nil },
	)

	// Unknown routing key should not return an error — just discard.
	err := p.Dispatch(context.Background(), "tool.completed", []byte(`{}`))
	assert.NoError(t, err)
}

func TestProcessor_Dispatch_InvalidJSON(t *testing.T) {
	p := NewProcessor(
		func(_ context.Context, _ []ExecutionEvent) error { return nil },
		func(_ context.Context, _ []NodeEvent) error { return nil },
	)

	err := p.Dispatch(context.Background(), "execution.created", []byte(`{invalid`))
	assert.Error(t, err)

	err = p.Dispatch(context.Background(), "node.created", []byte(`{invalid`))
	assert.Error(t, err)
}

func TestProcessor_Dispatch_MultipleEvents(t *testing.T) {
	var execCount, nodeCount int

	p := NewProcessor(
		func(_ context.Context, batch []ExecutionEvent) error { execCount += len(batch); return nil },
		func(_ context.Context, batch []NodeEvent) error { nodeCount += len(batch); return nil },
	)

	ctx := context.Background()

	for i := 0; i < 3; i++ {
		body, _ := json.Marshal(ExecutionEvent{ExecutionID: "e", TenantID: "t", AgentID: "a", Status: "SUCCESS", StartedAt: time.Now()})
		require.NoError(t, p.Dispatch(ctx, "execution.started", body))
	}
	for i := 0; i < 2; i++ {
		body, _ := json.Marshal(NodeEvent{NodeExecutionID: "n", ExecutionID: "e", TenantID: "t", NodeID: "nd", NodeType: "LLM", Status: "SUCCESS", StartedAt: time.Now()})
		require.NoError(t, p.Dispatch(ctx, "node.started", body))
	}

	p.Flush(ctx)

	assert.Equal(t, 3, execCount)
	assert.Equal(t, 2, nodeCount)
}
