package dev.cezar.agenthub.observability.scheduler;

import dev.cezar.agenthub.observability.service.AggregationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AggregationEngine.
 */
@ExtendWith(MockitoExtension.class)
class AggregationEngineTest {

    @Mock
    private AggregationService aggregationService;

    private AggregationEngine aggregationEngine;

    @BeforeEach
    void setUp() {
        aggregationEngine = new AggregationEngine(aggregationService);
    }

    @Test
    void shouldRunMinuteAggregationWithoutThrowing() {
        aggregationEngine.aggregateMinuteMetrics();
    }

    @Test
    void shouldRunHourAggregationWithoutThrowing() {
        aggregationEngine.aggregateHourMetrics();
    }

    @Test
    void shouldRunDayAggregationWithoutThrowing() {
        aggregationEngine.aggregateDayMetrics();
    }

    @Test
    void shouldRunWeekAggregationWithoutThrowing() {
        aggregationEngine.aggregateWeekMetrics();
    }

    @Test
    void shouldRunMonthAggregationWithoutThrowing() {
        aggregationEngine.aggregateMonthMetrics();
    }

    @Test
    void shouldRunCleanupWithoutThrowing() {
        when(aggregationService.cleanupOldAggregations(anyInt())).thenReturn(Mono.just(0L));
        aggregationEngine.cleanupOldAggregations();
    }
}