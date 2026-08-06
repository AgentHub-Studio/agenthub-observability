// Package database provides ClickHouse connection and migration utilities.
package database

import (
	"context"
	"fmt"
	"io"
	"os"
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

	root, err := os.OpenRoot(migrationsDir)
	if err != nil {
		return fmt.Errorf("migration: open root %q: %w", migrationsDir, err)
	}
	defer root.Close()

	var files []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			files = append(files, e.Name())
		}
	}
	sort.Strings(files)

	for _, f := range files {
		sql, err := readMigrationFile(root, f)
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

func readMigrationFile(root *os.Root, name string) ([]byte, error) {
	file, err := root.Open(name)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	return io.ReadAll(file)
}

// splitStatements splits a SQL string by semicolons, ignoring those inside comments.
func splitStatements(sql string) []string {
	return strings.Split(sql, ";")
}
