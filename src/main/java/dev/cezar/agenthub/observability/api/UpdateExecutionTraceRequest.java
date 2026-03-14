package dev.cezar.agenthub.observability.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * Request para atualizar execution trace.
 *
 * @since 1.0.0
 */
public record UpdateExecutionTraceRequest(
        String status,
        OffsetDateTime completedAt,
        Long durationMs,
        JsonNode outputData,
        String errorMessage,
        String errorStackTrace
) {}
