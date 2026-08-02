package handler

import (
	"net/http"

	"github.com/AgentHub-Studio/agenthub-go-commons/tenant"
)

func requireTenantContext(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if tenant.FromContext(r.Context()) == "" {
			jsonError(w, "tenant context is required", http.StatusUnauthorized)
			return
		}
		next.ServeHTTP(w, r)
	})
}
