-- AgentHub Observability — Initial Schema
-- Tables live in the shared 'agenthub' schema and carry tenant_id for isolation.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================================
-- EXECUTION TRACES
-- =============================================================================

CREATE TABLE IF NOT EXISTS execution_traces (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID         NOT NULL,
    agent_id            UUID         NOT NULL,
    agent_version_id    UUID,
    user_id             UUID,
    execution_id        UUID         NOT NULL UNIQUE,
    status              VARCHAR(50)  NOT NULL,
    started_at          TIMESTAMPTZ  NOT NULL,
    completed_at        TIMESTAMPTZ,
    duration_ms         BIGINT,
    input_data          JSONB,
    output_data         JSONB,
    error_message       TEXT,
    error_stack_trace   TEXT,
    trigger_source      VARCHAR(100),
    trigger_metadata    JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_execution_traces_tenant       ON execution_traces (tenant_id);
CREATE INDEX IF NOT EXISTS idx_execution_traces_agent        ON execution_traces (agent_id);
CREATE INDEX IF NOT EXISTS idx_execution_traces_execution    ON execution_traces (execution_id);
CREATE INDEX IF NOT EXISTS idx_execution_traces_status       ON execution_traces (status);
CREATE INDEX IF NOT EXISTS idx_execution_traces_started_at   ON execution_traces (started_at);
CREATE INDEX IF NOT EXISTS idx_execution_traces_user         ON execution_traces (user_id);

-- =============================================================================
-- NODE EXECUTION TRACES
-- =============================================================================

CREATE TABLE IF NOT EXISTS node_execution_traces (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID         NOT NULL,
    execution_id            UUID,
    execution_trace_id      UUID         NOT NULL REFERENCES execution_traces (id) ON DELETE CASCADE,
    node_id                 VARCHAR(255) NOT NULL,
    node_type               VARCHAR(50)  NOT NULL,
    node_name               VARCHAR(255),
    status                  VARCHAR(50)  NOT NULL,
    started_at              TIMESTAMPTZ  NOT NULL,
    completed_at            TIMESTAMPTZ,
    duration_ms             BIGINT,
    input_data              JSONB,
    output_data             JSONB,
    error_message           TEXT,
    context_before          JSONB,
    context_after           JSONB,
    attempt_number          INTEGER      DEFAULT 1,
    max_retries             INTEGER,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_node_execution_traces_tenant      ON node_execution_traces (tenant_id);
CREATE INDEX IF NOT EXISTS idx_node_execution_traces_execution   ON node_execution_traces (execution_trace_id);
CREATE INDEX IF NOT EXISTS idx_node_execution_traces_node        ON node_execution_traces (node_id);
CREATE INDEX IF NOT EXISTS idx_node_execution_traces_status      ON node_execution_traces (status);
CREATE INDEX IF NOT EXISTS idx_node_execution_traces_started_at  ON node_execution_traces (started_at);

-- =============================================================================
-- TOOL EXECUTION TRACES
-- =============================================================================

CREATE TABLE IF NOT EXISTS tool_execution_traces (
    id                      UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID        NOT NULL,
    node_execution_trace_id UUID        REFERENCES node_execution_traces (id) ON DELETE CASCADE,
    skill_id                UUID,
    skill_slug              VARCHAR(255),
    tool_id                 UUID,
    tool_type               VARCHAR(50) NOT NULL,
    status                  VARCHAR(50) NOT NULL,
    started_at              TIMESTAMPTZ NOT NULL,
    completed_at            TIMESTAMPTZ,
    duration_ms             BIGINT,
    input_data              JSONB,
    output_data             JSONB,
    error_message           TEXT,
    attempt_number          INTEGER     DEFAULT 1,
    max_retries             INTEGER,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tool_execution_traces_tenant   ON tool_execution_traces (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tool_execution_traces_node     ON tool_execution_traces (node_execution_trace_id);
CREATE INDEX IF NOT EXISTS idx_tool_execution_traces_skill    ON tool_execution_traces (skill_slug);
CREATE INDEX IF NOT EXISTS idx_tool_execution_traces_type     ON tool_execution_traces (tool_type);
CREATE INDEX IF NOT EXISTS idx_tool_execution_traces_status   ON tool_execution_traces (status);
CREATE INDEX IF NOT EXISTS idx_tool_execution_traces_started  ON tool_execution_traces (started_at);

-- =============================================================================
-- METRICS
-- =============================================================================

CREATE TABLE IF NOT EXISTS metric_events (
    id           UUID             PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id    UUID             NOT NULL,
    metric_name  VARCHAR(255)     NOT NULL,
    metric_type  VARCHAR(50)      NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    metric_unit  VARCHAR(50),
    dimensions   JSONB,
    timestamp    TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_metric_events_tenant     ON metric_events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_metric_events_name       ON metric_events (metric_name);
CREATE INDEX IF NOT EXISTS idx_metric_events_timestamp  ON metric_events (timestamp);
CREATE INDEX IF NOT EXISTS idx_metric_events_dimensions ON metric_events USING GIN (dimensions);

CREATE TABLE IF NOT EXISTS aggregated_metrics (
    id                  UUID             PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID             NOT NULL,
    metric_name         VARCHAR(255)     NOT NULL,
    aggregation_type    VARCHAR(50)      NOT NULL,
    aggregation_period  VARCHAR(50)      NOT NULL,
    period_start        TIMESTAMPTZ      NOT NULL,
    period_end          TIMESTAMPTZ      NOT NULL,
    dimensions          JSONB,
    value               DOUBLE PRECISION NOT NULL,
    sample_count        BIGINT           NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, metric_name, aggregation_type, aggregation_period, period_start)
);

CREATE INDEX IF NOT EXISTS idx_aggregated_metrics_tenant      ON aggregated_metrics (tenant_id);
CREATE INDEX IF NOT EXISTS idx_aggregated_metrics_name        ON aggregated_metrics (metric_name);
CREATE INDEX IF NOT EXISTS idx_aggregated_metrics_period      ON aggregated_metrics (aggregation_period);
CREATE INDEX IF NOT EXISTS idx_aggregated_metrics_period_start ON aggregated_metrics (period_start);

-- =============================================================================
-- TRIGGERS — auto-update updated_at
-- =============================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_execution_traces_updated_at
    BEFORE UPDATE ON execution_traces
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_node_execution_traces_updated_at
    BEFORE UPDATE ON node_execution_traces
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_aggregated_metrics_updated_at
    BEFORE UPDATE ON aggregated_metrics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
