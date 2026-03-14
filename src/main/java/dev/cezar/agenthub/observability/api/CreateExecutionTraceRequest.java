package dev.cezar.agenthub.observability.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request para criar execution trace.
 *
 * @since 1.0.0
 */
public record CreateExecutionTraceRequest(
        @NotNull UUID tenantId,
        @NotNull UUID agentId,
        UUID agentVersionId,
        UUID userId,
        @NotNull UUID executionId,
        @NotNull String status,
        @NotNull OffsetDateTime startedAt,
        JsonNode inputData,
        String triggerSource,
        JsonNode triggerMetadata
) {}
