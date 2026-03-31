package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/AgentHub-Studio/agenthub-observability/internal/config"
	"github.com/AgentHub-Studio/agenthub-observability/internal/database"
	"github.com/AgentHub-Studio/agenthub-observability/internal/server"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("failed to load config", "err", err)
		os.Exit(1)
	}

	ctx := context.Background()

	ch, err := database.NewClickHouse(ctx, cfg.ClickHouseURL)
	if err != nil {
		slog.Error("failed to connect to ClickHouse", "err", err)
		os.Exit(1)
	}
	defer ch.Close()

	srv := server.New(cfg, ch)

	httpServer := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      srv,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
	}

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		slog.Info("observability starting", "addr", httpServer.Addr)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("server error", "err", err)
			os.Exit(1)
		}
	}()

	<-quit
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	httpServer.Shutdown(shutdownCtx) //nolint:errcheck
	slog.Info("observability stopped")
}
