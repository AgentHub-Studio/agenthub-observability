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
 * Aggregated metric - pre-aggregated metric for performance.
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aggregated_metrics")
public class AggregatedMetric {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column("tenant_id")
    private UUID tenantId;

    // Metric metadata
    @Column("metric_name")
    private String metricName;

    @Column("aggregation_type")
    private String aggregationType; // SUM, AVG, MIN, MAX, COUNT, P50, P95, P99

    @Column("aggregation_period")
    private String aggregationPeriod; // MINUTE, HOUR, DAY, WEEK, MONTH

    // Time window
    @Column("period_start")
    private OffsetDateTime periodStart;

    @Column("period_end")
    private OffsetDateTime periodEnd;

    // Dimensions (stored as JSON String — ClickHouse has no JSONB)
    private String dimensions;

    // Aggregated value
    private Double value;

    @Column("sample_count")
    private Long sampleCount;

    // Audit
    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
