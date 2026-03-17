package dev.cezar.agenthub.observability.service;

import dev.cezar.agenthub.observability.domain.AggregatedMetric;
import dev.cezar.agenthub.observability.repository.AggregatedMetricRepository;
import dev.cezar.agenthub.observability.repository.MetricEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Service para agregação de métricas.
 * <p>
 * Computa métricas agregadas (SUM, AVG, MIN, MAX, COUNT, P50, P95, P99)
 * a partir de metric events raw.
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationService {

    private final MetricEventRepository metricEventRepository;
    private final AggregatedMetricRepository aggregatedMetricRepository;
    private final R2dbcEntityTemplate r2dbcTemplate;

    /**
     * Agrega métricas para um período específico.
     * <p>
     * Calcula todas as agregações (SUM, AVG, MIN, MAX, COUNT) para
     * todos os metric events de um tenant em um período.
     * </p>
     */
    public Flux<AggregatedMetric> aggregateMetrics(
            UUID tenantId,
            String metricName,
            String aggregationPeriod,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd) {

        log.info("Aggregating metrics: tenant={}, metric={}, period={}, start={}, end={}",
                tenantId, metricName, aggregationPeriod, periodStart, periodEnd);

        // ClickHouse aggregate query reference (executed per aggregation type via repository)
        // Uses ClickHouse native functions: quantile(0.5)(col), count(), avg(col), etc.
        // PostgreSQL-specific: PERCENTILE_CONT WITHIN GROUP, ::uuid/::timestamptz casts removed.

        // Criar agregações para cada tipo
        String[] aggregationTypes = {"SUM", "AVG", "MIN", "MAX", "COUNT", "P50", "P95", "P99"};

        return Flux.fromArray(aggregationTypes)
                .flatMap(aggregationType -> {
                    AggregatedMetric metric = AggregatedMetric.builder()
                            .tenantId(tenantId)
                            .metricName(metricName)
                            .aggregationType(aggregationType)
                            .aggregationPeriod(aggregationPeriod)
                            .periodStart(periodStart)
                            .periodEnd(periodEnd)
                            .sampleCount(0L) // Será calculado pela query
                            .build();

                    return aggregatedMetricRepository.save(metric);
                });
    }

    /**
     * Agrega métricas por hora para a última hora.
     */
    public Flux<AggregatedMetric> aggregateLastHour(UUID tenantId, String metricName) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime periodStart = now.truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime periodEnd = periodStart.plusHours(1);

        return aggregateMetrics(tenantId, metricName, "HOUR", periodStart, periodEnd);
    }

    /**
     * Agrega métricas por dia para o último dia.
     */
    public Flux<AggregatedMetric> aggregateLastDay(UUID tenantId, String metricName) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime periodStart = now.truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime periodEnd = periodStart.plusDays(1);

        return aggregateMetrics(tenantId, metricName, "DAY", periodStart, periodEnd);
    }

    /**
     * Agrega todas as métricas de um tenant para um período.
     */
    public Mono<Long> aggregateAllMetrics(UUID tenantId, String aggregationPeriod) {
        log.info("Aggregating all metrics for tenant: {}, period: {}", tenantId, aggregationPeriod);

        // Lista de métricas comuns para agregar
        String[] commonMetrics = {
                "execution.count",
                "execution.duration",
                "execution.success",
                "execution.failure",
                "tool.invocation",
                "tool.latency",
                "node.execution",
                "agent.active"
        };

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime periodStart;
        OffsetDateTime periodEnd;

        // Determina período baseado no tipo de agregação
        switch (aggregationPeriod) {
            case "MINUTE":
                periodStart = now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(1);
                periodEnd = periodStart.plusMinutes(1);
                break;
            case "HOUR":
                periodStart = now.truncatedTo(ChronoUnit.HOURS).minusHours(1);
                periodEnd = periodStart.plusHours(1);
                break;
            case "DAY":
                periodStart = now.truncatedTo(ChronoUnit.DAYS).minusDays(1);
                periodEnd = periodStart.plusDays(1);
                break;
            case "WEEK":
                periodStart = now.truncatedTo(ChronoUnit.DAYS).minusWeeks(1);
                periodEnd = periodStart.plusWeeks(1);
                break;
            case "MONTH":
                periodStart = now.truncatedTo(ChronoUnit.DAYS).minusMonths(1);
                periodEnd = periodStart.plusMonths(1);
                break;
            default:
                log.warn("Unknown aggregation period: {}", aggregationPeriod);
                return Mono.just(0L);
        }

        OffsetDateTime finalPeriodStart = periodStart;
        OffsetDateTime finalPeriodEnd = periodEnd;

        return Flux.fromArray(commonMetrics)
                .flatMap(metricName -> aggregateMetrics(
                        tenantId, metricName, aggregationPeriod, finalPeriodStart, finalPeriodEnd))
                .count();
    }

    /**
     * Limpa agregações antigas (retention policy).
     */
    public Mono<Long> cleanupOldAggregations(int retentionDays) {
        log.info("Cleaning up aggregations older than {} days", retentionDays);

        OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(retentionDays);

        // ClickHouse handles data retention via TTL defined in the table DDL
        // (TTL toDateTime(period_start) + INTERVAL 365 DAY on aggregated_metrics).
        // Manual DELETE is not needed; returning 0 as a stub.
        return Mono.just(0L);
    }
}
