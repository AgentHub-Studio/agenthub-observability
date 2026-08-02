// Package handler provides HTTP handlers for the observability REST API.
package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/AgentHub-Studio/agenthub-go-commons/tenant"
	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/ClickHouse/clickhouse-go/v2/lib/driver"
	"github.com/go-chi/chi/v5"
)

// TraceHandler handles trace-related HTTP requests.
type TraceHandler struct {
	conn clickhouse.Conn
}

// NewTraceHandler creates a TraceHandler backed by conn.
func NewTraceHandler(conn clickhouse.Conn) *TraceHandler {
	return &TraceHandler{conn: conn}
}

type executionRow struct {
	ExecutionID string     `json:"executionId"`
	TenantID    string     `json:"tenantId"`
	AgentID     string     `json:"agentId"`
	Status      string     `json:"status"`
	StartedAt   time.Time  `json:"startedAt"`
	FinishedAt  *time.Time `json:"finishedAt,omitempty"`
	DurationMs  int64      `json:"durationMs"`
	NodeCount   int32      `json:"nodeCount"`
	ErrorMsg    string     `json:"errorMsg,omitempty"`
}

type nodeExecutionRow struct {
	NodeExecutionID string     `json:"nodeExecutionId"`
	ExecutionID     string     `json:"executionId"`
	TenantID        string     `json:"tenantId"`
	NodeID          string     `json:"nodeId"`
	NodeType        string     `json:"nodeType"`
	Status          string     `json:"status"`
	StartedAt       time.Time  `json:"startedAt"`
	FinishedAt      *time.Time `json:"finishedAt,omitempty"`
	DurationMs      int64      `json:"durationMs"`
	InputTokens     int32      `json:"inputTokens"`
	OutputTokens    int32      `json:"outputTokens"`
	ErrorMsg        string     `json:"errorMsg,omitempty"`
}

type toolExecutionRow struct {
	ToolExecutionID string     `json:"toolExecutionId"`
	ExecutionID     string     `json:"executionId"`
	TenantID        string     `json:"tenantId"`
	SkillSlug       string     `json:"skillSlug"`
	ToolType        string     `json:"toolType"`
	Status          string     `json:"status"`
	StartedAt       time.Time  `json:"startedAt"`
	FinishedAt      *time.Time `json:"finishedAt,omitempty"`
	DurationMs      int64      `json:"durationMs"`
	ErrorMsg        string     `json:"errorMsg,omitempty"`
}

type executionCountRow struct {
	Status string `json:"status"`
	Count  uint64 `json:"count"`
}

type pageResponse[T any] struct {
	Content       []T `json:"content"`
	TotalElements int `json:"totalElements"`
	Page          int `json:"page"`
	Size          int `json:"size"`
}

// RegisterRoutes mounts all trace routes onto r.
func (h *TraceHandler) RegisterRoutes(r chi.Router) {
	// Legacy routes
	r.Get("/api/traces", h.listExecutions)
	r.Get("/api/traces/{executionId}", h.getExecution)

	// V1 execution traces
	r.Post("/api/v1/traces/executions", h.createExecution)
	r.Put("/api/v1/traces/executions/{executionId}", h.updateExecution)
	r.Get("/api/v1/traces/executions/{executionId}", h.getExecution)
	r.Get("/api/v1/traces/executions", h.listExecutions)
	r.Get("/api/v1/traces/executions/by-agent", h.listByAgent)
	r.Get("/api/v1/traces/executions/by-period", h.listByPeriod)
	r.Get("/api/v1/traces/executions/{executionId}/nodes", h.listNodeTraces)

	// V1 tool traces
	r.Post("/api/v1/traces/tools", h.createToolTrace)
	r.Get("/api/v1/traces/tools/by-skill", h.listToolsBySkill)
	r.Get("/api/v1/traces/tools/by-type", h.listToolsByType)

	// V1 stats
	r.Get("/api/v1/traces/stats/executions/count", h.countExecutions)
}

