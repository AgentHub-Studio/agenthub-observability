package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.MetricEvent;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for MetricEventRepository.
 */
@ExtendWith(MockitoExtension.class)
class MetricEventRepositoryTest {

    @Mock
    private MetricEventRepository repository;

    @Test
    void shouldSaveAndFindMetricEvent() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        MetricEvent event = MetricEvent.builder()
                .id(id)
                .tenantId(tenantId)
                .metricName("request.count")
                .metricType("COUNTER")
                .metricValue(1.0)
                .timestamp(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();

        when(repository.save(event)).thenReturn(Mono.just(event));

        StepVerifier.create(repository.save(event))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isEqualTo(id);
                    assertThat(saved.getMetricName()).isEqualTo("request.count");
                    assertThat(saved.getMetricType()).isEqualTo("COUNTER");
                    assertThat(saved.getMetricValue()).isEqualTo(1.0);
                })
                .verifyComplete();
    }

    @Test
    void shouldFindByMetricNameAndPeriod() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "execution.duration";
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now();

        MetricEvent event1 = MetricEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .metricName(metricName)
                .metricType("TIMER")
                .metricValue(250.0)
                .timestamp(start.plusMinutes(10))
                .build();

        MetricEvent event2 = MetricEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .metricName(metricName)
                .metricType("TIMER")
                .metricValue(300.0)
                .timestamp(start.plusMinutes(30))
                .build();

        when(repository.findByMetricNameAndPeriod(tenantId, metricName, start, end, 100))
                .thenReturn(Flux.just(event1, event2));

        StepVerifier.create(repository.findByMetricNameAndPeriod(tenantId, metricName, start, end, 100))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenNoEventsInPeriod() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now();

        when(repository.findByMetricNameAndPeriod(tenantId, "no.events", start, end, 100))
                .thenReturn(Flux.empty());

        StepVerifier.create(repository.findByMetricNameAndPeriod(tenantId, "no.events", start, end, 100))
                .verifyComplete();
    }

    @Test
    void shouldFindById() {
        UUID id = UUID.randomUUID();
        MetricEvent event = MetricEvent.builder()
                .id(id)
                .metricName("cpu.usage")
                .metricType("GAUGE")
                .metricValue(45.5)
                .timestamp(OffsetDateTime.now())
                .build();

        when(repository.findById(id)).thenReturn(Mono.just(event));

        StepVerifier.create(repository.findById(id))
                .assertNext(found -> {
                    assertThat(found.getId()).isEqualTo(id);
                    assertThat(found.getMetricValue()).isEqualTo(45.5);
                })
                .verifyComplete();
    }
}