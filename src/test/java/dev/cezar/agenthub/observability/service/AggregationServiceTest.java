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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AggregationService.
 */
@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock
    private MetricEventRepository metricEventRepository;

    @Mock
    private AggregatedMetricRepository aggregatedMetricRepository;

    private AggregationService aggregationService;

    @BeforeEach
    void setUp() {
        aggregationService = new AggregationService(metricEventRepository, aggregatedMetricRepository);
    }

    @Test
    void shouldComputeSumAggregation() {
        // Given
        Long tenantId = 1L;
        String metricName = "request.count";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        MetricEvent event1 = createMetricEvent(tenantId, metricName, "COUNTER", 10.0, start.plusSeconds(600));
        MetricEvent event2 = createMetricEvent(tenantId, metricName, "COUNTER", 20.0, start.plusSeconds(1200));
        MetricEvent event3 = createMetricEvent(tenantId, metricName, "COUNTER", 30.0, start.plusSeconds(1800));

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.just(event1, event2, event3));
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(aggregationService.computeAggregation(tenantId, metricName, "hour", "SUM", start, end))
            .assertNext(metric -> {
                assertThat(metric.getMetricName()).isEqualTo(metricName);
                assertThat(metric.getPeriod()).isEqualTo("hour");
                assertThat(metric.getAggregation()).isEqualTo("SUM");
                assertThat(metric.getValue()).isEqualTo(60.0); // 10 + 20 + 30
                assertThat(metric.getSampleCount()).isEqualTo(3L);
            })
            .verifyComplete();

        verify(metricEventRepository).findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end);
        verify(aggregatedMetricRepository).save(any(AggregatedMetric.class));
    }

    @Test
    void shouldComputeAverageAggregation() {
        // Given
        Long tenantId = 1L;
        String metricName = "response.time";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        MetricEvent event1 = createMetricEvent(tenantId, metricName, "TIMER", 100.0, start.plusSeconds(600));
        MetricEvent event2 = createMetricEvent(tenantId, metricName, "TIMER", 200.0, start.plusSeconds(1200));
        MetricEvent event3 = createMetricEvent(tenantId, metricName, "TIMER", 300.0, start.plusSeconds(1800));

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.just(event1, event2, event3));
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(aggregationService.computeAggregation(tenantId, metricName, "hour", "AVG", start, end))
            .assertNext(metric -> {
                assertThat(metric.getValue()).isEqualTo(200.0); // (100 + 200 + 300) / 3
                assertThat(metric.getAggregation()).isEqualTo("AVG");
            })
            .verifyComplete();
    }

    @Test
    void shouldComputeMinAggregation() {
        // Given
        Long tenantId = 1L;
        String metricName = "cpu.usage";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        MetricEvent event1 = createMetricEvent(tenantId, metricName, "GAUGE", 30.0, start.plusSeconds(600));
        MetricEvent event2 = createMetricEvent(tenantId, metricName, "GAUGE", 10.0, start.plusSeconds(1200));
        MetricEvent event3 = createMetricEvent(tenantId, metricName, "GAUGE", 20.0, start.plusSeconds(1800));

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.just(event1, event2, event3));
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(aggregationService.computeAggregation(tenantId, metricName, "hour", "MIN", start, end))
            .assertNext(metric -> assertThat(metric.getValue()).isEqualTo(10.0))
            .verifyComplete();
    }

    @Test
    void shouldComputeMaxAggregation() {
        // Given
        Long tenantId = 1L;
        String metricName = "memory.usage";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        MetricEvent event1 = createMetricEvent(tenantId, metricName, "GAUGE", 30.0, start.plusSeconds(600));
        MetricEvent event2 = createMetricEvent(tenantId, metricName, "GAUGE", 50.0, start.plusSeconds(1200));
        MetricEvent event3 = createMetricEvent(tenantId, metricName, "GAUGE", 20.0, start.plusSeconds(1800));

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.just(event1, event2, event3));
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(aggregationService.computeAggregation(tenantId, metricName, "hour", "MAX", start, end))
            .assertNext(metric -> assertThat(metric.getValue()).isEqualTo(50.0))
            .verifyComplete();
    }

    @Test
    void shouldComputeCountAggregation() {
        // Given
        Long tenantId = 1L;
        String metricName = "api.calls";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        MetricEvent event1 = createMetricEvent(tenantId, metricName, "COUNTER", 1.0, start.plusSeconds(600));
        MetricEvent event2 = createMetricEvent(tenantId, metricName, "COUNTER", 1.0, start.plusSeconds(1200));
        MetricEvent event3 = createMetricEvent(tenantId, metricName, "COUNTER", 1.0, start.plusSeconds(1800));

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.just(event1, event2, event3));
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(aggregationService.computeAggregation(tenantId, metricName, "hour", "COUNT", start, end))
            .assertNext(metric -> assertThat(metric.getValue()).isEqualTo(3.0))
            .verifyComplete();
    }

    @Test
    void shouldComputeP50Percentile() {
        // Given
        Long tenantId = 1L;
        String metricName = "response.time";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        // Values: 100, 200, 300, 400, 500 -> P50 = 300
        List<MetricEvent> events = List.of(
            createMetricEvent(tenantId, metricName, "TIMER", 100.0, start.plusSeconds(600)),
            createMetricEvent(tenantId, metricName, "TIMER", 200.0, start.plusSeconds(1200)),
            createMetricEvent(tenantId, metricName, "TIMER", 300.0, start.plusSeconds(1800)),
            createMetricEvent(tenantId, metricName, "TIMER", 400.0, start.plusSeconds(2400)),
            createMetricEvent(tenantId, metricName, "TIMER", 500.0, start.plusSeconds(3000))
        );

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.fromIterable(events));
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(aggregationService.computeAggregation(tenantId, metricName, "hour", "P50", start, end))
            .assertNext(metric -> assertThat(metric.getValue()).isEqualTo(300.0))
            .verifyComplete();
    }

    @Test
    void shouldComputeP95Percentile() {
        // Given
        Long tenantId = 1L;
        String metricName = "response.time";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        // 20 values: 10, 20, ..., 200 -> P95 at index 19 (95% of 20 = 19) = 190
        List<MetricEvent> events = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            events.add(createMetricEvent(tenantId, metricName, "TIMER", i * 10.0, start.plusSeconds(i * 100)));
        }

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.fromIterable(events));
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(aggregationService.computeAggregation(tenantId, metricName, "hour", "P95", start, end))
            .assertNext(metric -> assertThat(metric.getValue()).isEqualTo(190.0))
            .verifyComplete();
    }

    @Test
    void shouldReturnZeroWhenNoEvents() {
        // Given
        Long tenantId = 1L;
        String metricName = "empty.metric";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.empty());
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then
        StepVerifier.create(aggregationService.computeAggregation(tenantId, metricName, "hour", "SUM", start, end))
            .assertNext(metric -> {
                assertThat(metric.getValue()).isEqualTo(0.0);
                assertThat(metric.getSampleCount()).isEqualTo(0L);
            })
            .verifyComplete();
    }

    @Test
    void shouldComputeAllAggregationsForMetric() {
        // Given
        Long tenantId = 1L;
        String metricName = "test.metric";
        String period = "hour";
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        MetricEvent event = createMetricEvent(tenantId, metricName, "COUNTER", 10.0, start.plusSeconds(600));

        when(metricEventRepository.findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end))
            .thenReturn(Flux.just(event));
        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // When & Then - should compute all 8 aggregations
        String[] aggregations = {"SUM", "AVG", "MIN", "MAX", "COUNT", "P50", "P95", "P99"};
        
        StepVerifier.create(aggregationService.computeAllAggregations(tenantId, metricName, period, start, end))
            .expectNextCount(8) // All 8 aggregations
            .verifyComplete();

        verify(metricEventRepository, times(8))
            .findByTenantIdAndMetricNameAndTimestampBetween(tenantId, metricName, start, end);
        verify(aggregatedMetricRepository, times(8)).save(any(AggregatedMetric.class));
    }

    private MetricEvent createMetricEvent(Long tenantId, String metricName, String metricType, Double value, Instant timestamp) {
        MetricEvent event = new MetricEvent();
        event.setId(1L);
        event.setTenantId(tenantId);
        event.setMetricName(metricName);
        event.setMetricType(metricType);
        event.setValue(value);
        event.setTimestamp(timestamp);
        event.setDimensions(Map.of("test", "true"));
        return event;
    }
}
