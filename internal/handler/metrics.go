package handler

import (
	"net/http"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/go-chi/chi/v5"
)

// MetricHandler handles metrics-related HTTP requests.
type MetricHandler struct {
	conn clickhouse.Conn
}

// NewMetricHandler creates a MetricHandler backed by conn.
func NewMetricHandler(conn clickhouse.Conn) *MetricHandler {
	return &MetricHandler{conn: conn}
}

// agentMetricRow is the response shape for per-agent aggregated metrics.
type agentMetricRow struct {
	AgentID       string  `json:"agentId"`
	TotalRuns     uint64  `json:"totalRuns"`
	SuccessRate   float64 `json:"successRate"`
	AvgDurationMs float64 `json:"avgDurationMs"`
}

// summaryRow is the response shape for the tenant-level summary.
type summaryRow struct {
	TotalRuns     uint64  `json:"totalRuns"`
	SuccessRate   float64 `json:"successRate"`
	AvgDurationMs float64 `json:"avgDurationMs"`
	TotalAgents   uint64  `json:"totalAgents"`
}

// RegisterRoutes mounts all metric routes onto the given router.
func (h *MetricHandler) RegisterRoutes(r chi.Router) {
	r.Get("/api/metrics/agents", h.agentMetrics)
	r.Get("/api/metrics/summary", h.summary)
}

// agentMetrics handles GET /api/metrics/agents
//
// Query params: tenantId (required), from, to.
func (h *MetricHandler) agentMetrics(w http.ResponseWriter, r *http.Request) {
	tenantID := r.URL.Query().Get("tenantId")
	if tenantID == "" {
		jsonError(w, "tenantId is required", http.StatusBadRequest)
		return
	}

	fromStr := r.URL.Query().Get("from")
	toStr := r.URL.Query().Get("to")
	from, to, err := parseTimeRange(fromStr, toStr)
	if err != nil {
		jsonError(w, "invalid date format, use ISO 8601", http.StatusBadRequest)
		return
	}

	args := []any{tenantID}
	where := "tenant_id = ?"

	if from != nil {
		where += " AND started_at >= ?"
		args = append(args, *from)
	}
	if to != nil {
		where += " AND started_at <= ?"
		args = append(args, *to)
	}

	rows, err := h.conn.Query(r.Context(),
		"SELECT agent_id,"+
			" count() AS total_runs,"+
			" countIf(status = 'SUCCESS') AS success_runs,"+
			" avg(duration_ms) AS avg_duration_ms"+
			" FROM agent_executions WHERE "+where+
			" GROUP BY agent_id ORDER BY agent_id",
		args...,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	results := make([]agentMetricRow, 0)
	for rows.Next() {
		var agentID string
		var totalRuns, successRuns uint64
		var avgDuration float64

		if err := rows.Scan(&agentID, &totalRuns, &successRuns, &avgDuration); err != nil {
			jsonError(w, "scan failed", http.StatusInternalServerError)
			return
		}

		var rate float64
		if totalRuns > 0 {
			rate = float64(successRuns) / float64(totalRuns)
		}

		results = append(results, agentMetricRow{
			AgentID:       agentID,
			TotalRuns:     totalRuns,
			SuccessRate:   rate,
			AvgDurationMs: avgDuration,
		})
	}

	writeJSON(w, results)
}

// summary handles GET /api/metrics/summary
//
// Query params: tenantId (required).
func (h *MetricHandler) summary(w http.ResponseWriter, r *http.Request) {
	tenantID := r.URL.Query().Get("tenantId")
	if tenantID == "" {
		jsonError(w, "tenantId is required", http.StatusBadRequest)
		return
	}

	var totalRuns, successRuns, totalAgents uint64
	var avgDuration float64

	err := h.conn.QueryRow(r.Context(),
		"SELECT"+
			" count() AS total_runs,"+
			" countIf(status = 'SUCCESS') AS success_runs,"+
			" avg(duration_ms) AS avg_duration_ms,"+
			" uniqExact(agent_id) AS total_agents"+
			" FROM agent_executions WHERE tenant_id = ?",
		tenantID,
	).Scan(&totalRuns, &successRuns, &avgDuration, &totalAgents)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}

	var rate float64
	if totalRuns > 0 {
		rate = float64(successRuns) / float64(totalRuns)
	}

	writeJSON(w, summaryRow{
		TotalRuns:     totalRuns,
		SuccessRate:   rate,
		AvgDurationMs: avgDuration,
		TotalAgents:   totalAgents,
	})
}

// RegisterRoutes is a convenience that wires both trace and metric handlers
// onto a single chi.Router.
func RegisterAll(r chi.Router, conn clickhouse.Conn) {
	NewTraceHandler(conn).RegisterRoutes(r)
	NewMetricHandler(conn).RegisterRoutes(r)
}
