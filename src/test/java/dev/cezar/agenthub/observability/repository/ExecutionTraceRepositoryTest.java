package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.ExecutionTrace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExecutionTraceRepository.
 * Tests basic CRUD operations and custom queries.
 */
@DataR2dbcTest
@TestPropertySource(properties = {
    "spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1",
    "spring.r2dbc.username=sa",
    "spring.r2dbc.password="
})
class ExecutionTraceRepositoryTest {

    @Autowired
    private ExecutionTraceRepository repository;

    @Test
    void shouldSaveAndFindExecutionTrace() {
        // Given
        ExecutionTrace trace = new ExecutionTrace();
        trace.setExecutionId(UUID.randomUUID().toString());
        trace.setTenantId(1L);
        trace.setAgentId(100L);
        trace.setPipelineId(200L);
        trace.setStatus("RUNNING");
        trace.setStartTime(Instant.now());
        trace.setInputParameters(Map.of("key", "value"));

        // When & Then
        StepVerifier.create(repository.save(trace))
            .assertNext(saved -> {
                assertThat(saved.getId()).isNotNull();
                assertThat(saved.getExecutionId()).isEqualTo(trace.getExecutionId());
                assertThat(saved.getTenantId()).isEqualTo(1L);
                assertThat(saved.getAgentId()).isEqualTo(100L);
                assertThat(saved.getStatus()).isEqualTo("RUNNING");
            })
            .verifyComplete();
    }

    @Test
    void shouldFindByExecutionId() {
        // Given
        String executionId = UUID.randomUUID().toString();
        ExecutionTrace trace = new ExecutionTrace();
        trace.setExecutionId(executionId);
        trace.setTenantId(1L);
        trace.setAgentId(100L);
        trace.setPipelineId(200L);
        trace.setStatus("COMPLETED");
        trace.setStartTime(Instant.now());
        trace.setEndTime(Instant.now());

        // When & Then
        StepVerifier.create(
            repository.save(trace)
                .then(repository.findByExecutionId(executionId))
        )
        .assertNext(found -> {
            assertThat(found.getExecutionId()).isEqualTo(executionId);
            assertThat(found.getStatus()).isEqualTo("COMPLETED");
        })
        .verifyComplete();
    }

    @Test
    void shouldFindByTenantId() {
        // Given
        Long tenantId = 1L;
        ExecutionTrace trace1 = createTrace(tenantId, "exec-1");
        ExecutionTrace trace2 = createTrace(tenantId, "exec-2");
        ExecutionTrace trace3 = createTrace(2L, "exec-3");

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(trace1, trace2, trace3))
                .thenMany(repository.findByTenantId(tenantId))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    void shouldFindByTenantIdAndAgentId() {
        // Given
        Long tenantId = 1L;
        Long agentId = 100L;
        ExecutionTrace trace1 = createTrace(tenantId, "exec-1");
        trace1.setAgentId(agentId);
        ExecutionTrace trace2 = createTrace(tenantId, "exec-2");
        trace2.setAgentId(agentId);
        ExecutionTrace trace3 = createTrace(tenantId, "exec-3");
        trace3.setAgentId(200L);

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(trace1, trace2, trace3))
                .thenMany(repository.findByTenantIdAndAgentId(tenantId, agentId))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    void shouldFindByStatus() {
        // Given
        ExecutionTrace running1 = createTrace(1L, "exec-1");
        running1.setStatus("RUNNING");
        ExecutionTrace running2 = createTrace(1L, "exec-2");
        running2.setStatus("RUNNING");
        ExecutionTrace completed = createTrace(1L, "exec-3");
        completed.setStatus("COMPLETED");

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(running1, running2, completed))
                .thenMany(repository.findByStatus("RUNNING"))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    void shouldCountByTenantId() {
        // Given
        Long tenantId = 1L;
        ExecutionTrace trace1 = createTrace(tenantId, "exec-1");
        ExecutionTrace trace2 = createTrace(tenantId, "exec-2");
        ExecutionTrace trace3 = createTrace(2L, "exec-3");

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(trace1, trace2, trace3))
                .then(repository.countByTenantId(tenantId))
        )
        .assertNext(count -> assertThat(count).isEqualTo(2L))
        .verifyComplete();
    }

    @Test
    void shouldDeleteOldTraces() {
        // Given
        Instant cutoff = Instant.now().minusSeconds(3600);
        ExecutionTrace old1 = createTrace(1L, "old-1");
        old1.setStartTime(cutoff.minusSeconds(7200));
        ExecutionTrace old2 = createTrace(1L, "old-2");
        old2.setStartTime(cutoff.minusSeconds(3600));
        ExecutionTrace recent = createTrace(1L, "recent");
        recent.setStartTime(cutoff.plusSeconds(3600));

        // When & Then
        StepVerifier.create(
            repository.saveAll(java.util.List.of(old1, old2, recent))
                .then(repository.deleteByStartTimeBefore(cutoff))
        )
        .assertNext(count -> assertThat(count).isEqualTo(2L))
        .verifyComplete();
    }

    private ExecutionTrace createTrace(Long tenantId, String executionId) {
        ExecutionTrace trace = new ExecutionTrace();
        trace.setExecutionId(executionId);
        trace.setTenantId(tenantId);
        trace.setAgentId(100L);
        trace.setPipelineId(200L);
        trace.setStatus("RUNNING");
        trace.setStartTime(Instant.now());
        return trace;
    }
}
