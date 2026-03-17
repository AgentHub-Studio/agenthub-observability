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

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        UUID tenantId = UUID.randomUUID();
        MetricEvent event = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName("request.count")
                .metricType("COUNTER")
                .metricValue(1.0)
                .timestamp(OffsetDateTime.now())
                .build();

        when(metricEventRepository.save(any(MetricEvent.class))).thenReturn(Mono.just(event));

        StepVerifier.create(metricService.createMetricEvent(event))
                .assertNext(saved -> {
                    assertThat(saved.getMetricName()).isEqualTo("request.count");
                    assertThat(saved.getMetricType()).isEqualTo("COUNTER");
                    assertThat(saved.getMetricValue()).isEqualTo(1.0);
                })
                .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldListMetricEventsWithAllParams() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "execution.duration";
        OffsetDateTime start = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime end = start.plusHours(1);

        MetricEvent event1 = MetricEvent.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).metricName(metricName)
                .metricType("TIMER").metricValue(150.0).timestamp(start.plusMinutes(5))
                .build();
        MetricEvent event2 = MetricEvent.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).metricName(metricName)
                .metricType("TIMER").metricValue(200.0).timestamp(start.plusMinutes(30))
                .build();

        when(metricEventRepository.findByMetricNameAndPeriod(tenantId, metricName, start, end, 50))
                .thenReturn(Flux.just(event1, event2));

        StepVerifier.create(metricService.listMetricEvents(tenantId, metricName, start, end, 50))
                .expectNextCount(2)
                .verifyComplete();

        verify(metricEventRepository).findByMetricNameAndPeriod(tenantId, metricName, start, end, 50);
    }

    @Test
    void shouldReturnEmptyListWhenNoMetricEvents() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now();

        when(metricEventRepository.findByMetricNameAndPeriod(tenantId, "none", start, end, 10))
                .thenReturn(Flux.empty());

        StepVerifier.create(metricService.listMetricEvents(tenantId, "none", start, end, 10))
                .verifyComplete();
    }

    @Test
    void shouldRecordCounter() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "api.requests";

        when(metricEventRepository.save(any(MetricEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(metricService.recordCounter(tenantId, metricName, 1.0, null))
                .assertNext(event -> {
                    assertThat(event.getMetricType()).isEqualTo("COUNTER");
                    assertThat(event.getMetricName()).isEqualTo(metricName);
                    assertThat(event.getMetricValue()).isEqualTo(1.0);
                })
                .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldRecordGauge() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "memory.usage";

        when(metricEventRepository.save(any(MetricEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(metricService.recordGauge(tenantId, metricName, 1024.0, "bytes"))
                .assertNext(event -> {
                    assertThat(event.getMetricType()).isEqualTo("GAUGE");
                    assertThat(event.getMetricName()).isEqualTo(metricName);
                    assertThat(event.getMetricValue()).isEqualTo(1024.0);
                })
                .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldRecordTimer() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "api.response.time";

        when(metricEventRepository.save(any(MetricEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(metricService.recordTimer(tenantId, metricName, 150L))
                .assertNext(event -> {
                    assertThat(event.getMetricType()).isEqualTo("TIMER");
                    assertThat(event.getMetricName()).isEqualTo(metricName);
                    assertThat(event.getMetricValue()).isEqualTo(150.0);
                })
                .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldRecordHistogram() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "request.size";

        when(metricEventRepository.save(any(MetricEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(metricService.recordHistogram(tenantId, metricName, 2048.0, "bytes"))
                .assertNext(event -> {
                    assertThat(event.getMetricType()).isEqualTo("HISTOGRAM");
                    assertThat(event.getMetricName()).isEqualTo(metricName);
                    assertThat(event.getMetricValue()).isEqualTo(2048.0);
                })
                .verifyComplete();

        verify(metricEventRepository).save(any(MetricEvent.class));
    }

    @Test
    void shouldGetAggregatedMetrics() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "execution.count";
        OffsetDateTime start = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime end = start.plusHours(1);

        AggregatedMetric metric = AggregatedMetric.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .metricName(metricName)
                .aggregationType("SUM")
                .aggregationPeriod("HOUR")
                .periodStart(start)
                .periodEnd(end)
                .value(42.0)
                .build();

        when(aggregatedMetricRepository.findAggregatedMetrics(tenantId, metricName, "SUM", "HOUR", start, end))
                .thenReturn(Flux.just(metric));

        StepVerifier.create(metricService.getAggregatedMetrics(tenantId, metricName, "SUM", "HOUR", start, end))
                .assertNext(m -> {
                    assertThat(m.getMetricName()).isEqualTo(metricName);
                    assertThat(m.getAggregationType()).isEqualTo("SUM");
                })
                .verifyComplete();

        verify(aggregatedMetricRepository).findAggregatedMetrics(tenantId, metricName, "SUM", "HOUR", start, end);
    }
}