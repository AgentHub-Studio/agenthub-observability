package server

import (
	"encoding/json"
	"net/http"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/go-chi/chi/v5"

	"github.com/AgentHub-Studio/agenthub-observability/internal/config"
)

// Server wraps the HTTP router and ClickHouse connection.
type Server struct {
	router http.Handler
	ch     clickhouse.Conn
}

// New creates a Server with health/ready endpoints wired.
func New(cfg *config.Config, ch clickhouse.Conn) *Server {
	s := &Server{ch: ch}
	r := chi.NewRouter()

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"}) //nolint:errcheck
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
