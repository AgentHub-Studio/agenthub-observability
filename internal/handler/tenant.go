package handler

import (
	"net/http"
	"strings"

	"github.com/AgentHub-Studio/agenthub-go-commons/tenant"
)

func tenantIDFromRequest(r *http.Request) string {
	if tenantID := strings.TrimSpace(tenant.FromContext(r.Context())); tenantID != "" {
		return tenantID
	}
	return strings.TrimSpace(r.URL.Query().Get("tenantId"))
}

func tenantIDFromRequestOrValue(r *http.Request, value string) string {
	if tenantID := tenantIDFromRequest(r); tenantID != "" {
		return tenantID
	}
	return strings.TrimSpace(value)
}

func requireTenantID(w http.ResponseWriter, r *http.Request) (string, bool) {
	tenantID := tenantIDFromRequest(r)
	if tenantID == "" {
		jsonError(w, "tenantId is required", http.StatusBadRequest)
		return "", false
	}
	return tenantID, true
}
