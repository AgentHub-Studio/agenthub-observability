package server

import (
	"encoding/json"
	"net/http"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/go-chi/chi/v5"

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

	handler.RegisterAll(r, ch)

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
