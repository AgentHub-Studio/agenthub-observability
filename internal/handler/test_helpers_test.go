package handler

import (
	"io"
	"net/http"
	"net/http/httptest"

	"github.com/AgentHub-Studio/agenthub-go-commons/tenant"
)

func tenantRequest(method, target string, body io.Reader) *http.Request {
	req := httptest.NewRequest(method, target, body)
	return req.WithContext(tenant.NewContext(req.Context(), "tenant-a"))
}
