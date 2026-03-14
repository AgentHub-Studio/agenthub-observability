package dev.cezar.agenthub.observability.service;

import dev.cezar.agenthub.observability.domain.AggregatedMetric;
import dev.cezar.agenthub.observability.domain.MetricEvent;
import dev.cezar.agenthub.observability.repository.AggregatedMetricRepository;
import dev.cezar.agenthub.observability.repository.MetricEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MetricService.
 */
@ExtendWith(MockitoExtension.class)
class MetricServiceTest {

    @Mock
    private MetricEventRepository metricEventRepository;

    @Mock
    private AggregatedMetricRepository aggregatedMetricRepository;

    private MetricService metricService;

    @BeforeEach
    void setUp() {
        metricService = new MetricService(metricEventRepository, aggregatedMetricRepository);
    }

    @Test
    void shouldCreateMetricEvent() {
        // Given
        MetricEvent event = createMetricEvent("request.count", "COUNTER", 1.0);
        when(metricEventRepository.save(any(MetricEvent.class)))
            .thenReturn(Mono.just(event));

        // When & Then
        StepVerifier.create(metricService.createMetricEvent(event))
            .assertNext(saved -> {
                assertThat(saved.getMetricName()).isEqualTo("request.count");
                assertThat(saved.getMetricType()).isEqualTo("COUNTER");
                assertThat(saved.getValue()).isEqualTo(1.0);
            })
            .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldCreateMetricEventsBatch() {
        // Given
        MetricEvent event1 = createMetricEvent("metric1", "COUNTER", 1.0);
        MetricEvent event2 = createMetricEvent("metric2", "GAUGE", 2.0);
        List<MetricEvent> events = List.of(event1, event2);

        when(metricEventRepository.saveAll(anyIterable()))
            .thenReturn(Flux.fromIterable(events));

        // When & Then
        StepVerifier.create(metricService.createMetricEventsBatch(events))
            .expectNextCount(2)
            .verifyComplete();

        verify(metricEventRepository).saveAll(anyIterable());
    }

    @Test
    void shouldListMetricEvents() {
        // Given
        Long tenantId = 1L;
        MetricEvent event1 = createMetricEvent("metric1", "COUNTER", 1.0);
        MetricEvent event2 = createMetricEvent("metric2", "GAUGE", 2.0);

        when(metricEventRepository.findByTenantId(tenantId))
            .thenReturn(Flux.just(event1, event2));

        // When & Then
        StepVerifier.create(metricService.listMetricEvents(tenantId))
            .expectNextCount(2)
            .verifyComplete();

        verify(metricEventRepository).findByTenantId(tenantId);
    }

    @Test
    void shouldListMetricEventsByName() {
        // Given
        Long tenantId = 1L;
        String metricName = "request.count";
        MetricEvent event1 = createMetricEvent(metricName, "COUNTER", 1.0);
        MetricEvent event2 = createMetricEvent(metricName, "COUNTER", 2.0);

        when(metricEventRepository.findByTenantIdAndMetricName(tenantId, metricName))
            .thenReturn(Flux.just(event1, event2));

        // When & Then
        StepVerifier.create(metricService.listMetricEventsByName(tenantId, metricName))
            .expectNextCount(2)
            .verifyComplete();

        verify(metricEventRepository).findByTenantIdAndMetricName(tenantId, metricName);
    }

    @Test
    void shouldGetAggregatedMetrics() {
        // Given
        Long tenantId = 1L;
        String metricName = "request.count";
        String period = "hour";
        
        AggregatedMetric metric1 = createAggregatedMetric(metricName, period, "SUM", 100.0);
        AggregatedMetric metric2 = createAggregatedMetric(metricName, period, "AVG", 50.0);

        when(aggregatedMetricRepository.findByTenantIdAndMetricNameAndPeriod(tenantId, metricName, period))
            .thenReturn(Flux.just(metric1, metric2));

        // When & Then
        StepVerifier.create(metricService.getAggregatedMetrics(tenantId, metricName, period))
            .expectNextCount(2)
            .verifyComplete();

        verify(aggregatedMetricRepository).findByTenantIdAndMetricNameAndPeriod(tenantId, metricName, period);
    }

    @Test
    void shouldRecordCounter() {
        // Given
        Long tenantId = 1L;
        String metricName = "api.requests";
        Map<String, String> dimensions = Map.of("endpoint", "/api/agents");

        when(metricEventRepository.save(any(MetricEvent.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(metricService.recordCounter(tenantId, metricName, 1.0, dimensions))
            .assertNext(event -> {
                assertThat(event.getMetricType()).isEqualTo("COUNTER");
                assertThat(event.getMetricName()).isEqualTo(metricName);
                assertThat(event.getValue()).isEqualTo(1.0);
                assertThat(event.getDimensions()).containsEntry("endpoint", "/api/agents");
            })
            .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldRecordGauge() {
        // Given
        Long tenantId = 1L;
        String metricName = "memory.usage";
        Double value = 1024.0;

        when(metricEventRepository.save(any(MetricEvent.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(metricService.recordGauge(tenantId, metricName, value))
            .assertNext(event -> {
                assertThat(event.getMetricType()).isEqualTo("GAUGE");
                assertThat(event.getMetricName()).isEqualTo(metricName);
                assertThat(event.getValue()).isEqualTo(1024.0);
            })
            .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldRecordTimer() {
        // Given
        Long tenantId = 1L;
        String metricName = "api.response.time";
        Long durationMs = 150L;

        when(metricEventRepository.save(any(MetricEvent.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(metricService.recordTimer(tenantId, metricName, durationMs))
            .assertNext(event -> {
                assertThat(event.getMetricType()).isEqualTo("TIMER");
                assertThat(event.getMetricName()).isEqualTo(metricName);
                assertThat(event.getValue()).isEqualTo(150.0);
            })
            .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldRecordHistogram() {
        // Given
        Long tenantId = 1L;
        String metricName = "request.size";
        Double value = 2048.0;

        when(metricEventRepository.save(any(MetricEvent.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(metricService.recordHistogram(tenantId, metricName, value))
            .assertNext(event -> {
                assertThat(event.getMetricType()).isEqualTo("HISTOGRAM");
                assertThat(event.getMetricName()).isEqualTo(metricName);
                assertThat(event.getValue()).isEqualTo(2048.0);
            })
            .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    private MetricEvent createMetricEvent(String metricName, String metricType, Double value) {
        MetricEvent event = new MetricEvent();
        event.setId(1L);
        event.setTenantId(1L);
        event.setMetricName(metricName);
        event.setMetricType(metricType);
        event.setValue(value);
        event.setTimestamp(Instant.now());
        event.setDimensions(Map.of("test", "true"));
        return event;
    }

    private AggregatedMetric createAggregatedMetric(String metricName, String period, String aggregation, Double value) {
        AggregatedMetric metric = new AggregatedMetric();
        metric.setId(1L);
        metric.setTenantId(1L);
        metric.setMetricName(metricName);
        metric.setMetricType("COUNTER");
        metric.setPeriod(period);
        metric.setAggregation(aggregation);
        metric.setValue(value);
        metric.setPeriodStart(Instant.now());
        metric.setPeriodEnd(Instant.now().plusSeconds(3600));
        return metric;
    }
}
