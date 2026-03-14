package dev.cezar.agenthub.observability.event;

import java.util.UUID;

/**
 * Base DTO for execution events from orchestrator.
 * 
 * @param executionId Execution ID
 * @param tenantId Tenant ID
 */
public record ExecutionEventDto(
    UUID executionId,
    UUID tenantId
) {}
