package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.AggregatedMetric;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Repository for AggregatedMetric.
 *
 * @since 1.0.0
 */
@Repository
public interface AggregatedMetricRepository extends ReactiveCrudRepository<AggregatedMetric, UUID> {

    /**
     * Finds aggregated metrics by name, type, period and time range.
     */
    @Query("SELECT * FROM aggregated_metrics WHERE tenant_id = :tenantId AND metric_name = :metricName AND aggregation_type = :aggregationType AND aggregation_period = :aggregationPeriod AND period_start >= :startDate AND period_start < :endDate ORDER BY period_start DESC")
    Flux<AggregatedMetric> findAggregatedMetrics(
            UUID tenantId,
            String metricName,
            String aggregationType,
            String aggregationPeriod,
            OffsetDateTime startDate,
            OffsetDateTime endDate
    );
}
