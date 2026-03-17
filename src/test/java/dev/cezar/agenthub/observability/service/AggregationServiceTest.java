package dev.cezar.agenthub.observability.service;

import dev.cezar.agenthub.observability.domain.AggregatedMetric;
import dev.cezar.agenthub.observability.repository.AggregatedMetricRepository;
import dev.cezar.agenthub.observability.repository.MetricEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private R2dbcEntityTemplate r2dbcTemplate;

    private AggregationService aggregationService;

    @BeforeEach
    void setUp() {
        aggregationService = new AggregationService(metricEventRepository, aggregatedMetricRepository, r2dbcTemplate);
    }

    @Test
    void shouldAggregateMetricsAndSaveAllAggregationTypes() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "execution.duration";
        OffsetDateTime start = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime end = start.plusHours(1);

        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(aggregationService.aggregateMetrics(tenantId, metricName, "HOUR", start, end))
                .expectNextCount(8) // SUM, AVG, MIN, MAX, COUNT, P50, P95, P99
                .verifyComplete();

        verify(aggregatedMetricRepository, times(8)).save(any(AggregatedMetric.class));
    }

    @Test
    void shouldSaveAggregationWithCorrectTenantAndMetricName() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "tool.latency";
        OffsetDateTime start = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime end = start.plusHours(1);

        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(aggregationService.aggregateMetrics(tenantId, metricName, "HOUR", start, end))
                .assertNext(metric -> {
                    assertThat(metric.getTenantId()).isEqualTo(tenantId);
                    assertThat(metric.getMetricName()).isEqualTo(metricName);
                    assertThat(metric.getAggregationPeriod()).isEqualTo("HOUR");
                })
                .expectNextCount(7)
                .verifyComplete();
    }

    @Test
    void shouldAggregateAllMetricsForUnknownPeriodReturnZero() {
        UUID tenantId = UUID.randomUUID();

        StepVerifier.create(aggregationService.aggregateAllMetrics(tenantId, "UNKNOWN"))
                .assertNext(count -> assertThat(count).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    void shouldAggregateAllMetricsForMinutePeriod() {
        UUID tenantId = UUID.randomUUID();

        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // aggregateAllMetrics iterates 8 common metrics x 8 aggregation types = 64 saves
        StepVerifier.create(aggregationService.aggregateAllMetrics(tenantId, "MINUTE"))
                .assertNext(count -> assertThat(count).isGreaterThan(0))
                .verifyComplete();
    }

    @Test
    void shouldCleanupOldAggregationsReturnZero() {
        // cleanupOldAggregations is a stub that returns 0
        StepVerifier.create(aggregationService.cleanupOldAggregations(30))
                .assertNext(count -> assertThat(count).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    void shouldAggregateLastHourWithSaveForAllTypes() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "execution.count";

        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(aggregationService.aggregateLastHour(tenantId, metricName))
                .expectNextCount(8)
                .verifyComplete();
    }

    @Test
    void shouldAggregateLastDayWithSaveForAllTypes() {
        UUID tenantId = UUID.randomUUID();
        String metricName = "tool.invocation";

        when(aggregatedMetricRepository.save(any(AggregatedMetric.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(aggregationService.aggregateLastDay(tenantId, metricName))
                .expectNextCount(8)
                .verifyComplete();
    }
}