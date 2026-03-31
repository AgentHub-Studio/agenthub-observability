package dev.cezar.agenthub.observability.controller;

import dev.cezar.agenthub.observability.api.CreateMetricEventRequest;
import dev.cezar.agenthub.observability.domain.AggregatedMetric;
import dev.cezar.agenthub.observability.domain.MetricEvent;
import dev.cezar.agenthub.observability.service.MetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MetricController.
 */
@ExtendWith(MockitoExtension.class)
class MetricControllerTest {

    @Mock
    private MetricService metricService;

    private MetricController controller;

    @BeforeEach
    void setUp() {
        controller = new MetricController(metricService);
    }

    @Test
    void shouldCreateMetricEventFromRequest() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.now();
        CreateMetricEventRequest request = new CreateMetricEventRequest(
                tenantId, "execution.count", "COUNTER", 1.0, "count", null, timestamp
        );

        MetricEvent saved = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName("execution.count")
                .metricType("COUNTER")
                .metricValue(1.0)
                .metricUnit("count")
                .timestamp(timestamp)
                .build();

        when(metricService.createMetricEvent(any(MetricEvent.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(controller.createMetricEvent(request))
                .assertNext(event -> {
                    assertThat(event.getTenantId()).isEqualTo(tenantId);
                    assertThat(event.getMetricName()).isEqualTo("execution.count");
                    assertThat(event.getMetricType()).isEqualTo("COUNTER");
                    assertThat(event.getMetricValue()).isEqualTo(1.0);
                })
                .verifyComplete();

        verify(metricService).createMetricEvent(any(MetricEvent.class));
    }

    @Test
    void shouldUseCurrentTimestampWhenNotProvidedInRequest() {
        UUID tenantId = UUID.randomUUID();
        CreateMetricEventRequest request = new CreateMetricEventRequest(
                tenantId, "api.latency", "TIMER", 250.0, "ms", null, null
        );

        MetricEvent saved = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName("api.latency")
                .metricType("TIMER")
                .metricValue(250.0)
                .metricUnit("ms")
                .timestamp(OffsetDateTime.now())
                .build();

        when(metricService.createMetricEvent(any(MetricEvent.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(controller.createMetricEvent(request))
                .assertNext(event -> {
                    assertThat(event.getMetricName()).isEqualTo("api.latency");
                    assertThat(event.getTimestamp()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldRecordCounterMetric() {
        UUID tenantId = UUID.randomUUID();
        MetricEvent saved = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName("skill.invocation")
                .metricType("COUNTER")
                .metricValue(1.0)
                .metricUnit("count")
                .timestamp(OffsetDateTime.now())
                .build();

        when(metricService.recordCounter(eq(tenantId), eq("skill.invocation"), eq(1.0), any()))
                .thenReturn(Mono.just(saved));

        StepVerifier.create(controller.recordCounter(tenantId, "skill.invocation", 1.0))
                .assertNext(event -> {
                    assertThat(event.getMetricType()).isEqualTo("COUNTER");
                    assertThat(event.getMetricName()).isEqualTo("skill.invocation");
                })
                .verifyComplete();

        verify(metricService).recordCounter(eq(tenantId), eq("skill.invocation"), eq(1.0), any());
    }

    @Test
    void shouldRecordGaugeMetric() {
        UUID tenantId = UUID.randomUUID();
        MetricEvent saved = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName("memory.usage")
                .metricType("GAUGE")
                .metricValue(75.5)
                .metricUnit("percent")
                .timestamp(OffsetDateTime.now())
                .build();

        when(metricService.recordGauge(tenantId, "memory.usage", 75.5, "percent"))
                .thenReturn(Mono.just(saved));

        StepVerifier.create(controller.recordGauge(tenantId, "memory.usage", 75.5, "percent"))
                .assertNext(event -> {
                    assertThat(event.getMetricType()).isEqualTo("GAUGE");
                    assertThat(event.getMetricValue()).isEqualTo(75.5);
                })
                .verifyComplete();

        verify(metricService).recordGauge(tenantId, "memory.usage", 75.5, "percent");
    }

    @Test
    void shouldRecordTimerMetric() {
        UUID tenantId = UUID.randomUUID();
        MetricEvent saved = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName("http.request.duration")
                .metricType("TIMER")
                .metricValue(350.0)
                .metricUnit("ms")
                .timestamp(OffsetDateTime.now())
                .build();

        when(metricService.recordTimer(tenantId, "http.request.duration", 350L))
                .thenReturn(Mono.just(saved));

        StepVerifier.create(controller.recordTimer(tenantId, "http.request.duration", 350L))
                .assertNext(event -> {
                    assertThat(event.getMetricType()).isEqualTo("TIMER");
                    assertThat(event.getMetricUnit()).isEqualTo("ms");
                })
                .verifyComplete();

        verify(metricService).recordTimer(tenantId, "http.request.duration", 350L);
    }

    @Test
    void shouldRecordHistogramMetric() {
        UUID tenantId = UUID.randomUUID();
        MetricEvent saved = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName("response.size")
                .metricType("HISTOGRAM")
                .metricValue(1024.0)
                .metricUnit("bytes")
                .timestamp(OffsetDateTime.now())
                .build();

        when(metricService.recordHistogram(tenantId, "response.size", 1024.0, "bytes"))
                .thenReturn(Mono.just(saved));

        StepVerifier.create(controller.recordHistogram(tenantId, "response.size", 1024.0, "bytes"))
                .assertNext(event -> {
                    assertThat(event.getMetricType()).isEqualTo("HISTOGRAM");
                    assertThat(event.getMetricName()).isEqualTo("response.size");
                })
                .verifyComplete();

        verify(metricService).recordHistogram(tenantId, "response.size", 1024.0, "bytes");
    }

    @Test
    void shouldListMetricEvents() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now();

        MetricEvent event1 = MetricEvent.builder()
                .tenantId(tenantId)
                .metricName("execution.count")
                .metricType("COUNTER")
                .metricValue(1.0)
                .timestamp(start.plusMinutes(10))
                .build();

        when(metricService.listMetricEvents(tenantId, "execution.count", start, end, 1000))
                .thenReturn(Flux.just(event1));

        StepVerifier.create(controller.listMetricEvents(tenantId, "execution.count", start, end, 1000))
                .assertNext(e -> assertThat(e.getMetricName()).isEqualTo("execution.count"))
                .verifyComplete();

        verify(metricService).listMetricEvents(tenantId, "execution.count", start, end, 1000);
    }

    @Test
    void shouldGetAggregatedMetrics() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().minusDays(7);
        OffsetDateTime end = OffsetDateTime.now();

        AggregatedMetric metric = AggregatedMetric.builder()
                .tenantId(tenantId)
                .metricName("execution.count")
                .aggregationType("SUM")
                .aggregationPeriod("DAY")
                .value(42.0)
                .sampleCount(42L)
                .periodStart(start)
                .periodEnd(end)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(metricService.getAggregatedMetrics(tenantId, "execution.count", "SUM", "DAY", start, end))
                .thenReturn(Flux.just(metric));

        StepVerifier.create(controller.getAggregatedMetrics(
                        tenantId, "execution.count", "SUM", "DAY", start, end))
                .assertNext(m -> {
                    assertThat(m.getAggregationType()).isEqualTo("SUM");
                    assertThat(m.getValue()).isEqualTo(42.0);
                })
                .verifyComplete();

        verify(metricService).getAggregatedMetrics(tenantId, "execution.count", "SUM", "DAY", start, end);
    }
}
