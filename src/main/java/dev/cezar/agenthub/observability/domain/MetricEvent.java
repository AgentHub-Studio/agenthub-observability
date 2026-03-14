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
 * Metric event - evento de métrica time-series.
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("metric_events")
public class MetricEvent {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    // Metric metadata
    @Column("metric_name")
    private String metricName; // execution.count, execution.duration, tool.invocation, etc.

    @Column("metric_type")
    private String metricType; // COUNTER, GAUGE, HISTOGRAM, TIMER

    @Column("metric_value")
    private Double metricValue;

    @Column("metric_unit")
    private String metricUnit; // ms, count, bytes, etc.

    // Dimensions (for grouping/filtering)
    private JsonNode dimensions; // {agentId, skillSlug, toolType, status, etc.}

    // Timestamp
    private OffsetDateTime timestamp;

    // Audit
    @Column("created_at")
    private OffsetDateTime createdAt;
}
