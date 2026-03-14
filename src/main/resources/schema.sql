-- AgentHub Observability Schema
-- Traces and Metrics for Agent Executions

-- Extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================================
-- EXECUTION TRACES
-- =============================================================================

-- Agent execution traces (orchestrator level)
CREATE TABLE IF NOT EXISTS execution_traces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    agent_version_id UUID,
    user_id UUID,
    execution_id UUID NOT NULL UNIQUE,
    
    -- Execution metadata
    status VARCHAR(50) NOT NULL, -- RUNNING, COMPLETED, FAILED, CANCELLED
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    
    -- Input/Output
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    error_stack_trace TEXT,
    
    -- Context
    trigger_source VARCHAR(100), -- API, WEBHOOK, SCHEDULE, MANUAL
    trigger_metadata JSONB,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    INDEX idx_execution_traces_tenant (tenant_id),
    INDEX idx_execution_traces_agent (agent_id),
    INDEX idx_execution_traces_execution (execution_id),
    INDEX idx_execution_traces_status (status),
    INDEX idx_execution_traces_started_at (started_at),
    INDEX idx_execution_traces_user (user_id)
);

-- Pipeline node execution traces (pipeline level)
CREATE TABLE IF NOT EXISTS node_execution_traces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    execution_trace_id UUID NOT NULL REFERENCES execution_traces(id) ON DELETE CASCADE,
    
    -- Node metadata
    node_id VARCHAR(255) NOT NULL,
    node_type VARCHAR(50) NOT NULL, -- TOOL, LLM, DECISION, LOOP, PARALLEL
    node_name VARCHAR(255),
    
    -- Execution metadata
    status VARCHAR(50) NOT NULL, -- RUNNING, COMPLETED, FAILED, SKIPPED
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    
    -- Input/Output
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    
    -- Context mutation
    context_before JSONB,
    context_after JSONB,
    
    -- Retry metadata
    attempt_number INTEGER DEFAULT 1,
    max_retries INTEGER,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    INDEX idx_node_execution_traces_tenant (tenant_id),
    INDEX idx_node_execution_traces_execution (execution_trace_id),
    INDEX idx_node_execution_traces_node (node_id),
    INDEX idx_node_execution_traces_type (node_type),
    INDEX idx_node_execution_traces_status (status),
    INDEX idx_node_execution_traces_started_at (started_at)
);

-- Tool execution traces (skill runtime level)
CREATE TABLE IF NOT EXISTS tool_execution_traces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    node_execution_trace_id UUID REFERENCES node_execution_traces(id) ON DELETE CASCADE,
    
    -- Tool metadata
    skill_id UUID,
    skill_slug VARCHAR(255),
    tool_id UUID,
    tool_type VARCHAR(50) NOT NULL, -- HTTP, SQL, DOCUMENT_SEARCH, SCRIPT, MCP
    
    -- Execution metadata
    status VARCHAR(50) NOT NULL, -- SUCCESS, FAILURE
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    
    -- Input/Output
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    
    -- Retry metadata
    attempt_number INTEGER DEFAULT 1,
    max_retries INTEGER,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    INDEX idx_tool_execution_traces_tenant (tenant_id),
    INDEX idx_tool_execution_traces_node (node_execution_trace_id),
    INDEX idx_tool_execution_traces_skill (skill_slug),
    INDEX idx_tool_execution_traces_type (tool_type),
    INDEX idx_tool_execution_traces_status (status),
    INDEX idx_tool_execution_traces_started_at (started_at)
);

-- =============================================================================
-- METRICS
-- =============================================================================

-- Metric events (raw time-series data)
CREATE TABLE IF NOT EXISTS metric_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    
    -- Metric metadata
    metric_name VARCHAR(255) NOT NULL, -- execution.count, execution.duration, tool.invocation, etc.
    metric_type VARCHAR(50) NOT NULL, -- COUNTER, GAUGE, HISTOGRAM, TIMER
    metric_value DOUBLE PRECISION NOT NULL,
    metric_unit VARCHAR(50), -- ms, count, bytes, etc.
    
    -- Dimensions (for grouping/filtering)
    dimensions JSONB, -- {agentId, skillSlug, toolType, status, etc.}
    
    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    INDEX idx_metric_events_tenant (tenant_id),
    INDEX idx_metric_events_name (metric_name),
    INDEX idx_metric_events_timestamp (timestamp),
    INDEX idx_metric_events_dimensions USING GIN (dimensions)
);

