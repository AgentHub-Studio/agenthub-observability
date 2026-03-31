// Package writer provides ClickHouse persistence for execution events and
// aggregated agent metrics.
package writer

import (
	"context"
	"fmt"
	"time"

	"github.com/ClickHouse/clickhouse-go/v2"

	"github.com/AgentHub-Studio/agenthub-observability/internal/consumer"
)

const (
	createAgentExecutionsTable = `
CREATE TABLE IF NOT EXISTS agent_executions (
    execution_id   String,
    tenant_id      String,
    agent_id       String,
    status         String,
    started_at     DateTime64(3, 'UTC'),
    finished_at    Nullable(DateTime64(3, 'UTC')),
    duration_ms    Int64,
    node_count     Int32,
    error_msg      String,
    INDEX idx_tenant_id (tenant_id) TYPE bloom_filter GRANULARITY 1,
    INDEX idx_agent_id (agent_id) TYPE bloom_filter GRANULARITY 1
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(started_at)
ORDER BY (tenant_id, started_at)
TTL started_at + INTERVAL 30 DAY`

	createAgentMetricsTable = `
CREATE TABLE IF NOT EXISTS agent_metrics (
    tenant_id       String,
    agent_id        String,
    date            Date,
    total_runs      UInt64,
    success_runs    UInt64,
    failed_runs     UInt64,
    avg_duration_ms Float64,
    p95_duration_ms Float64,
    p99_duration_ms Float64
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (tenant_id, agent_id, date)`
)

// Writer persists execution events to ClickHouse.
type Writer struct {
	conn clickhouse.Conn
}

// NewWriter creates a Writer backed by conn.
func NewWriter(conn clickhouse.Conn) *Writer {
	return &Writer{conn: conn}
}

// CreateTables creates the agent_executions and agent_metrics tables if they
// do not already exist.
func (w *Writer) CreateTables(ctx context.Context) error {
	for _, ddl := range []string{createAgentExecutionsTable, createAgentMetricsTable} {
		if err := w.conn.Exec(ctx, ddl); err != nil {
			return fmt.Errorf("writer: create table: %w", err)
		}
	}
	return nil
}

// BulkInsertExecutions inserts a batch of ExecutionEvents into agent_executions.
func (w *Writer) BulkInsertExecutions(ctx context.Context, events []consumer.ExecutionEvent) error {
	if len(events) == 0 {
		return nil
	}

	batch, err := w.conn.PrepareBatch(ctx, "INSERT INTO agent_executions")
	if err != nil {
		return fmt.Errorf("writer: prepare batch: %w", err)
	}

	for _, e := range events {
		var finishedAt *time.Time
		if e.FinishedAt != nil {
			t := e.FinishedAt.UTC()
			finishedAt = &t
		}

		if err := batch.Append(
			e.ExecutionID,
			e.TenantID,
			e.AgentID,
			e.Status,
			e.StartedAt.UTC(),
			finishedAt,
			e.DurationMs,
			int32(e.NodeCount),
			e.ErrorMsg,
		); err != nil {
			return fmt.Errorf("writer: append row: %w", err)
		}
	}

	if err := batch.Send(); err != nil {
		return fmt.Errorf("writer: send batch: %w", err)
	}

	return nil
}
