package dev.cezar.agenthub.observability.api;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request to create a tool execution trace.
 *
 * @since 1.0.0
 */
public record CreateToolExecutionTraceRequest(
        @NotNull UUID tenantId,
        UUID nodeExecutionTraceId,
        UUID skillId,
        String skillSlug,
        UUID toolId,
        @NotNull String toolType,
        @NotNull String status,
        @NotNull OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Long durationMs,
        String inputData,
        String outputData,
        String errorMessage,
        Integer attemptNumber,
        Integer maxRetries
) {}
