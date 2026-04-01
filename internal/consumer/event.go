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
