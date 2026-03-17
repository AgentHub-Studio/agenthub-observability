package dev.cezar.agenthub.observability.api;

import java.time.OffsetDateTime;

/**
 * Request to update an execution trace.
 *
 * @since 1.0.0
 */
public record UpdateExecutionTraceRequest(
        String status,
        OffsetDateTime completedAt,
        Long durationMs,
        String outputData,
        String errorMessage,
        String errorStackTrace
) {}
