package dev.cezar.agenthub.observability.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request para criar tool execution trace.
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
        JsonNode inputData,
        JsonNode outputData,
        String errorMessage,
        Integer attemptNumber,
        Integer maxRetries
) {}
