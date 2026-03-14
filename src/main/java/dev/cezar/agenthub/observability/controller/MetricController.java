package dev.cezar.agenthub.observability.controller;

import dev.cezar.agenthub.observability.api.CreateMetricEventRequest;
import dev.cezar.agenthub.observability.domain.AggregatedMetric;
import dev.cezar.agenthub.observability.domain.MetricEvent;
import dev.cezar.agenthub.observability.service.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Controller REST para métricas.
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Metric event collection and aggregation")
public class MetricController {

    private final MetricService metricService;

    // =============================================================================
    // METRIC EVENTS
    // =============================================================================

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create metric event", description = "Records a new metric event")
    public Mono<MetricEvent> createMetricEvent(@Valid @RequestBody CreateMetricEventRequest request) {
        MetricEvent event = MetricEvent.builder()
                .tenantId(request.tenantId())
                .metricName(request.metricName())
                .metricType(request.metricType())
                .metricValue(request.metricValue())
                .metricUnit(request.metricUnit())
                .dimensions(request.dimensions())
                .timestamp(request.timestamp() != null ? request.timestamp() : OffsetDateTime.now())
                .build();

        return metricService.createMetricEvent(event);
    }

    @PostMapping("/events/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create metric events in batch", description = "Records multiple metric events")
    public Flux<MetricEvent> createMetricEventsBatch(@Valid @RequestBody Flux<CreateMetricEventRequest> requests) {
        Flux<MetricEvent> events = requests.map(request -> MetricEvent.builder()
                .tenantId(request.tenantId())
                .metricName(request.metricName())
                .metricType(request.metricType())
                .metricValue(request.metricValue())
                .metricUnit(request.metricUnit())
                .dimensions(request.dimensions())
                .timestamp(request.timestamp() != null ? request.timestamp() : OffsetDateTime.now())
                .build());

        return metricService.createMetricEventsBatch(events);
    }

    @GetMapping("/events")
    @Operation(summary = "List metric events", description = "Lists metric events by name and period")
    public Flux<MetricEvent> listMetricEvents(
            @RequestParam UUID tenantId,
            @RequestParam String metricName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "1000") int limit) {

        return metricService.listMetricEvents(tenantId, metricName, startDate, endDate, limit);
    }

    // =============================================================================
    // AGGREGATED METRICS
    // =============================================================================

    @GetMapping("/aggregated")
    @Operation(summary = "Get aggregated metrics", description = "Retrieves pre-aggregated metrics")
    public Flux<AggregatedMetric> getAggregatedMetrics(
            @RequestParam UUID tenantId,
            @RequestParam String metricName,
            @RequestParam String aggregationType, // SUM, AVG, MIN, MAX, COUNT, P50, P95, P99
            @RequestParam String aggregationPeriod, // MINUTE, HOUR, DAY, WEEK, MONTH
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return metricService.getAggregatedMetrics(
                tenantId, metricName, aggregationType, aggregationPeriod, startDate, endDate);
    }

    // =============================================================================
    // CONVENIENCE ENDPOINTS
    // =============================================================================

    @PostMapping("/counter")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record counter", description = "Records a counter metric (increment)")
    public Mono<MetricEvent> recordCounter(
            @RequestParam UUID tenantId,
            @RequestParam String metricName,
            @RequestParam(defaultValue = "1.0") double value) {

        return metricService.recordCounter(tenantId, metricName, value, null);
    }

    @PostMapping("/gauge")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record gauge", description = "Records a gauge metric (absolute value)")
    public Mono<MetricEvent> recordGauge(
            @RequestParam UUID tenantId,
            @RequestParam String metricName,
            @RequestParam double value,
            @RequestParam(defaultValue = "count") String unit) {

        return metricService.recordGauge(tenantId, metricName, value, unit);
    }

    @PostMapping("/timer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record timer", description = "Records a timer metric (duration in ms)")
    public Mono<MetricEvent> recordTimer(
            @RequestParam UUID tenantId,
            @RequestParam String metricName,
            @RequestParam long durationMs) {

        return metricService.recordTimer(tenantId, metricName, durationMs);
    }

    @PostMapping("/histogram")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record histogram", description = "Records a histogram metric (value distribution)")
    public Mono<MetricEvent> recordHistogram(
            @RequestParam UUID tenantId,
            @RequestParam String metricName,
            @RequestParam double value,
            @RequestParam String unit) {

        return metricService.recordHistogram(tenantId, metricName, value, unit);
    }
}
