package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.MetricEvent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Repository for MetricEvent.
 *
 * @since 1.0.0
 */
@Repository
public interface MetricEventRepository extends ReactiveCrudRepository<MetricEvent, UUID> {

    /**
     * Finds metric events by metric name within a time period.
     */
    @Query("SELECT * FROM metric_events WHERE tenant_id = :tenantId AND metric_name = :metricName AND timestamp >= :startDate AND timestamp < :endDate ORDER BY timestamp DESC LIMIT :limit")
    Flux<MetricEvent> findByMetricNameAndPeriod(
            UUID tenantId,
            String metricName,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            int limit
    );
}
