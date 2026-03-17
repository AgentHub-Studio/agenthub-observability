package dev.cezar.agenthub.observability.domain;

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
 * Node execution trace - pipeline node execution level.
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("node_execution_traces")
public class NodeExecutionTrace {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column("tenant_id")
    private UUID tenantId;

    @Column("execution_trace_id")
    private UUID executionTraceId;

    @Column("execution_id")
    private UUID executionId;

    // Node metadata
    @Column("node_id")
    private String nodeId;

    @Column("node_type")
    private String nodeType; // TOOL, LLM, DECISION, LOOP, PARALLEL

    @Column("node_name")
    private String nodeName;

    // Execution metadata
    private NodeStatus status;

    public enum NodeStatus {
        RUNNING, COMPLETED, FAILED, SKIPPED
    }

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;

    @Column("duration_ms")
    private Long durationMs;

    // Input/Output (stored as JSON String — ClickHouse has no JSONB)
    @Column("input_data")
    private String inputData;

    @Column("output_data")
    private String outputData;

    @Column("error_message")
    private String errorMessage;

    // Context mutation (stored as JSON String — ClickHouse has no JSONB)
    @Column("context_before")
    private String contextBefore;

    @Column("context_after")
    private String contextAfter;

    // Retry metadata
    @Column("attempt_number")
    private Integer attemptNumber;

    @Column("max_retries")
    private Integer maxRetries;

    // Audit
    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
