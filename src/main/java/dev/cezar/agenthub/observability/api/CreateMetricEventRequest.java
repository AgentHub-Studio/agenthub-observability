package dev.cezar.agenthub.observability.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request para criar evento de métrica.
 *
 * @since 1.0.0
 */
public record CreateMetricEventRequest(
        @NotNull UUID tenantId,
        @NotNull String metricName,
        @NotNull String metricType, // COUNTER, GAUGE, HISTOGRAM, TIMER
        @NotNull Double metricValue,
        String metricUnit,
        JsonNode dimensions,
        OffsetDateTime timestamp
) {}
