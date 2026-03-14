package dev.cezar.agenthub.observability.event;

import java.util.UUID;

/**
 * Base DTO for node events from orchestrator.
 * 
 * @param executionId Execution ID
 * @param nodeId Node ID
 */
public record NodeEventDto(
    UUID executionId,
    String nodeId
) {}
