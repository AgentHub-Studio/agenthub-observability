// Package handler provides HTTP handlers for the observability REST API.
package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/ClickHouse/clickhouse-go/v2"
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

// executionRow mirrors the agent_executions table columns.
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

// pageResponse is the standard paginated response envelope.
type pageResponse[T any] struct {
	Content       []T `json:"content"`
	TotalElements int `json:"totalElements"`
	Page          int `json:"page"`
	Size          int `json:"size"`
}

// RegisterRoutes mounts all trace routes onto the given router.
func (h *TraceHandler) RegisterRoutes(r chi.Router) {
	r.Get("/api/traces", h.listTraces)
	r.Get("/api/traces/{executionId}", h.getTrace)
}

// listTraces handles GET /api/traces
//
// Query params: tenantId (required), agentId, status, from, to, page, size.
func (h *TraceHandler) listTraces(w http.ResponseWriter, r *http.Request) {
	tenantID := r.URL.Query().Get("tenantId")
	if tenantID == "" {
		http.Error(w, `{"error":"tenantId is required"}`, http.StatusBadRequest)
		return
	}

	agentID := r.URL.Query().Get("agentId")
	status := r.URL.Query().Get("status")
	fromStr := r.URL.Query().Get("from")
	toStr := r.URL.Query().Get("to")
	page := queryInt(r, "page", 0)
	size := queryInt(r, "size", 20)

	from, to, err := parseTimeRange(fromStr, toStr)
	if err != nil {
		http.Error(w, `{"error":"invalid date format, use ISO 8601"}`, http.StatusBadRequest)
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

	// Count query
	countArgs := make([]any, len(args))
	copy(countArgs, args)

	var total uint64
	if err := h.conn.QueryRow(r.Context(),
		"SELECT count() FROM agent_executions WHERE "+where,
		countArgs...,
	).Scan(&total); err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}

	// Data query
	offset := page * size
	args = append(args, size, offset)

	rows, err := h.conn.Query(r.Context(),
		"SELECT execution_id, tenant_id, agent_id, status, started_at, finished_at, duration_ms, node_count, error_msg"+
			" FROM agent_executions WHERE "+where+
			" ORDER BY started_at DESC LIMIT ? OFFSET ?",
		args...,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	results := make([]executionRow, 0, size)
	for rows.Next() {
		var e executionRow
		if err := rows.Scan(
			&e.ExecutionID, &e.TenantID, &e.AgentID, &e.Status,
			&e.StartedAt, &e.FinishedAt, &e.DurationMs, &e.NodeCount, &e.ErrorMsg,
		); err != nil {
			jsonError(w, "scan failed", http.StatusInternalServerError)
			return
		}
		results = append(results, e)
	}

	writeJSON(w, pageResponse[executionRow]{
		Content:       results,
		TotalElements: int(total),
		Page:          page,
		Size:          size,
	})
}

// getTrace handles GET /api/traces/{executionId}
//
// Query params: tenantId (required).
func (h *TraceHandler) getTrace(w http.ResponseWriter, r *http.Request) {
	executionID := chi.URLParam(r, "executionId")
	tenantID := r.URL.Query().Get("tenantId")
	if tenantID == "" {
		http.Error(w, `{"error":"tenantId is required"}`, http.StatusBadRequest)
		return
	}

	var e executionRow
	err := h.conn.QueryRow(r.Context(),
		"SELECT execution_id, tenant_id, agent_id, status, started_at, finished_at, duration_ms, node_count, error_msg"+
			" FROM agent_executions WHERE execution_id = ? AND tenant_id = ? LIMIT 1",
		executionID, tenantID,
	).Scan(&e.ExecutionID, &e.TenantID, &e.AgentID, &e.Status,
		&e.StartedAt, &e.FinishedAt, &e.DurationMs, &e.NodeCount, &e.ErrorMsg)
	if err != nil {
		jsonError(w, "not found", http.StatusNotFound)
		return
	}

	writeJSON(w, e)
}

// parseTimeRange parses optional ISO-8601 from/to strings.
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

// queryInt reads an int query param, falling back to def on parse failure.
func queryInt(r *http.Request, key string, def int) int {
	if v := r.URL.Query().Get(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

// writeJSON writes v as an indented JSON response.
func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v) //nolint:errcheck
}

// jsonError writes a simple JSON error response.
func jsonError(w http.ResponseWriter, msg string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": msg}) //nolint:errcheck
}
