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
 * Tool execution trace - tool invocation level (skill runtime).
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tool_execution_traces")
public class ToolExecutionTrace {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column("tenant_id")
    private UUID tenantId;

    @Column("node_execution_trace_id")
    private UUID nodeExecutionTraceId;

    // Tool metadata
    @Column("skill_id")
    private UUID skillId;

    @Column("skill_slug")
    private String skillSlug;

    @Column("tool_id")
    private UUID toolId;

    @Column("tool_type")
    private String toolType; // HTTP, SQL, DOCUMENT_SEARCH, SCRIPT, MCP

    // Execution metadata
    private String status; // SUCCESS, FAILURE

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

    // Retry metadata
    @Column("attempt_number")
    private Integer attemptNumber;

    @Column("max_retries")
    private Integer maxRetries;

    // Audit
    @Column("created_at")
    private OffsetDateTime createdAt;
}
