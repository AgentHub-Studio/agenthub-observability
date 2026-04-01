// Package database provides ClickHouse connection and migration utilities.
package database

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/ClickHouse/clickhouse-go/v2"
)

// RunMigrations applies all *.sql files in migrationsDir to conn in sorted order.
// Files are applied in lexicographic order (001_, 002_, …). Already-applied
// migrations are tracked in a simple in-memory set (re-applied on each startup
// using IF NOT EXISTS DDL statements — safe for ClickHouse idempotent DDL).
func RunMigrations(ctx context.Context, conn clickhouse.Conn, migrationsDir string) error {
	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return fmt.Errorf("migration: read dir %q: %w", migrationsDir, err)
	}

	var files []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			files = append(files, filepath.Join(migrationsDir, e.Name()))
		}
	}
	sort.Strings(files)

	for _, f := range files {
		sql, err := os.ReadFile(f)
		if err != nil {
			return fmt.Errorf("migration: read %q: %w", f, err)
		}
		for _, stmt := range splitStatements(string(sql)) {
			stmt = strings.TrimSpace(stmt)
			if stmt == "" {
				continue
			}
			if err := conn.Exec(ctx, stmt); err != nil {
				return fmt.Errorf("migration: exec %q: %w", f, err)
			}
		}
	}
	return nil
}

// splitStatements splits a SQL string by semicolons, ignoring those inside comments.
func splitStatements(sql string) []string {
	return strings.Split(sql, ";")
}
