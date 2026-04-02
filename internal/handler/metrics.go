package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
)

// MetricHandler handles metrics-related HTTP requests.
type MetricHandler struct {
	conn clickhouse.Conn
}

// NewMetricHandler creates a MetricHandler backed by conn.
func NewMetricHandler(conn clickhouse.Conn) *MetricHandler {
	return &MetricHandler{conn: conn}
}

type metricEventRow struct {
	EventID    string    `json:"eventId"`
	TenantID   string    `json:"tenantId"`
	MetricName string    `json:"metricName"`
	MetricType string    `json:"metricType"`
	Value      float64   `json:"value"`
	Labels     string    `json:"labels,omitempty"`
	OccurredAt time.Time `json:"occurredAt"`
}

type aggregatedMetricRow struct {
	MetricName        string  `json:"metricName"`
	AggregationType   string  `json:"aggregationType"`
	AggregationPeriod string  `json:"aggregationPeriod"`
	Value             float64 `json:"value"`
	PeriodStart       string  `json:"periodStart"`
	PeriodEnd         string  `json:"periodEnd"`
}

// agentMetricRow is the per-agent metrics response.
type agentMetricRow struct {
	AgentID       string  `json:"agentId"`
	TotalRuns     uint64  `json:"totalRuns"`
	SuccessRate   float64 `json:"successRate"`
	AvgDurationMs float64 `json:"avgDurationMs"`
}

// summaryRow is the tenant-level summary response.
type summaryRow struct {
	TotalRuns     uint64  `json:"totalRuns"`
	SuccessRate   float64 `json:"successRate"`
	AvgDurationMs float64 `json:"avgDurationMs"`
	TotalAgents   uint64  `json:"totalAgents"`
}

// RegisterRoutes mounts all metric routes onto r.
func (h *MetricHandler) RegisterRoutes(r chi.Router) {
	// Legacy routes
	r.Get("/api/metrics/agents", h.agentMetrics)
	r.Get("/api/metrics/summary", h.summary)

	// V1 event routes
	r.Post("/api/v1/metrics/events", h.createEvent)
	r.Post("/api/v1/metrics/events/batch", h.createEventBatch)
	r.Get("/api/v1/metrics/events", h.listEvents)
	r.Get("/api/v1/metrics/aggregated", h.aggregated)

	// V1 metric names
	r.Get("/api/v1/metrics/names", h.listMetricNames)

	// V1 convenience routes
	r.Post("/api/v1/metrics/counter", h.createConvenience("COUNTER"))
	r.Post("/api/v1/metrics/gauge", h.createConvenience("GAUGE"))
	r.Post("/api/v1/metrics/timer", h.createConvenience("TIMER"))
	r.Post("/api/v1/metrics/histogram", h.createConvenience("HISTOGRAM"))

	// V1 agent/summary (aliased)
	r.Get("/api/v1/metrics/agents", h.agentMetrics)
	r.Get("/api/v1/metrics/summary", h.summary)
}

