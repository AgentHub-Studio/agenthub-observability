package handler

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/stretchr/testify/assert"
)

func TestCreateEvent_MissingRequired(t *testing.T) {
	h := &MetricHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodPost, "/api/v1/metrics/events", strings.NewReader(`{}`))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
	assert.Contains(t, rec.Body.String(), "metricName is required")
}

func TestCreateEvent_InvalidBody(t *testing.T) {
	h := &MetricHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodPost, "/api/v1/metrics/events", strings.NewReader("bad-json"))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestMetricRoutes_RejectMissingTenantContext(t *testing.T) {
	r := chi.NewRouter()
	RegisterAll(r, nil)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/metrics/events", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.Contains(t, rec.Body.String(), "tenant context is required")
}

func TestListEvents_InvalidDate(t *testing.T) {
	h := &MetricHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodGet, "/api/v1/metrics/events?tenantId=other-tenant&startDate=invalid", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestAggregated_InvalidDate(t *testing.T) {
	h := &MetricHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodGet, "/api/v1/metrics/aggregated?tenantId=other-tenant&startDate=invalid", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestConvenienceCounter_MissingParams(t *testing.T) {
	h := &MetricHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodPost, "/api/v1/metrics/counter", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestConvenienceGauge_InvalidValue(t *testing.T) {
	h := &MetricHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodPost, "/api/v1/metrics/gauge?tenantId=other-tenant&metricName=m&value=notanumber", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestMetricSummary_RejectsMissingTenantContext(t *testing.T) {
	r := chi.NewRouter()
	RegisterAll(r, nil)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/metrics/summary", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func TestPeriodTruncExpr(t *testing.T) {
	assert.Contains(t, periodTruncExpr("HOUR"), "Hour")
	assert.Contains(t, periodTruncExpr("DAY"), "Day")
	assert.Contains(t, periodTruncExpr("MONTH"), "Month")
	assert.Contains(t, periodTruncExpr("UNKNOWN"), "Hour")
}

func TestAggregationFunc(t *testing.T) {
	assert.Equal(t, "sum", aggregationFunc("SUM"))
	assert.Equal(t, "avg", aggregationFunc("AVG"))
	assert.Equal(t, "count", aggregationFunc("COUNT"))
	assert.Equal(t, "sum", aggregationFunc("UNKNOWN"))
}
