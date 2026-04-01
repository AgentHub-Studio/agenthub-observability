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
TTL toDateTime(started_at) + INTERVAL 30 DAY`

	createNodeExecutionsTable = `
CREATE TABLE IF NOT EXISTS node_executions (
    node_execution_id  String,
    execution_id       String,
    tenant_id          String,
    node_id            String,
    node_type          String,
    status             String,
    started_at         DateTime64(3, 'UTC'),
    finished_at        Nullable(DateTime64(3, 'UTC')),
    duration_ms        Int64,
    input_tokens       Int32,
    output_tokens      Int32,
    error_msg          String,
    INDEX idx_execution_id (execution_id) TYPE bloom_filter GRANULARITY 1
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(started_at)
ORDER BY (tenant_id, execution_id, started_at)
TTL toDateTime(started_at) + INTERVAL 30 DAY`

	createToolExecutionsTable = `
CREATE TABLE IF NOT EXISTS tool_executions (
    tool_execution_id  String,
    execution_id       String,
    tenant_id          String,
    skill_slug         String,
    tool_type          String,
    status             String,
    started_at         DateTime64(3, 'UTC'),
    finished_at        Nullable(DateTime64(3, 'UTC')),
    duration_ms        Int64,
    error_msg          String,
    INDEX idx_skill_slug (skill_slug) TYPE bloom_filter GRANULARITY 1,
    INDEX idx_tool_type (tool_type) TYPE bloom_filter GRANULARITY 1
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(started_at)
ORDER BY (tenant_id, started_at)
TTL toDateTime(started_at) + INTERVAL 30 DAY`

	createMetricEventsTable = `
CREATE TABLE IF NOT EXISTS metric_events (
    event_id       String,
    tenant_id      String,
    metric_name    String,
    metric_type    String,
    value          Float64,
    labels         String,
    occurred_at    DateTime64(3, 'UTC'),
    INDEX idx_metric_name (metric_name) TYPE bloom_filter GRANULARITY 1
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (tenant_id, metric_name, occurred_at)
TTL toDateTime(occurred_at) + INTERVAL 90 DAY`

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

// Conn returns the underlying ClickHouse connection.
// Intended for use in integration tests that need to query tables directly.
func (w *Writer) Conn() clickhouse.Conn {
	return w.conn
}

// CreateTables creates all required ClickHouse tables if they do not already exist.
func (w *Writer) CreateTables(ctx context.Context) error {
	for _, ddl := range []string{
		createAgentExecutionsTable,
		createNodeExecutionsTable,
		createToolExecutionsTable,
		createMetricEventsTable,
		createAgentMetricsTable,
	} {
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

// BulkInsertNodeExecutions inserts a batch of NodeEvents into node_executions.
func (w *Writer) BulkInsertNodeExecutions(ctx context.Context, events []consumer.NodeEvent) error {
	if len(events) == 0 {
		return nil
	}

	batch, err := w.conn.PrepareBatch(ctx, "INSERT INTO node_executions")
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
			e.NodeExecutionID,
			e.ExecutionID,
			e.TenantID,
			e.NodeID,
			e.NodeType,
			e.Status,
			e.StartedAt.UTC(),
			finishedAt,
			e.DurationMs,
			e.InputTokens,
			e.OutputTokens,
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

// BulkInsertToolExecutions inserts a batch of ToolEvents into tool_executions.
func (w *Writer) BulkInsertToolExecutions(ctx context.Context, events []consumer.ToolEvent) error {
	if len(events) == 0 {
		return nil
	}

	batch, err := w.conn.PrepareBatch(ctx, "INSERT INTO tool_executions")
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
			e.ToolExecutionID,
			e.ExecutionID,
			e.TenantID,
			e.SkillSlug,
			e.ToolType,
			e.Status,
			e.StartedAt.UTC(),
			finishedAt,
			e.DurationMs,
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

// BulkInsertMetricEvents inserts a batch of MetricEvents into metric_events.
func (w *Writer) BulkInsertMetricEvents(ctx context.Context, events []consumer.MetricEvent) error {
	if len(events) == 0 {
		return nil
	}

	batch, err := w.conn.PrepareBatch(ctx, "INSERT INTO metric_events")
	if err != nil {
		return fmt.Errorf("writer: prepare batch: %w", err)
	}

	for _, e := range events {
		if err := batch.Append(
			e.EventID,
			e.TenantID,
			e.MetricName,
			e.MetricType,
			e.Value,
			e.Labels,
			e.OccurredAt.UTC(),
		); err != nil {
			return fmt.Errorf("writer: append row: %w", err)
		}
	}

	if err := batch.Send(); err != nil {
		return fmt.Errorf("writer: send batch: %w", err)
	}

	return nil
}

// BulkInsertAgentMetrics inserts pre-aggregated daily agent metrics into agent_metrics.
func (w *Writer) BulkInsertAgentMetrics(ctx context.Context, metrics []consumer.AgentMetric) error {
	if len(metrics) == 0 {
		return nil
	}

	batch, err := w.conn.PrepareBatch(ctx, "INSERT INTO agent_metrics")
	if err != nil {
		return fmt.Errorf("writer: prepare batch: %w", err)
	}

	for _, m := range metrics {
		if err := batch.Append(
			m.TenantID,
			m.AgentID,
			m.Date,
			m.TotalRuns,
			m.SuccessRuns,
			m.FailedRuns,
			m.AvgDurationMs,
			m.P95DurationMs,
			m.P99DurationMs,
		); err != nil {
			return fmt.Errorf("writer: append row: %w", err)
		}
	}

	if err := batch.Send(); err != nil {
		return fmt.Errorf("writer: send batch: %w", err)
	}

	return nil
}
