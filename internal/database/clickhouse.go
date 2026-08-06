package database

import (
	"context"
	"fmt"

	"github.com/ClickHouse/clickhouse-go/v2"
)

// NewClickHouse creates and validates a ClickHouse connection.
func NewClickHouse(ctx context.Context, url string) (clickhouse.Conn, error) {
	opts, err := clickhouse.ParseDSN(url)
	if err != nil {
		return nil, fmt.Errorf("clickhouse: parse DSN: %w", err)
	}

	conn, err := clickhouse.Open(opts)
	if err != nil {
		return nil, fmt.Errorf("clickhouse: open connection: %w", err)
	}

	if err := conn.Ping(ctx); err != nil {
		if closeErr := conn.Close(); closeErr != nil {
			return nil, fmt.Errorf("clickhouse: ping: %w; close connection: %v", err, closeErr)
		}
		return nil, fmt.Errorf("clickhouse: ping: %w", err)
	}

	return conn, nil
}
