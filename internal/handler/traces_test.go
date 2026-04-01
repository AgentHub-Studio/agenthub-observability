package handler

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/stretchr/testify/assert"
)

func TestListExecutions_MissingTenantID(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/executions", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
	assert.Contains(t, rec.Body.String(), "tenantId is required")
}

func TestGetExecution_MissingTenantID(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/executions/abc", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestCreateExecution_InvalidBody(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/traces/executions", strings.NewReader("not-json"))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestCreateExecution_MissingRequired(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/traces/executions", strings.NewReader(`{}`))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
	assert.Contains(t, rec.Body.String(), "executionId and tenantId are required")
}

func TestListByAgent_MissingParams(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/executions/by-agent?tenantId=test", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestListByPeriod_MissingDates(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/executions/by-period?tenantId=test", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestListByPeriod_InvalidDate(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/executions/by-period?tenantId=test&startDate=not-a-date&endDate=not-a-date", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestCountExecutions_MissingTenantID(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/stats/executions/count", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestCountExecutions_InvalidSince(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/stats/executions/count?tenantId=test&since=invalid", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestCreateToolTrace_MissingRequired(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/traces/tools", strings.NewReader(`{}`))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestListToolsBySkill_MissingParams(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/tools/by-skill?tenantId=test", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestParseTimeRange_Valid(t *testing.T) {
	from, to, err := parseTimeRange("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z")
	assert.NoError(t, err)
	assert.NotNil(t, from)
	assert.NotNil(t, to)
	assert.Equal(t, 2026, from.Year())
}

func TestParseTimeRange_Invalid(t *testing.T) {
	_, _, err := parseTimeRange("not-a-date", "")
	assert.Error(t, err)
}

func TestQueryInt_Default(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	assert.Equal(t, 42, queryInt(req, "missing", 42))
}

func TestQueryInt_Valid(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/?n=7", nil)
	assert.Equal(t, 7, queryInt(req, "n", 0))
}