func (h *MetricHandler) createEvent(w http.ResponseWriter, r *http.Request) {
	var req metricEventRow
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if req.TenantID == "" || req.MetricName == "" {
		jsonError(w, "tenantId and metricName are required", http.StatusBadRequest)
		return
	}
	if req.EventID == "" {
		req.EventID = uuid.New().String()
	}
	if req.OccurredAt.IsZero() {
		req.OccurredAt = time.Now().UTC()
	}
	if err := h.insertEvent(r, req); err != nil {
		jsonError(w, "insert failed", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(req) //nolint:errcheck
}

func (h *MetricHandler) createEventBatch(w http.ResponseWriter, r *http.Request) {
	var events []metricEventRow
	if err := json.NewDecoder(r.Body).Decode(&events); err != nil {
		jsonError(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if len(events) == 0 {
		w.WriteHeader(http.StatusNoContent)
		return
	}

	batch, err := h.conn.PrepareBatch(r.Context(), "INSERT INTO metric_events")
	if err != nil {
		jsonError(w, "prepare batch failed", http.StatusInternalServerError)
		return
	}
	now := time.Now().UTC()
	for _, e := range events {
		if e.EventID == "" {
			e.EventID = uuid.New().String()
		}
		if e.OccurredAt.IsZero() {
			e.OccurredAt = now
		}
		if err := batch.Append(e.EventID, e.TenantID, e.MetricName, e.MetricType, e.Value, e.Labels, e.OccurredAt.UTC()); err != nil {
			jsonError(w, "batch append failed", http.StatusInternalServerError)
			return
		}
	}
	if err := batch.Send(); err != nil {
		jsonError(w, "batch send failed", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
}

func (h *MetricHandler) listEvents(w http.ResponseWriter, r *http.Request) {
	tenantID := r.URL.Query().Get("tenantId")
	if tenantID == "" {
		jsonError(w, "tenantId is required", http.StatusBadRequest)
		return
	}
	metricName := r.URL.Query().Get("metricName")
	limit := queryInt(r, "limit", 1000)
	from, to, err := parseTimeRange(r.URL.Query().Get("startDate"), r.URL.Query().Get("endDate"))
	if err != nil {
		jsonError(w, "invalid date format, use ISO 8601", http.StatusBadRequest)
		return
	}

	args := []any{tenantID}
	where := "tenant_id = ?"
	if metricName != "" {
		where += " AND metric_name = ?"
		args = append(args, metricName)
	}
	if from != nil {
		where += " AND occurred_at >= ?"
		args = append(args, *from)
	}
	if to != nil {
		where += " AND occurred_at <= ?"
		args = append(args, *to)
	}
	args = append(args, limit)

	rows, err := h.conn.Query(r.Context(),
		"SELECT event_id, tenant_id, metric_name, metric_type, value, labels, occurred_at FROM metric_events WHERE "+where+" ORDER BY occurred_at DESC LIMIT ?",
		args...,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	results := make([]metricEventRow, 0)
	for rows.Next() {
		var e metricEventRow
		if err := rows.Scan(&e.EventID, &e.TenantID, &e.MetricName, &e.MetricType, &e.Value, &e.Labels, &e.OccurredAt); err != nil {
			jsonError(w, "scan failed", http.StatusInternalServerError)
			return
		}
		results = append(results, e)
	}
	writeJSON(w, results)
}

func (h *MetricHandler) aggregated(w http.ResponseWriter, r *http.Request) {
	tenantID := r.URL.Query().Get("tenantId")
	metricName := r.URL.Query().Get("metricName")
	if tenantID == "" || metricName == "" {
		jsonError(w, "tenantId and metricName are required", http.StatusBadRequest)
		return
	}
	aggType := r.URL.Query().Get("aggregationType")
	aggPeriod := r.URL.Query().Get("aggregationPeriod")
	from, to, err := parseTimeRange(r.URL.Query().Get("startDate"), r.URL.Query().Get("endDate"))
	if err != nil {
		jsonError(w, "invalid date format, use ISO 8601", http.StatusBadRequest)
		return
	}

	// Build GROUP BY period
	periodExpr := periodTruncExpr(aggPeriod)

	// Build aggregation function
	aggFunc := aggregationFunc(aggType)

	args := []any{tenantID, metricName}
	where := "tenant_id = ? AND metric_name = ?"
	if from != nil {
		where += " AND occurred_at >= ?"
		args = append(args, *from)
	}
	if to != nil {
		where += " AND occurred_at <= ?"
		args = append(args, *to)
	}

	query := "SELECT metric_name, '" + aggType + "' AS aggregation_type, '" + aggPeriod + "' AS aggregation_period, " +
		aggFunc + "(value) AS agg_value, " +
		"toString(" + periodExpr + ") AS period_start, " +
		"toString(addSeconds(" + periodExpr + ", periodSeconds('" + periodInterval(aggPeriod) + "'))) AS period_end " +
		"FROM metric_events WHERE " + where + " " +
		"GROUP BY metric_name, " + periodExpr + " " +
		"ORDER BY " + periodExpr + " ASC"

	rows, err := h.conn.Query(r.Context(), query, args...)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	results := make([]aggregatedMetricRow, 0)
	for rows.Next() {
		var a aggregatedMetricRow
		if err := rows.Scan(&a.MetricName, &a.AggregationType, &a.AggregationPeriod, &a.Value, &a.PeriodStart, &a.PeriodEnd); err != nil {
			jsonError(w, "scan failed", http.StatusInternalServerError)
			return
		}
		results = append(results, a)
	}
	writeJSON(w, results)
}

func (h *MetricHandler) listMetricNames(w http.ResponseWriter, r *http.Request) {
	tenantID := r.URL.Query().Get("tenantId")
	if tenantID == "" {
		jsonError(w, "tenantId is required", http.StatusBadRequest)
		return
	}

	rows, err := h.conn.Query(r.Context(),
		"SELECT DISTINCT metric_name FROM metric_events WHERE tenant_id = ? ORDER BY metric_name ASC",
		tenantID,
	)
	if err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	names := make([]string, 0)
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			jsonError(w, "scan failed", http.StatusInternalServerError)
			return
		}
		names = append(names, name)
	}
	writeJSON(w, names)
}

// createConvenience returns a handler for POST /api/v1/metrics/{type} convenience endpoints.
// Params: tenantId, metricName, value (all via query string).
func (h *MetricHandler) createConvenience(metricType string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tenantID := r.URL.Query().Get("tenantId")
		metricName := r.URL.Query().Get("metricName")
		valueStr := r.URL.Query().Get("value")
		if tenantID == "" || metricName == "" {
			jsonError(w, "tenantId and metricName are required", http.StatusBadRequest)
			return
		}
		var value float64
		if valueStr != "" {
			v, err := strconv.ParseFloat(valueStr, 64)
			if err != nil {
				jsonError(w, "invalid value, must be a number", http.StatusBadRequest)
				return
			}
			value = v
		} else {
			value = 1.0
		}
		evt := metricEventRow{
			EventID:    uuid.New().String(),
			TenantID:   tenantID,
			MetricName: metricName,
			MetricType: metricType,
			Value:      value,
			OccurredAt: time.Now().UTC(),
		}
		if err := h.insertEvent(r, evt); err != nil {
			jsonError(w, "insert failed", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(evt) //nolint:errcheck
	}
}

func (h *MetricHandler) insertEvent(r *http.Request, e metricEventRow) error {
	return h.conn.Exec(r.Context(),
		"INSERT INTO metric_events (event_id, tenant_id, metric_name, metric_type, value, labels, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
		e.EventID, e.TenantID, e.MetricName, e.MetricType, e.Value, e.Labels, e.OccurredAt.UTC(),
	)
}

func (h *MetricHandler) agentMetrics(w http.ResponseWriter, r *http.Request) {
	tenantID := r.URL.Query().Get("tenantId")
	if tenantID == "" {
		jsonError(w, "tenantId is required", http.StatusBadRequest)
		return
	}
	from, to, err := parseTimeRange(r.URL.Query().Get("from"), r.URL.Query().Get("to"))
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
		"SELECT agent_id, count() AS total_runs, countIf(status = 'SUCCESS') AS success_runs, avg(duration_ms) AS avg_duration_ms FROM agent_executions WHERE "+where+" GROUP BY agent_id ORDER BY agent_id",
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
		results = append(results, agentMetricRow{AgentID: agentID, TotalRuns: totalRuns, SuccessRate: rate, AvgDurationMs: avgDuration})
	}
	writeJSON(w, results)
}

func (h *MetricHandler) summary(w http.ResponseWriter, r *http.Request) {
	tenantID := r.URL.Query().Get("tenantId")
	if tenantID == "" {
		jsonError(w, "tenantId is required", http.StatusBadRequest)
		return
	}
	var totalRuns, successRuns, totalAgents uint64
	var avgDuration float64
	if err := h.conn.QueryRow(r.Context(),
		"SELECT count() AS total_runs, countIf(status = 'SUCCESS') AS success_runs, avg(duration_ms) AS avg_duration_ms, uniqExact(agent_id) AS total_agents FROM agent_executions WHERE tenant_id = ?",
		tenantID,
	).Scan(&totalRuns, &successRuns, &avgDuration, &totalAgents); err != nil {
		jsonError(w, "query failed", http.StatusInternalServerError)
		return
	}
	var rate float64
	if totalRuns > 0 {
		rate = float64(successRuns) / float64(totalRuns)
	}
	writeJSON(w, summaryRow{TotalRuns: totalRuns, SuccessRate: rate, AvgDurationMs: avgDuration, TotalAgents: totalAgents})
}

// RegisterAll wires both trace and metric handlers onto r.
func RegisterAll(r chi.Router, conn clickhouse.Conn) {
	NewTraceHandler(conn).RegisterRoutes(r)
	NewMetricHandler(conn).RegisterRoutes(r)
}

// periodTruncExpr returns a ClickHouse expression to truncate occurred_at to the given period.
func periodTruncExpr(period string) string {
	switch period {
	case "HOUR":
		return "toStartOfHour(occurred_at)"
	case "DAY":
		return "toStartOfDay(occurred_at)"
	case "WEEK":
		return "toStartOfWeek(occurred_at)"
	case "MONTH":
		return "toStartOfMonth(occurred_at)"
	default:
		return "toStartOfHour(occurred_at)"
	}
}

// aggregationFunc returns the ClickHouse aggregation function for the given type.
func aggregationFunc(aggType string) string {
	switch aggType {
	case "SUM":
		return "sum"
	case "AVG":
		return "avg"
	case "MAX":
		return "max"
	case "MIN":
		return "min"
	case "COUNT":
		return "count"
	default:
		return "sum"
	}
}

// periodInterval returns a ClickHouse interval string for the given period.
func periodInterval(period string) string {
	switch period {
	case "HOUR":
		return "1 HOUR"
	case "DAY":
		return "1 DAY"
	case "WEEK":
		return "7 DAY"
	case "MONTH":
		return "1 MONTH"
	default:
		return "1 HOUR"
	}
}