-- Aggregated metrics (pre-computed for performance)
CREATE TABLE IF NOT EXISTS aggregated_metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    
    -- Metric metadata
    metric_name VARCHAR(255) NOT NULL,
    aggregation_type VARCHAR(50) NOT NULL, -- SUM, AVG, MIN, MAX, COUNT, P50, P95, P99
    aggregation_period VARCHAR(50) NOT NULL, -- MINUTE, HOUR, DAY, WEEK, MONTH
    
    -- Time window
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    
    -- Dimensions (grouping keys)
    dimensions JSONB,
    
    -- Aggregated value
    value DOUBLE PRECISION NOT NULL,
    sample_count BIGINT NOT NULL,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    UNIQUE (tenant_id, metric_name, aggregation_type, aggregation_period, period_start, dimensions),
    INDEX idx_aggregated_metrics_tenant (tenant_id),
    INDEX idx_aggregated_metrics_name (metric_name),
    INDEX idx_aggregated_metrics_period (aggregation_period),
    INDEX idx_aggregated_metrics_period_start (period_start),
    INDEX idx_aggregated_metrics_dimensions USING GIN (dimensions)
);

-- =============================================================================
-- VIEWS (for common queries)
-- =============================================================================

-- Recent executions with summary
CREATE OR REPLACE VIEW v_recent_executions AS
SELECT 
    et.id,
    et.tenant_id,
    et.agent_id,
    et.user_id,
    et.execution_id,
    et.status,
    et.started_at,
    et.completed_at,
    et.duration_ms,
    et.trigger_source,
    COUNT(net.id) AS total_nodes,
    COUNT(CASE WHEN net.status = 'COMPLETED' THEN 1 END) AS completed_nodes,
    COUNT(CASE WHEN net.status = 'FAILED' THEN 1 END) AS failed_nodes,
    COUNT(tet.id) AS total_tool_calls
FROM execution_traces et
LEFT JOIN node_execution_traces net ON net.execution_trace_id = et.id
LEFT JOIN tool_execution_traces tet ON tet.node_execution_trace_id = net.id
GROUP BY et.id
ORDER BY et.started_at DESC;

-- Tool performance summary
CREATE OR REPLACE VIEW v_tool_performance AS
SELECT 
    tenant_id,
    skill_slug,
    tool_type,
    status,
    COUNT(*) AS invocation_count,
    AVG(duration_ms) AS avg_duration_ms,
    MIN(duration_ms) AS min_duration_ms,
    MAX(duration_ms) AS max_duration_ms,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50_duration_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_duration_ms,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms) AS p99_duration_ms
FROM tool_execution_traces
WHERE started_at >= NOW() - INTERVAL '24 hours'
GROUP BY tenant_id, skill_slug, tool_type, status;

-- =============================================================================
-- FUNCTIONS
-- =============================================================================

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for auto-updating updated_at
CREATE TRIGGER update_execution_traces_updated_at
    BEFORE UPDATE ON execution_traces
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_node_execution_traces_updated_at
    BEFORE UPDATE ON node_execution_traces
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_aggregated_metrics_updated_at
    BEFORE UPDATE ON aggregated_metrics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- CLEANUP (retention policies)
-- =============================================================================

-- Function to cleanup old traces (older than retention_days)
CREATE OR REPLACE FUNCTION cleanup_old_traces(retention_days INTEGER DEFAULT 30)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    WITH deleted AS (
        DELETE FROM execution_traces
        WHERE started_at < NOW() - (retention_days || ' days')::INTERVAL
        RETURNING id
    )
    SELECT COUNT(*) INTO deleted_count FROM deleted;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Function to cleanup old metric events (older than retention_days)
CREATE OR REPLACE FUNCTION cleanup_old_metric_events(retention_days INTEGER DEFAULT 7)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    WITH deleted AS (
        DELETE FROM metric_events
        WHERE timestamp < NOW() - (retention_days || ' days')::INTERVAL
        RETURNING id
    )
    SELECT COUNT(*) INTO deleted_count FROM deleted;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;
