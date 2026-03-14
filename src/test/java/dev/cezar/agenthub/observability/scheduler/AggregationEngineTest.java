package dev.cezar.agenthub.observability.scheduler;

import dev.cezar.agenthub.observability.domain.AggregatedMetric;
import dev.cezar.agenthub.observability.domain.ExecutionTrace;
import dev.cezar.agenthub.observability.domain.MetricEvent;
import dev.cezar.agenthub.observability.repository.ExecutionTraceRepository;
import dev.cezar.agenthub.observability.repository.MetricEventRepository;
import dev.cezar.agenthub.observability.service.AggregationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AggregationEngine.
 * Tests the scheduled aggregation jobs.
 */
@ExtendWith(MockitoExtension.class)
class AggregationEngineTest {

    @Mock
    private AggregationService aggregationService;

    @Mock
    private MetricEventRepository metricEventRepository;

    @Mock
    private ExecutionTraceRepository executionTraceRepository;

    private AggregationEngine aggregationEngine;

    @BeforeEach
    void setUp() {
        aggregationEngine = new AggregationEngine(
            aggregationService,
            metricEventRepository,
            executionTraceRepository
        );
    }

    @Test
    void shouldAggregateMinuteMetrics() {
        // Given
        String metricName = "test.metric";
        MetricEvent event = new MetricEvent();
        event.setMetricName(metricName);

        when(metricEventRepository.findDistinctMetricNames())
            .thenReturn(Flux.just(metricName));
        when(aggregationService.computeAllAggregations(
            anyLong(), eq(metricName), eq("minute"), any(Instant.class), any(Instant.class)))
            .thenReturn(Flux.just(new AggregatedMetric()));

        // When
        aggregationEngine.aggregateMinuteMetrics();

        // Then
        verify(metricEventRepository).findDistinctMetricNames();
        verify(aggregationService, atLeastOnce()).computeAllAggregations(
            anyLong(), eq(metricName), eq("minute"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldAggregateHourMetrics() {
        // Given
        String metricName = "test.metric";
        when(metricEventRepository.findDistinctMetricNames())
            .thenReturn(Flux.just(metricName));
        when(aggregationService.computeAllAggregations(
            anyLong(), eq(metricName), eq("hour"), any(Instant.class), any(Instant.class)))
            .thenReturn(Flux.just(new AggregatedMetric()));

        // When
        aggregationEngine.aggregateHourMetrics();

        // Then
        verify(metricEventRepository).findDistinctMetricNames();
        verify(aggregationService, atLeastOnce()).computeAllAggregations(
            anyLong(), eq(metricName), eq("hour"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldAggregateDayMetrics() {
        // Given
        String metricName = "test.metric";
        when(metricEventRepository.findDistinctMetricNames())
            .thenReturn(Flux.just(metricName));
        when(aggregationService.computeAllAggregations(
            anyLong(), eq(metricName), eq("day"), any(Instant.class), any(Instant.class)))
            .thenReturn(Flux.just(new AggregatedMetric()));

        // When
        aggregationEngine.aggregateDayMetrics();

        // Then
        verify(metricEventRepository).findDistinctMetricNames();
        verify(aggregationService, atLeastOnce()).computeAllAggregations(
            anyLong(), eq(metricName), eq("day"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldAggregateWeekMetrics() {
        // Given
        String metricName = "test.metric";
        when(metricEventRepository.findDistinctMetricNames())
            .thenReturn(Flux.just(metricName));
        when(aggregationService.computeAllAggregations(
            anyLong(), eq(metricName), eq("week"), any(Instant.class), any(Instant.class)))
            .thenReturn(Flux.just(new AggregatedMetric()));

        // When
        aggregationEngine.aggregateWeekMetrics();

        // Then
        verify(metricEventRepository).findDistinctMetricNames();
        verify(aggregationService, atLeastOnce()).computeAllAggregations(
            anyLong(), eq(metricName), eq("week"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldAggregateMonthMetrics() {
        // Given
        String metricName = "test.metric";
        when(metricEventRepository.findDistinctMetricNames())
            .thenReturn(Flux.just(metricName));
        when(aggregationService.computeAllAggregations(
            anyLong(), eq(metricName), eq("month"), any(Instant.class), any(Instant.class)))
            .thenReturn(Flux.just(new AggregatedMetric()));

        // When
        aggregationEngine.aggregateMonthMetrics();

        // Then
        verify(metricEventRepository).findDistinctMetricNames();
        verify(aggregationService, atLeastOnce()).computeAllAggregations(
            anyLong(), eq(metricName), eq("month"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldCleanupOldData() {
        // Given
        when(executionTraceRepository.deleteByStartTimeBefore(any(Instant.class)))
            .thenReturn(Mono.just(10L));
        when(metricEventRepository.deleteByTimestampBefore(any(Instant.class)))
            .thenReturn(Mono.just(20L));

        // When
        aggregationEngine.cleanupOldData();

        // Then
        verify(executionTraceRepository).deleteByStartTimeBefore(any(Instant.class));
        verify(metricEventRepository).deleteByTimestampBefore(any(Instant.class));
    }

    @Test
    void shouldHandleNoMetricsGracefully() {
        // Given
        when(metricEventRepository.findDistinctMetricNames())
            .thenReturn(Flux.empty());

        // When
        aggregationEngine.aggregateMinuteMetrics();

        // Then
        verify(metricEventRepository).findDistinctMetricNames();
        verify(aggregationService, never()).computeAllAggregations(
            anyLong(), anyString(), anyString(), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldHandleMultipleMetrics() {
        // Given
        String metric1 = "metric.one";
        String metric2 = "metric.two";
        when(metricEventRepository.findDistinctMetricNames())
            .thenReturn(Flux.just(metric1, metric2));
        when(aggregationService.computeAllAggregations(
            anyLong(), anyString(), eq("hour"), any(Instant.class), any(Instant.class)))
            .thenReturn(Flux.just(new AggregatedMetric()));

        // When
        aggregationEngine.aggregateHourMetrics();

        // Then
        verify(metricEventRepository).findDistinctMetricNames();
        verify(aggregationService, atLeastOnce()).computeAllAggregations(
            anyLong(), eq(metric1), eq("hour"), any(Instant.class), any(Instant.class));
        verify(aggregationService, atLeastOnce()).computeAllAggregations(
            anyLong(), eq(metric2), eq("hour"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldHandleAggregationErrors() {
        // Given
        String metricName = "test.metric";
        when(metricEventRepository.findDistinctMetricNames())
            .thenReturn(Flux.just(metricName));
        when(aggregationService.computeAllAggregations(
            anyLong(), eq(metricName), eq("minute"), any(Instant.class), any(Instant.class)))
            .thenReturn(Flux.error(new RuntimeException("Aggregation failed")));

        // When - should not throw exception
        aggregationEngine.aggregateMinuteMetrics();

        // Then
        verify(metricEventRepository).findDistinctMetricNames();
        verify(aggregationService, atLeastOnce()).computeAllAggregations(
            anyLong(), eq(metricName), eq("minute"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldHandleCleanupErrors() {
        // Given
        when(executionTraceRepository.deleteByStartTimeBefore(any(Instant.class)))
            .thenReturn(Mono.error(new RuntimeException("Cleanup failed")));
        when(metricEventRepository.deleteByTimestampBefore(any(Instant.class)))
            .thenReturn(Mono.just(20L));

        // When - should not throw exception
        aggregationEngine.cleanupOldData();

        // Then
        verify(executionTraceRepository).deleteByStartTimeBefore(any(Instant.class));
        verify(metricEventRepository).deleteByTimestampBefore(any(Instant.class));
    }
}
