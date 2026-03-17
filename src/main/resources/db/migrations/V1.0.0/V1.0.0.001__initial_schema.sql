-- AgentHub Observability — ClickHouse Initial Schema
-- All tables use tenant_id for isolation (no schema-per-tenant in ClickHouse).
-- Engine choices:
--   ReplacingMergeTree(updated_at) — for rows that get updated (traces)
--   MergeTree()                   — for append-only events (metric_events, tool_traces)

-- =============================================================================
-- EXECUTION TRACES
-- =============================================================================

CREATE TABLE IF NOT EXISTS execution_traces
(
    id                  UUID,
    tenant_id           UUID                NOT NULL,
    agent_id            UUID                NOT NULL,
    agent_version_id    UUID,
    user_id             UUID,
    execution_id        UUID                NOT NULL,
    status              LowCardinality(String) NOT NULL,
    started_at          DateTime64(6, 'UTC') NOT NULL,
    completed_at        DateTime64(6, 'UTC'),
    duration_ms         Int64,
    input_data          String              DEFAULT '{}',
    output_data         String              DEFAULT '{}',
    error_message       String,
    error_stack_trace   String,
    trigger_source      LowCardinality(String),
    trigger_metadata    String              DEFAULT '{}',
    created_at          DateTime64(6, 'UTC') NOT NULL DEFAULT now64(),
    updated_at          DateTime64(6, 'UTC') NOT NULL DEFAULT now64()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(started_at)
ORDER BY (tenant_id, started_at, id)
TTL toDateTime(started_at) + INTERVAL 90 DAY;

-- =============================================================================
-- NODE EXECUTION TRACES
-- =============================================================================

CREATE TABLE IF NOT EXISTS node_execution_traces
(
    id                  UUID,
    tenant_id           UUID                NOT NULL,
    execution_id        UUID,
    execution_trace_id  UUID                NOT NULL,
    node_id             String              NOT NULL,
    node_type           LowCardinality(String) NOT NULL,
    node_name           String,
    status              LowCardinality(String) NOT NULL,
    started_at          DateTime64(6, 'UTC') NOT NULL,
    completed_at        DateTime64(6, 'UTC'),
    duration_ms         Int64,
    input_data          String              DEFAULT '{}',
    output_data         String              DEFAULT '{}',
    error_message       String,
    context_before      String              DEFAULT '{}',
    context_after       String              DEFAULT '{}',
    attempt_number      Int32               DEFAULT 1,
    max_retries         Int32,
    created_at          DateTime64(6, 'UTC') NOT NULL DEFAULT now64(),
    updated_at          DateTime64(6, 'UTC') NOT NULL DEFAULT now64()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(started_at)
ORDER BY (tenant_id, execution_trace_id, started_at, id)
TTL toDateTime(started_at) + INTERVAL 90 DAY;

-- =============================================================================
-- TOOL EXECUTION TRACES
-- =============================================================================

CREATE TABLE IF NOT EXISTS tool_execution_traces
(
    id                      UUID,
    tenant_id               UUID                NOT NULL,
    node_execution_trace_id UUID,
    skill_id                UUID,
    skill_slug              String,
    tool_id                 UUID,
    tool_type               LowCardinality(String) NOT NULL,
    status                  LowCardinality(String) NOT NULL,
    started_at              DateTime64(6, 'UTC') NOT NULL,
    completed_at            DateTime64(6, 'UTC'),
    duration_ms             Int64,
    input_data              String              DEFAULT '{}',
    output_data             String              DEFAULT '{}',
    error_message           String,
    attempt_number          Int32               DEFAULT 1,
    max_retries             Int32,
    created_at              DateTime64(6, 'UTC') NOT NULL DEFAULT now64()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(started_at)
ORDER BY (tenant_id, started_at, id)
TTL toDateTime(started_at) + INTERVAL 90 DAY;

-- =============================================================================
-- METRIC EVENTS
-- =============================================================================

CREATE TABLE IF NOT EXISTS metric_events
(
    id           UUID,
    tenant_id    UUID                   NOT NULL,
    metric_name  String                 NOT NULL,
    metric_type  LowCardinality(String) NOT NULL,
    metric_value Float64                NOT NULL,
    metric_unit  LowCardinality(String),
    dimensions   String                 DEFAULT '{}',
    timestamp    DateTime64(6, 'UTC')   NOT NULL DEFAULT now64(),
    created_at   DateTime64(6, 'UTC')   NOT NULL DEFAULT now64()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tenant_id, metric_name, timestamp, id)
TTL toDateTime(timestamp) + INTERVAL 30 DAY;

-- =============================================================================
-- AGGREGATED METRICS
-- =============================================================================

CREATE TABLE IF NOT EXISTS aggregated_metrics
(
    id                  UUID,
    tenant_id           UUID                   NOT NULL,
    metric_name         String                 NOT NULL,
    aggregation_type    LowCardinality(String) NOT NULL,
    aggregation_period  LowCardinality(String) NOT NULL,
    period_start        DateTime64(6, 'UTC')   NOT NULL,
    period_end          DateTime64(6, 'UTC')   NOT NULL,
    dimensions          String                 DEFAULT '{}',
    value               Float64                NOT NULL,
    sample_count        Int64                  NOT NULL,
    created_at          DateTime64(6, 'UTC')   NOT NULL DEFAULT now64(),
    updated_at          DateTime64(6, 'UTC')   NOT NULL DEFAULT now64()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(period_start)
ORDER BY (tenant_id, metric_name, aggregation_type, aggregation_period, period_start, id)
TTL toDateTime(period_start) + INTERVAL 365 DAY;
