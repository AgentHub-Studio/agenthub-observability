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
 * Node execution trace - nível de execução de nó do pipeline.
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
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("execution_trace_id")
    private UUID executionTraceId;

    // Node metadata
    @Column("node_id")
    private String nodeId;

    @Column("node_type")
    private String nodeType; // TOOL, LLM, DECISION, LOOP, PARALLEL

    @Column("node_name")
    private String nodeName;

    // Execution metadata
    private String status; // RUNNING, COMPLETED, FAILED, SKIPPED

    @Column("started_at")
    private OffsetDateTime startedAt;

    @Column("completed_at")
    private OffsetDateTime completedAt;

    @Column("duration_ms")
    private Long durationMs;

    // Input/Output
    @Column("input_data")
    private JsonNode inputData;

    @Column("output_data")
    private JsonNode outputData;

    @Column("error_message")
    private String errorMessage;

    // Context mutation
    @Column("context_before")
    private JsonNode contextBefore;

    @Column("context_after")
    private JsonNode contextAfter;

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
