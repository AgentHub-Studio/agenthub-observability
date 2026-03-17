package dev.cezar.agenthub.observability.api;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request to create an execution trace.
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
        String inputData,
        String triggerSource,
        String triggerMetadata
) {}
