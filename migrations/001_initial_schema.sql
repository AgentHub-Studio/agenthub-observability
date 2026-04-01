-- ClickHouse schema for agenthub-observability
-- Tables are created with TTL retention policies

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
TTL started_at + INTERVAL 30 DAY;

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
TTL started_at + INTERVAL 30 DAY;

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
TTL started_at + INTERVAL 30 DAY;

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
TTL occurred_at + INTERVAL 30 DAY;

CREATE TABLE IF NOT EXISTS aggregated_metrics (
    id             String,
    tenant_id      String,
    metric_name    String,
    aggregation_type   String,
    aggregation_period String,
    period_start   DateTime64(3, 'UTC'),
    period_end     DateTime64(3, 'UTC'),
    value          Float64,
    sample_count   Int64,
    created_at     DateTime64(3, 'UTC')
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(period_start)
ORDER BY (tenant_id, metric_name, aggregation_period, period_start)
TTL period_start + INTERVAL 365 DAY;

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
ORDER BY (tenant_id, agent_id, date);
