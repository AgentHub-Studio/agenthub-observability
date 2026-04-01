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
	"github.com/AgentHub-Studio/agenthub-observability/internal/consumer"
	"github.com/AgentHub-Studio/agenthub-observability/internal/database"
	"github.com/AgentHub-Studio/agenthub-observability/internal/server"
	"github.com/AgentHub-Studio/agenthub-observability/internal/writer"
)

const aggregationInterval = 5 * time.Minute

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("failed to load config", "err", err)
		os.Exit(1)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	ch, err := database.NewClickHouse(ctx, cfg.ClickHouseURL)
	if err != nil {
		slog.Error("failed to connect to ClickHouse", "err", err)
		os.Exit(1)
	}
	defer ch.Close()

	w := writer.NewWriter(ch)
	if err := w.CreateTables(ctx); err != nil {
		slog.Error("failed to create ClickHouse tables", "err", err)
		os.Exit(1)
	}

	c := consumer.New(
		cfg.RabbitMQURL,
		func(batchCtx context.Context, events []consumer.ExecutionEvent) error {
			return w.BulkInsertExecutions(batchCtx, events)
		},
		func(batchCtx context.Context, events []consumer.NodeEvent) error {
			return w.BulkInsertNodeExecutions(batchCtx, events)
		},
	)

	go c.Run(ctx)

	agg := database.NewAggregator(ch, aggregationInterval)
	go agg.Run(ctx)

	srv := server.New(cfg, ch, c)

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
	cancel() // signal consumer to stop

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer shutdownCancel()
	httpServer.Shutdown(shutdownCtx) //nolint:errcheck
	slog.Info("observability stopped")
}
