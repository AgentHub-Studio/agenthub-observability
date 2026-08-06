package server

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strings"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/go-chi/chi/v5"

	"github.com/AgentHub-Studio/agenthub-go-commons/auth"
	"github.com/AgentHub-Studio/agenthub-go-commons/tenant"
	"github.com/AgentHub-Studio/agenthub-observability/internal/config"
	"github.com/AgentHub-Studio/agenthub-observability/internal/consumer"
	"github.com/AgentHub-Studio/agenthub-observability/internal/handler"
)

// Server wraps the HTTP router and ClickHouse connection.
type Server struct {
	router http.Handler
	ch     clickhouse.Conn
}

// New creates a Server with health/ready endpoints wired.
// Pass a non-nil consumer to expose its runtime metrics at GET /metrics.
func New(cfg *config.Config, ch clickhouse.Conn, c *consumer.Consumer) *Server {
	s := &Server{ch: ch}
	r := chi.NewRouter()

	// CORS at root level so OPTIONS preflight returns 204 before chi returns 405.
	r.Use(corsMiddleware(cfg.CORSOrigins))
	r.Options("/*", func(w http.ResponseWriter, r *http.Request) {})

	// /api/* routes require JWT + tenant context. Health/ready/metrics stay public.
	r.Group(func(pr chi.Router) {
		if cfg.KeycloakBaseURL != "" {
			pr.Use(auth.Middleware(auth.Config{KeycloakBaseURL: cfg.KeycloakBaseURL}))
			pr.Use(tenant.Middleware())
		}
		handler.RegisterAll(pr, ch)
	})

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})

	r.Get("/metrics", func(w http.ResponseWriter, r *http.Request) {
		if c == nil {
			writeJSON(w, http.StatusOK, map[string]any{"consumer": nil})
			return
		}
		m := c.Metrics()
		writeJSON(w, http.StatusOK, map[string]any{
			"consumer": map[string]any{
				"eventsProcessed": m.EventsProcessed,
				"eventsErrored":   m.EventsErrored,
			},
		})
	})

	r.Get("/ready", func(w http.ResponseWriter, r *http.Request) {
		if err := s.ch.Ping(r.Context()); err != nil {
			writeJSON(w, http.StatusServiceUnavailable, map[string]string{"status": "unavailable"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})

	s.router = r
	return s
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		slog.Error("server: write JSON response failed", "err", err)
	}
}

// ServeHTTP implements http.Handler.
func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	s.router.ServeHTTP(w, r)
}

// corsMiddleware sets CORS headers and handles OPTIONS preflight.
func corsMiddleware(origins []string) func(http.Handler) http.Handler {
	allowAll := len(origins) == 0
	originsMap := make(map[string]bool, len(origins))
	for _, o := range origins {
		if strings.TrimSpace(o) == "*" {
			allowAll = true
			break
		}
		originsMap[strings.TrimSpace(o)] = true
	}
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")
			if origin != "" && (allowAll || originsMap[origin]) {
				w.Header().Set("Access-Control-Allow-Origin", origin)
				w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
				w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-ID")
				w.Header().Set("Access-Control-Max-Age", "86400")
			}
			if r.Method == http.MethodOptions {
				w.WriteHeader(http.StatusNoContent)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
