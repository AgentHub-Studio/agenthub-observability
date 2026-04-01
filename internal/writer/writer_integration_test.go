package writer_test

import (
	"context"
	"fmt"
	"os"
	"testing"
	"time"

	clickhousedriver "github.com/ClickHouse/clickhouse-go/v2"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	tcclickhouse "github.com/testcontainers/testcontainers-go/modules/clickhouse"

	"github.com/AgentHub-Studio/agenthub-observability/internal/consumer"
	"github.com/AgentHub-Studio/agenthub-observability/internal/writer"
)

// newTestClickHouse spins up a real ClickHouse container using testcontainers and
// returns a Writer connected to it plus a cleanup function.
// Requires INTEGRATION_TESTS=1 and a Docker daemon to be accessible.
func newTestClickHouse(t *testing.T) (*writer.Writer, func()) {
	t.Helper()
	if os.Getenv("INTEGRATION_TESTS") == "" {
		t.Skip("skipping: set INTEGRATION_TESTS=1 to run ClickHouse integration tests")
	}
	ctx := context.Background()

	container, err := tcclickhouse.Run(ctx, "clickhouse/clickhouse-server:24.3-alpine")
	require.NoError(t, err, "start ClickHouse container")

	host, err := container.Host(ctx)
	require.NoError(t, err)
	port, err := container.MappedPort(ctx, "9000/tcp")
	require.NoError(t, err)

	conn, err := clickhousedriver.Open(&clickhousedriver.Options{
		Addr: []string{fmt.Sprintf("%s:%s", host, port.Port())},
		Auth: clickhousedriver.Auth{
			Database: "clickhouse",
			Username: "default",
			Password: "default",
		},
		DialTimeout:  10 * time.Second,
		MaxOpenConns: 2,
	})
	require.NoError(t, err, "open ClickHouse connection")

	w := writer.NewWriter(conn)
	require.NoError(t, w.CreateTables(ctx), "create tables")

	cleanup := func() {
		conn.Close()
		_ = container.Terminate(ctx)
	}
	return w, cleanup
}

// TestBulkInsertExecutions_Integration verifies that a batch of ExecutionEvents is
// persisted to the agent_executions table and can be queried back.
func TestBulkInsertExecutions_Integration(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	w, cleanup := newTestClickHouse(t)
	defer cleanup()

	ctx := context.Background()
	now := time.Now().UTC().Truncate(time.Millisecond)
	finished := now.Add(2 * time.Second)

	events := []consumer.ExecutionEvent{
		{
			ExecutionID: uuid.New().String(),
			TenantID:    "tenant-a",
			AgentID:     uuid.New().String(),
			Status:      "SUCCESS",
			StartedAt:   now,
			FinishedAt:  &finished,
			DurationMs:  2000,
			NodeCount:   3,
		},
		{
			ExecutionID: uuid.New().String(),
			TenantID:    "tenant-a",
			AgentID:     uuid.New().String(),
			Status:      "FAILED",
			StartedAt:   now,
			DurationMs:  500,
			ErrorMsg:    "something went wrong",
		},
	}

	err := w.BulkInsertExecutions(ctx, events)
	require.NoError(t, err)

	// Allow MergeTree to settle.
	time.Sleep(200 * time.Millisecond)

	conn := w.Conn()
	var count uint64
	err = conn.QueryRow(ctx, "SELECT count() FROM agent_executions WHERE tenant_id = 'tenant-a'").Scan(&count)
	require.NoError(t, err)
	assert.Equal(t, uint64(2), count, "expected 2 rows in agent_executions")
}

// TestBulkInsertNodeExecutions_Integration verifies node execution events are stored.
func TestBulkInsertNodeExecutions_Integration(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	w, cleanup := newTestClickHouse(t)
	defer cleanup()

	ctx := context.Background()
	now := time.Now().UTC().Truncate(time.Millisecond)
	execID := uuid.New().String()

	events := []consumer.NodeEvent{
		{
			NodeExecutionID: uuid.New().String(),
			ExecutionID:     execID,
			TenantID:        "tenant-b",
			NodeID:          uuid.New().String(),
			NodeType:        "LLM",
			Status:          "SUCCESS",
			StartedAt:       now,
			DurationMs:      1200,
			InputTokens:     150,
			OutputTokens:    300,
		},
	}

	err := w.BulkInsertNodeExecutions(ctx, events)
	require.NoError(t, err)

	time.Sleep(200 * time.Millisecond)

	conn := w.Conn()
	var count uint64
	err = conn.QueryRow(ctx, "SELECT count() FROM node_executions WHERE execution_id = ?", execID).Scan(&count)
	require.NoError(t, err)
	assert.Equal(t, uint64(1), count)
}

// TestBulkInsertToolExecutions_Integration verifies tool execution events are stored.
func TestBulkInsertToolExecutions_Integration(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	w, cleanup := newTestClickHouse(t)
	defer cleanup()

	ctx := context.Background()
	now := time.Now().UTC().Truncate(time.Millisecond)

	events := []consumer.ToolEvent{
		{
			ToolExecutionID: uuid.New().String(),
			ExecutionID:     uuid.New().String(),
			TenantID:        "tenant-c",
			SkillSlug:       "document-search",
			ToolType:        "DOCUMENT_SEARCH",
			Status:          "SUCCESS",
			StartedAt:       now,
			DurationMs:      80,
		},
	}

	err := w.BulkInsertToolExecutions(ctx, events)
	require.NoError(t, err)

	time.Sleep(200 * time.Millisecond)

	conn := w.Conn()
	var count uint64
	err = conn.QueryRow(ctx, "SELECT count() FROM tool_executions WHERE skill_slug = 'document-search'").Scan(&count)
	require.NoError(t, err)
	assert.Equal(t, uint64(1), count)
}

// TestBulkInsertMetricEvents_Integration verifies metric events are persisted.
func TestBulkInsertMetricEvents_Integration(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	w, cleanup := newTestClickHouse(t)
	defer cleanup()

	ctx := context.Background()

	events := []consumer.MetricEvent{
		{
			EventID:    uuid.New().String(),
			TenantID:   "tenant-d",
			MetricName: "api.request.count",
			MetricType: "counter",
			Value:      42.0,
			Labels:     `{"endpoint":"/api/agents"}`,
			OccurredAt: time.Now().UTC(),
		},
	}

	err := w.BulkInsertMetricEvents(ctx, events)
	require.NoError(t, err)

	time.Sleep(200 * time.Millisecond)

	conn := w.Conn()
	var val float64
	err = conn.QueryRow(ctx,
		"SELECT value FROM metric_events WHERE metric_name = 'api.request.count' LIMIT 1",
	).Scan(&val)
	require.NoError(t, err)
	assert.Equal(t, 42.0, val)
}

// TestCreateTables_Idempotent verifies that calling CreateTables twice is safe.
func TestCreateTables_Idempotent(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	w, cleanup := newTestClickHouse(t)
	defer cleanup()

	// Second call should not fail (all tables use CREATE TABLE IF NOT EXISTS).
	require.NoError(t, w.CreateTables(context.Background()))
}
