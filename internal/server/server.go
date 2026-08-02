package server

import (
	"encoding/json"
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
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"}) //nolint:errcheck
	})

	r.Get("/metrics", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if c == nil {
			json.NewEncoder(w).Encode(map[string]any{"consumer": nil}) //nolint:errcheck
			return
		}
		m := c.Metrics()
		json.NewEncoder(w).Encode(map[string]any{ //nolint:errcheck
			"consumer": map[string]any{
				"eventsProcessed": m.EventsProcessed,
				"eventsErrored":   m.EventsErrored,
			},
		})
	})

	r.Get("/ready", func(w http.ResponseWriter, r *http.Request) {
		if err := s.ch.Ping(r.Context()); err != nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			json.NewEncoder(w).Encode(map[string]string{"status": "unavailable"}) //nolint:errcheck
			return
		}
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"}) //nolint:errcheck
	})

	s.router = r
	return s
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
