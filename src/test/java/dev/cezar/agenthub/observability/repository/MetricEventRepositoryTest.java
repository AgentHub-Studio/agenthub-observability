package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.MetricEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricEventRepository.
 */
@DataR2dbcTest
@TestPropertySource(properties = {
    "spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1",
    "spring.r2dbc.username=sa",
    "spring.r2dbc.password="
})
class MetricEventRepositoryTest {

    @Autowired
    private MetricEventRepository repository;

    @Test
    void shouldSaveAndFindMetricEvent() {
        // Given
        MetricEvent event = new MetricEvent();
        event.setTenantId(1L);
        event.setMetricName("request.count");
        event.setMetricType("COUNTER");
        event.setValue(1.0);
        event.setTimestamp(Instant.now());
        event.setDimensions(Map.of("endpoint", "/api/agents"));

        // When & Then
        StepVerifier.create(repository.save(event))
            .assertNext(saved -> {
                assertThat(saved.getId()).isNotNull();
                assertThat(saved.getMetricName()).isEqualTo("request.count");
                assertThat(saved.getMetricType()).isEqualTo("COUNTER");
                assertThat(saved.getValue()).isEqualTo(1.0);
            })
            .verifyComplete();
    }

    @Test
    void shouldFindByTenantId() {
        // Given
        Long tenantId = 1L;
        MetricEvent event1 = createMetricEvent(tenantId, "metric-1", "COUNTER");
        MetricEvent event2 = createMetricEvent(tenantId, "metric-2", "GAUGE");
        MetricEvent event3 = createMetricEvent(2L, "metric-3", "COUNTER");

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(event1, event2, event3))
                .thenMany(repository.findByTenantId(tenantId))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    void shouldFindByMetricName() {
        // Given
        String metricName = "request.count";
        MetricEvent event1 = createMetricEvent(1L, metricName, "COUNTER");
        MetricEvent event2 = createMetricEvent(1L, metricName, "COUNTER");
        MetricEvent event3 = createMetricEvent(1L, "response.time", "TIMER");

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(event1, event2, event3))
                .thenMany(repository.findByMetricName(metricName))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    void shouldFindByTenantIdAndMetricName() {
        // Given
        Long tenantId = 1L;
        String metricName = "request.count";
        MetricEvent event1 = createMetricEvent(tenantId, metricName, "COUNTER");
        MetricEvent event2 = createMetricEvent(tenantId, metricName, "COUNTER");
        MetricEvent event3 = createMetricEvent(tenantId, "other.metric", "GAUGE");
        MetricEvent event4 = createMetricEvent(2L, metricName, "COUNTER");

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(event1, event2, event3, event4))
                .thenMany(repository.findByTenantIdAndMetricName(tenantId, metricName))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    void shouldFindByTimestampBetween() {
        // Given
        Instant now = Instant.now();
        Instant start = now.minusSeconds(3600);
        Instant end = now.plusSeconds(3600);
        
        MetricEvent before = createMetricEvent(1L, "metric-1", "COUNTER");
        before.setTimestamp(start.minusSeconds(7200));
        
        MetricEvent during1 = createMetricEvent(1L, "metric-2", "COUNTER");
        during1.setTimestamp(now);
        
        MetricEvent during2 = createMetricEvent(1L, "metric-3", "COUNTER");
        during2.setTimestamp(now.plusSeconds(1800));
        
        MetricEvent after = createMetricEvent(1L, "metric-4", "COUNTER");
        after.setTimestamp(end.plusSeconds(7200));

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(before, during1, during2, after))
                .thenMany(repository.findByTimestampBetween(start, end))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    void shouldDeleteOldEvents() {
        // Given
        Instant cutoff = Instant.now().minusSeconds(3600);
        MetricEvent old1 = createMetricEvent(1L, "old-1", "COUNTER");
        old1.setTimestamp(cutoff.minusSeconds(7200));
        MetricEvent old2 = createMetricEvent(1L, "old-2", "COUNTER");
        old2.setTimestamp(cutoff.minusSeconds(3600));
        MetricEvent recent = createMetricEvent(1L, "recent", "COUNTER");
        recent.setTimestamp(cutoff.plusSeconds(3600));

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(old1, old2, recent))
                .then(repository.deleteByTimestampBefore(cutoff))
        )
        .assertNext(count -> assertThat(count).isEqualTo(2L))
        .verifyComplete();
    }

    private MetricEvent createMetricEvent(Long tenantId, String metricName, String metricType) {
        MetricEvent event = new MetricEvent();
        event.setTenantId(tenantId);
        event.setMetricName(metricName);
        event.setMetricType(metricType);
        event.setValue(1.0);
        event.setTimestamp(Instant.now());
        event.setDimensions(Map.of("test", "true"));
        return event;
    }
}
