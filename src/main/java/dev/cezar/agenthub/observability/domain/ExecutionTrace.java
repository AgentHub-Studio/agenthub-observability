package dev.cezar.agenthub.observability.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Execution trace - nível de execução do agente (orchestrator).
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("execution_traces")
public class ExecutionTrace {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agent_id")
    private UUID agentId;

    @Column("agent_version_id")
    private UUID agentVersionId;

    @Column("user_id")
    private UUID userId;

    @Column("execution_id")
    private UUID executionId;

    // Execution metadata
    private String status; // RUNNING, COMPLETED, FAILED, CANCELLED

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;

    @Column("duration_ms")
    private Long durationMs;

    // Input/Output (stored as JSONB)
    @Column("input_data")
    private JsonNode inputData;

    @Column("output_data")
    private JsonNode outputData;

    @Column("error_message")
    private String errorMessage;

    @Column("error_stack_trace")
    private String errorStackTrace;

    // Context
    @Column("trigger_source")
    private String triggerSource; // API, WEBHOOK, SCHEDULE, MANUAL

    @Column("trigger_metadata")
    private JsonNode triggerMetadata;

    // Audit
    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
