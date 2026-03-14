package dev.cezar.agenthub.observability.service;

import dev.cezar.agenthub.observability.domain.AggregatedMetric;
import dev.cezar.agenthub.observability.domain.MetricEvent;
import dev.cezar.agenthub.observability.repository.AggregatedMetricRepository;
import dev.cezar.agenthub.observability.repository.MetricEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service para gerenciar métricas e eventos.
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricService {

    private final MetricEventRepository metricEventRepository;
    private final AggregatedMetricRepository aggregatedMetricRepository;

    // =============================================================================
    // METRIC EVENTS
    // =============================================================================

    /**
     * Cria um evento de métrica.
     */
    public Mono<MetricEvent> createMetricEvent(MetricEvent event) {
        log.debug("Creating metric event: name={}, type={}, value={}",
                event.getMetricName(), event.getMetricType(), event.getMetricValue());

        if (event.getTimestamp() == null) {
            event.setTimestamp(OffsetDateTime.now());
        }
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(OffsetDateTime.now());
        }

        return metricEventRepository.save(event);
    }

    /**
     * Cria múltiplos eventos de métrica em batch.
     */
    public Flux<MetricEvent> createMetricEventsBatch(Flux<MetricEvent> events) {
        return events
                .doOnNext(event -> {
                    if (event.getTimestamp() == null) {
                        event.setTimestamp(OffsetDateTime.now());
                    }
                    if (event.getCreatedAt() == null) {
                        event.setCreatedAt(OffsetDateTime.now());
                    }
                })
                .flatMap(metricEventRepository::save);
    }

    /**
     * Lista eventos de métrica por nome e período.
     */
    public Flux<MetricEvent> listMetricEvents(
            UUID tenantId,
            String metricName,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            int limit) {

        return metricEventRepository.findByMetricNameAndPeriod(
                tenantId, metricName, startDate, endDate, limit);
    }

    // =============================================================================
    // AGGREGATED METRICS
    // =============================================================================

    /**
     * Busca métricas agregadas.
     */
    public Flux<AggregatedMetric> getAggregatedMetrics(
            UUID tenantId,
            String metricName,
            String aggregationType,
            String aggregationPeriod,
            OffsetDateTime startDate,
            OffsetDateTime endDate) {

        return aggregatedMetricRepository.findAggregatedMetrics(
                tenantId, metricName, aggregationType, aggregationPeriod, startDate, endDate);
    }

    /**
     * Cria ou atualiza métrica agregada.
     */
    public Mono<AggregatedMetric> saveAggregatedMetric(AggregatedMetric metric) {
        log.debug("Saving aggregated metric: name={}, type={}, period={}, value={}",
                metric.getMetricName(), metric.getAggregationType(),
                metric.getAggregationPeriod(), metric.getValue());

        if (metric.getCreatedAt() == null) {
            metric.setCreatedAt(OffsetDateTime.now());
        }
        metric.setUpdatedAt(OffsetDateTime.now());

        return aggregatedMetricRepository.save(metric);
    }

    // =============================================================================
    // HELPER METHODS
    // =============================================================================

    /**
     * Cria evento de contador (incremento).
     */
    public Mono<MetricEvent> recordCounter(
            UUID tenantId,
            String metricName,
            double value,
            Object dimensions) {

        MetricEvent event = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName(metricName)
                .metricType("COUNTER")
                .metricValue(value)
                .metricUnit("count")
                .timestamp(OffsetDateTime.now())
                .build();

        return createMetricEvent(event);
    }

    /**
     * Cria evento de gauge (valor absoluto).
     */
    public Mono<MetricEvent> recordGauge(
            UUID tenantId,
            String metricName,
            double value,
            String unit) {

        MetricEvent event = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName(metricName)
                .metricType("GAUGE")
                .metricValue(value)
                .metricUnit(unit)
                .timestamp(OffsetDateTime.now())
                .build();

        return createMetricEvent(event);
    }

    /**
     * Cria evento de timer (duração).
     */
    public Mono<MetricEvent> recordTimer(
            UUID tenantId,
            String metricName,
            long durationMs) {

        MetricEvent event = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName(metricName)
                .metricType("TIMER")
                .metricValue((double) durationMs)
                .metricUnit("ms")
                .timestamp(OffsetDateTime.now())
                .build();

        return createMetricEvent(event);
    }

    /**
     * Cria evento de histogram (distribuição de valores).
     */
    public Mono<MetricEvent> recordHistogram(
            UUID tenantId,
            String metricName,
            double value,
            String unit) {

        MetricEvent event = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName(metricName)
                .metricType("HISTOGRAM")
                .metricValue(value)
                .metricUnit(unit)
                .timestamp(OffsetDateTime.now())
                .build();

        return createMetricEvent(event);
    }
}