func (h *TraceHandler) createExecution(w http.ResponseWriter, r *http.Request) {
	var req executionRow
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, "invalid request body", http.StatusBadRequest)
		return
	}
	req.TenantID = tenant.FromContext(r.Context())
	if req.ExecutionID == "" {
		jsonError(w, "executionId is required", http.StatusBadRequest)
		return
	}
	if req.StartedAt.IsZero() {
		req.StartedAt = time.Now().UTC()
	}
	if err := h.conn.Exec(r.Context(),
		"INSERT INTO agent_executions (execution_id, tenant_id, agent_id, status, started_at, finished_at, duration_ms, node_count, error_msg) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
		req.ExecutionID, req.TenantID, req.AgentID, req.Status,
		req.StartedAt.UTC(), req.FinishedAt, req.DurationMs, req.NodeCount, req.ErrorMsg,
	); err != nil {
		jsonError(w, "insert failed", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(req) //nolint:errcheck
}

func (h *TraceHandler) updateExecution(w http.ResponseWriter, r *http.Request) {
	executionID := chi.URLParam(r, "executionId")
	tenantID := tenant.FromContext(r.Context())
	var req struct {
		Status     string     `json:"status"`
		FinishedAt *time.Time `json:"finishedAt"`
		DurationMs int64      `json:"durationMs"`
		NodeCount  int32      `json:"nodeCount"`
		ErrorMsg   string     `json:"errorMsg"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if err := h.conn.Exec(r.Context(),
		"ALTER TABLE agent_executions UPDATE status = ?, finished_at = ?, duration_ms = ?, node_count = ?, error_msg = ? WHERE execution_id = ? AND tenant_id = ?",
		req.Status, req.FinishedAt, req.DurationMs, req.NodeCount, req.ErrorMsg, executionID, tenantID,
	); err != nil {
		jsonError(w, "update failed", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *TraceHandler) getExecution(w http.ResponseWriter, r *http.Request) {
	executionID := chi.URLParam(r, "executionId")
	tenantID := tenant.FromContext(r.Context())
	var e executionRow
	if err := h.conn.QueryRow(r.Context(),
		"SELECT execution_id, tenant_id, agent_id, status, started_at, finished_at, duration_ms, node_count, error_msg FROM agent_executions WHERE execution_id = ? AND tenant_id = ? LIMIT 1",
		executionID, tenantID,
	).Scan(&e.ExecutionID, &e.TenantID, &e.AgentID, &e.Status, &e.StartedAt, &e.FinishedAt, &e.DurationMs, &e.NodeCount, &e.ErrorMsg); err != nil {
		jsonError(w, "not found", http.StatusNotFound)
		return
	}
	writeJSON(w, e)
}

func (h *TraceHandler) listExecutions(w http.ResponseWriter, r *http.Request) {
	tenantID := tenant.FromContext(r.Context())
	limit := queryInt(r, "limit", 100)
	page := queryInt(r, "page", 0)
	size := queryInt(r, "size", limit)
	agentID := r.URL.Query().Get("agentId")
	status := r.URL.Query().Get("status")
	from, to, err := parseTimeRange(r.URL.Query().Get("from"), r.URL.Query().Get("to"))
	if err != nil {
		jsonError(w, "invalid date format, use ISO 8601", http.StatusBadRequest)
		return
	}

	args := []any{tenantID}
	where := "tenant_id = ?"
	if agentID != "" {
		where += " AND agent_id = ?"
		args = append(args, agentID)
	}
	if status != "" {
		where += " AND status = ?"
		args = append(args, status)
	}
	if from != nil {
		where += " AND started_at >= ?"
		args = append(args, *from)
	}
	if to != nil {
		where += " AND started_at <= ?"
		args = append(args, *to)
	}

	countArgs := make([]any, len(args))
	copy(countArgs, args)
	var total uint64
	if err := h.conn.QueryRow(r.Context(), "SELECT count() FROM agent_executions WHERE "+where, countArgs...).Scan(&total); err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}

	offset := page * size
	args = append(args, size, offset)
	rows, err := h.conn.Query(r.Context(),
		"SELECT execution_id, tenant_id, agent_id, status, started_at, finished_at, duration_ms, node_count, error_msg FROM agent_executions WHERE "+where+" ORDER BY started_at DESC LIMIT ? OFFSET ?",
		args...,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer func() { _ = rows.Close() }()

	results := scanExecutions(w, rows)
	if results == nil {
		return
	}
	writeJSON(w, pageResponse[executionRow]{Content: results, TotalElements: int(total), Page: page, Size: size})
}

func (h *TraceHandler) listByAgent(w http.ResponseWriter, r *http.Request) {
	tenantID := tenant.FromContext(r.Context())
	agentID := r.URL.Query().Get("agentId")
	if agentID == "" {
		jsonError(w, "agentId is required", http.StatusBadRequest)
		return
	}
	limit := queryInt(r, "limit", 100)
	rows, err := h.conn.Query(r.Context(),
		"SELECT execution_id, tenant_id, agent_id, status, started_at, finished_at, duration_ms, node_count, error_msg FROM agent_executions WHERE tenant_id = ? AND agent_id = ? ORDER BY started_at DESC LIMIT ?",
		tenantID, agentID, limit,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer func() { _ = rows.Close() }()
	results := scanExecutions(w, rows)
	if results != nil {
		writeJSON(w, results)
	}
}

func (h *TraceHandler) listByPeriod(w http.ResponseWriter, r *http.Request) {
	tenantID := tenant.FromContext(r.Context())
	from, to, err := parseTimeRange(r.URL.Query().Get("startDate"), r.URL.Query().Get("endDate"))
	if err != nil || from == nil || to == nil {
		jsonError(w, "startDate and endDate are required (ISO 8601)", http.StatusBadRequest)
		return
	}
	limit := queryInt(r, "limit", 100)
	rows, err := h.conn.Query(r.Context(),
		"SELECT execution_id, tenant_id, agent_id, status, started_at, finished_at, duration_ms, node_count, error_msg FROM agent_executions WHERE tenant_id = ? AND started_at >= ? AND started_at <= ? ORDER BY started_at DESC LIMIT ?",
		tenantID, *from, *to, limit,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer func() { _ = rows.Close() }()
	results := scanExecutions(w, rows)
	if results != nil {
		writeJSON(w, results)
	}
}

func (h *TraceHandler) listNodeTraces(w http.ResponseWriter, r *http.Request) {
	executionID := chi.URLParam(r, "executionId")
	tenantID := tenant.FromContext(r.Context())
	rows, err := h.conn.Query(r.Context(),
		"SELECT node_execution_id, execution_id, tenant_id, node_id, node_type, status, started_at, finished_at, duration_ms, input_tokens, output_tokens, error_msg FROM node_executions WHERE execution_id = ? AND tenant_id = ? ORDER BY started_at ASC",
		executionID, tenantID,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer func() { _ = rows.Close() }()

	results := make([]nodeExecutionRow, 0)
	for rows.Next() {
		var n nodeExecutionRow
		if err := rows.Scan(&n.NodeExecutionID, &n.ExecutionID, &n.TenantID, &n.NodeID, &n.NodeType, &n.Status, &n.StartedAt, &n.FinishedAt, &n.DurationMs, &n.InputTokens, &n.OutputTokens, &n.ErrorMsg); err != nil {
			jsonError(w, "scan failed", http.StatusInternalServerError)
			return
		}
		results = append(results, n)
	}
	writeJSON(w, results)
}

func (h *TraceHandler) createToolTrace(w http.ResponseWriter, r *http.Request) {
	var req toolExecutionRow
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, "invalid request body", http.StatusBadRequest)
		return
	}
	req.TenantID = tenant.FromContext(r.Context())
	if req.ToolExecutionID == "" {
		jsonError(w, "toolExecutionId is required", http.StatusBadRequest)
		return
	}
	if req.StartedAt.IsZero() {
		req.StartedAt = time.Now().UTC()
	}
	if err := h.conn.Exec(r.Context(),
		"INSERT INTO tool_executions (tool_execution_id, execution_id, tenant_id, skill_slug, tool_type, status, started_at, finished_at, duration_ms, error_msg) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
		req.ToolExecutionID, req.ExecutionID, req.TenantID, req.SkillSlug, req.ToolType, req.Status,
		req.StartedAt.UTC(), req.FinishedAt, req.DurationMs, req.ErrorMsg,
	); err != nil {
		jsonError(w, "insert failed", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(req) //nolint:errcheck
}

func (h *TraceHandler) listToolsBySkill(w http.ResponseWriter, r *http.Request) {
	tenantID := tenant.FromContext(r.Context())
	skillSlug := r.URL.Query().Get("skillSlug")
	if skillSlug == "" {
		jsonError(w, "skillSlug is required", http.StatusBadRequest)
		return
	}
	limit := queryInt(r, "limit", 100)
	rows, err := h.conn.Query(r.Context(),
		"SELECT tool_execution_id, execution_id, tenant_id, skill_slug, tool_type, status, started_at, finished_at, duration_ms, error_msg FROM tool_executions WHERE tenant_id = ? AND skill_slug = ? ORDER BY started_at DESC LIMIT ?",
		tenantID, skillSlug, limit,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer func() { _ = rows.Close() }()
	results := scanToolExecutions(w, rows)
	if results != nil {
		writeJSON(w, results)
	}
}

func (h *TraceHandler) listToolsByType(w http.ResponseWriter, r *http.Request) {
	tenantID := tenant.FromContext(r.Context())
	toolType := r.URL.Query().Get("toolType")
	if toolType == "" {
		jsonError(w, "toolType is required", http.StatusBadRequest)
		return
	}
	limit := queryInt(r, "limit", 100)
	rows, err := h.conn.Query(r.Context(),
		"SELECT tool_execution_id, execution_id, tenant_id, skill_slug, tool_type, status, started_at, finished_at, duration_ms, error_msg FROM tool_executions WHERE tenant_id = ? AND tool_type = ? ORDER BY started_at DESC LIMIT ?",
		tenantID, toolType, limit,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer func() { _ = rows.Close() }()
	results := scanToolExecutions(w, rows)
	if results != nil {
		writeJSON(w, results)
	}
}

func (h *TraceHandler) countExecutions(w http.ResponseWriter, r *http.Request) {
	tenantID := tenant.FromContext(r.Context())
	status := r.URL.Query().Get("status")
	sinceStr := r.URL.Query().Get("since")

	args := []any{tenantID}
	where := "tenant_id = ?"
	if status != "" {
		where += " AND status = ?"
		args = append(args, status)
	}
	if sinceStr != "" {
		since, err := time.Parse(time.RFC3339, sinceStr)
		if err != nil {
			jsonError(w, "invalid since format, use ISO 8601", http.StatusBadRequest)
			return
		}
		where += " AND started_at >= ?"
		args = append(args, since.UTC())
	}

	var count uint64
	if err := h.conn.QueryRow(r.Context(), "SELECT count() FROM agent_executions WHERE "+where, args...).Scan(&count); err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, executionCountRow{Status: status, Count: count})
}

func scanExecutions(w http.ResponseWriter, rows driver.Rows) []executionRow {
	results := make([]executionRow, 0)
	for rows.Next() {
		var e executionRow
		if err := rows.Scan(&e.ExecutionID, &e.TenantID, &e.AgentID, &e.Status, &e.StartedAt, &e.FinishedAt, &e.DurationMs, &e.NodeCount, &e.ErrorMsg); err != nil {
			jsonError(w, "scan failed", http.StatusInternalServerError)
			return nil
		}
		results = append(results, e)
	}
	return results
}

func scanToolExecutions(w http.ResponseWriter, rows driver.Rows) []toolExecutionRow {
	results := make([]toolExecutionRow, 0)
	for rows.Next() {
		var t toolExecutionRow
		if err := rows.Scan(&t.ToolExecutionID, &t.ExecutionID, &t.TenantID, &t.SkillSlug, &t.ToolType, &t.Status, &t.StartedAt, &t.FinishedAt, &t.DurationMs, &t.ErrorMsg); err != nil {
			jsonError(w, "scan failed", http.StatusInternalServerError)
			return nil
		}
		results = append(results, t)
	}
	return results
}

func parseTimeRange(fromStr, toStr string) (*time.Time, *time.Time, error) {
	var from, to *time.Time
	if fromStr != "" {
		t, err := time.Parse(time.RFC3339, fromStr)
		if err != nil {
			return nil, nil, err
		}
		from = &t
	}
	if toStr != "" {
		t, err := time.Parse(time.RFC3339, toStr)
		if err != nil {
			return nil, nil, err
		}
		to = &t
	}
	return from, to, nil
}

func queryInt(r *http.Request, key string, def int) int {
	if v := r.URL.Query().Get(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v) //nolint:errcheck
}

func jsonError(w http.ResponseWriter, msg string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": msg}) //nolint:errcheck
}
