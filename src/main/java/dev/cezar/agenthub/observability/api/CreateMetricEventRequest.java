package dev.cezar.agenthub.observability.api;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request to create a metric event.
 *
 * @since 1.0.0
 */
public record CreateMetricEventRequest(
        @NotNull UUID tenantId,
        @NotNull String metricName,
        @NotNull String metricType, // COUNTER, GAUGE, HISTOGRAM, TIMER
        @NotNull Double metricValue,
        String metricUnit,
        String dimensions,
        OffsetDateTime timestamp
) {}
