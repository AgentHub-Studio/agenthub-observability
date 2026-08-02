package handler

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/go-chi/chi/v5"
	"github.com/stretchr/testify/assert"
)

type executionUpdateConn struct {
	clickhouse.Conn
	query string
	args  []any
}

func (c *executionUpdateConn) Exec(_ context.Context, query string, args ...any) error {
	c.query = query
	c.args = args
	return nil
}

func TestTraceRoutes_RejectMissingTenantContext(t *testing.T) {
	r := chi.NewRouter()
	RegisterAll(r, nil)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/executions", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.Contains(t, rec.Body.String(), "tenant context is required")
}

func TestGetExecution_RejectsMissingTenantContext(t *testing.T) {
	r := chi.NewRouter()
	RegisterAll(r, nil)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/executions/abc", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func TestCreateExecution_InvalidBody(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodPost, "/api/v1/traces/executions", strings.NewReader("not-json"))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestCreateExecution_MissingRequired(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodPost, "/api/v1/traces/executions", strings.NewReader(`{}`))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
	assert.Contains(t, rec.Body.String(), "executionId is required")
}

func TestUpdateExecution_ScopesMutationToContextTenant(t *testing.T) {
	conn := &executionUpdateConn{}
	h := &TraceHandler{conn: conn}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodPut, "/api/v1/traces/executions/execution-a", strings.NewReader(`{"status":"SUCCESS"}`))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusNoContent, rec.Code)
	assert.Contains(t, conn.query, "WHERE execution_id = ? AND tenant_id = ?")
	assert.Len(t, conn.args, 7)
	assert.Equal(t, "execution-a", conn.args[5])
	assert.Equal(t, "tenant-a", conn.args[6])
}

func TestListByAgent_MissingParams(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodGet, "/api/v1/traces/executions/by-agent?tenantId=other-tenant", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestListByPeriod_MissingDates(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodGet, "/api/v1/traces/executions/by-period?tenantId=other-tenant", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestListByPeriod_InvalidDate(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodGet, "/api/v1/traces/executions/by-period?tenantId=other-tenant&startDate=not-a-date&endDate=not-a-date", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestTraceCount_RejectsMissingTenantContext(t *testing.T) {
	r := chi.NewRouter()
	RegisterAll(r, nil)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/traces/stats/executions/count", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func TestCountExecutions_InvalidSince(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodGet, "/api/v1/traces/stats/executions/count?tenantId=other-tenant&since=invalid", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestCreateToolTrace_MissingRequired(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodPost, "/api/v1/traces/tools", strings.NewReader(`{}`))
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestListToolsBySkill_MissingParams(t *testing.T) {
	h := &TraceHandler{conn: nil}
	r := chi.NewRouter()
	h.RegisterRoutes(r)

	req := tenantRequest(http.MethodGet, "/api/v1/traces/tools/by-skill?tenantId=other-tenant", nil)
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
