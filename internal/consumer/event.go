// Package consumer provides a RabbitMQ consumer for observability events.
package consumer

import "time"

// ExecutionEvent represents an agent execution lifecycle event.
type ExecutionEvent struct {
	ExecutionID string     `json:"executionId"`
	TenantID    string     `json:"tenantId"`
	AgentID     string     `json:"agentId"`
	Status      string     `json:"status"` // RUNNING, SUCCESS, FAILED, CANCELLED
	StartedAt   time.Time  `json:"startedAt"`
	FinishedAt  *time.Time `json:"finishedAt,omitempty"`
	DurationMs  int64      `json:"durationMs,omitempty"`
	NodeCount   int        `json:"nodeCount,omitempty"`
	ErrorMsg    string     `json:"errorMsg,omitempty"`
}

// NodeEvent represents a pipeline node execution event.
type NodeEvent struct {
	NodeExecutionID string     `json:"nodeExecutionId"`
	ExecutionID     string     `json:"executionId"`
	TenantID        string     `json:"tenantId"`
	NodeID          string     `json:"nodeId"`
	NodeType        string     `json:"nodeType"`
	Status          string     `json:"status"`
	StartedAt       time.Time  `json:"startedAt"`
	FinishedAt      *time.Time `json:"finishedAt,omitempty"`
	DurationMs      int64      `json:"durationMs,omitempty"`
	InputTokens     int32      `json:"inputTokens,omitempty"`
	OutputTokens    int32      `json:"outputTokens,omitempty"`
	ErrorMsg        string     `json:"errorMsg,omitempty"`
}

// ToolEvent represents a tool execution event within a pipeline node.
type ToolEvent struct {
	ToolExecutionID string     `json:"toolExecutionId"`
	ExecutionID     string     `json:"executionId"`
	TenantID        string     `json:"tenantId"`
	SkillSlug       string     `json:"skillSlug"`
	ToolType        string     `json:"toolType"`
	Status          string     `json:"status"`
	StartedAt       time.Time  `json:"startedAt"`
	FinishedAt      *time.Time `json:"finishedAt,omitempty"`
	DurationMs      int64      `json:"durationMs,omitempty"`
	ErrorMsg        string     `json:"errorMsg,omitempty"`
}

// MetricEvent represents a generic metric data point.
type MetricEvent struct {
	EventID    string    `json:"eventId"`
	TenantID   string    `json:"tenantId"`
	MetricName string    `json:"metricName"`
	MetricType string    `json:"metricType"` // counter, gauge, histogram
	Value      float64   `json:"value"`
	Labels     string    `json:"labels"` // JSON-encoded key-value pairs
	OccurredAt time.Time `json:"occurredAt"`
}

// AgentMetric holds pre-aggregated daily statistics for an agent.
type AgentMetric struct {
	TenantID      string  `json:"tenantId"`
	AgentID       string  `json:"agentId"`
	Date          string  `json:"date"` // YYYY-MM-DD
	TotalRuns     uint64  `json:"totalRuns"`
	SuccessRuns   uint64  `json:"successRuns"`
	FailedRuns    uint64  `json:"failedRuns"`
	AvgDurationMs float64 `json:"avgDurationMs"`
	P95DurationMs float64 `json:"p95DurationMs"`
	P99DurationMs float64 `json:"p99DurationMs"`
}
