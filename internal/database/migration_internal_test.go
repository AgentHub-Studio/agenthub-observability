package database

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestReadMigrationFileUsesScopedRoot(t *testing.T) {
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "001_init.sql"), []byte("SELECT 1;"), 0o600))

	root, err := os.OpenRoot(dir)
	require.NoError(t, err)
	defer root.Close()

	got, err := readMigrationFile(root, "001_init.sql")
	require.NoError(t, err)
	assert.Equal(t, "SELECT 1;", string(got))
}

func TestReadMigrationFileRejectsSymlinkEscape(t *testing.T) {
	dir := t.TempDir()
	outside := filepath.Join(t.TempDir(), "outside.sql")
	require.NoError(t, os.WriteFile(outside, []byte("DROP TABLE secret;"), 0o600))
	require.NoError(t, os.Symlink(outside, filepath.Join(dir, "002_escape.sql")))

	root, err := os.OpenRoot(dir)
	require.NoError(t, err)
	defer root.Close()

	_, err = readMigrationFile(root, "002_escape.sql")
	require.Error(t, err)
}
