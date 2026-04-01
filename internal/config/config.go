package config

import (
	"fmt"
	"os"
	"strings"
)

// Config holds the service configuration loaded from environment variables.
type Config struct {
	Port            string
	ClickHouseURL   string
	RabbitMQURL     string
	KeycloakBaseURL string
	LogLevel        string
	CORSOrigins     []string
}

// Load reads configuration from environment variables.
func Load() (*Config, error) {
	cfg := &Config{
		Port:            getEnv("PORT", "8086"),
		ClickHouseURL:   os.Getenv("CLICKHOUSE_URL"),
		RabbitMQURL:     getEnv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"),
		KeycloakBaseURL: os.Getenv("KEYCLOAK_BASE_URL"),
		LogLevel:        getEnv("LOG_LEVEL", "info"),
		CORSOrigins:     strings.Split(getEnv("CORS_ORIGINS", "*"), ","),
	}
	if cfg.ClickHouseURL == "" {
		return nil, fmt.Errorf("config: CLICKHOUSE_URL is required")
	}
	return cfg, nil
}

func getEnv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
