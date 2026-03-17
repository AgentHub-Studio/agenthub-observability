package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.ExecutionTrace;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for ExecutionTraceRepository.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionTraceRepositoryTest {

    @Mock
    private ExecutionTraceRepository repository;

    @Test
    void shouldFindByExecutionId() {
        UUID executionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ExecutionTrace trace = ExecutionTrace.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .tenantId(tenantId)
                .status(ExecutionTrace.ExecutionStatus.RUNNING)
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(repository.findByExecutionId(executionId)).thenReturn(Mono.just(trace));

        StepVerifier.create(repository.findByExecutionId(executionId))
                .assertNext(found -> {
                    assertThat(found.getExecutionId()).isEqualTo(executionId);
                    assertThat(found.getStatus()).isEqualTo(ExecutionTrace.ExecutionStatus.RUNNING);
                })
                .verifyComplete();
    }

    @Test
    void shouldFindByTenantId() {
        UUID tenantId = UUID.randomUUID();

        ExecutionTrace trace1 = ExecutionTrace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(ExecutionTrace.ExecutionStatus.COMPLETED)
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        ExecutionTrace trace2 = ExecutionTrace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(ExecutionTrace.ExecutionStatus.FAILED)
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(repository.findByTenantIdOrderByStartedAtDesc(tenantId))
                .thenReturn(Flux.just(trace1, trace2));

        StepVerifier.create(repository.findByTenantIdOrderByStartedAtDesc(tenantId))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldFindByTenantIdAndAgentId() {
        UUID tenantId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        ExecutionTrace trace = ExecutionTrace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .agentId(agentId)
                .status(ExecutionTrace.ExecutionStatus.COMPLETED)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(repository.findByTenantIdAndAgentIdOrderByStartedAtDesc(tenantId, agentId))
                .thenReturn(Flux.just(trace));

        StepVerifier.create(repository.findByTenantIdAndAgentIdOrderByStartedAtDesc(tenantId, agentId))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenExecutionIdNotFound() {
        UUID executionId = UUID.randomUUID();

        when(repository.findByExecutionId(executionId)).thenReturn(Mono.empty());

        StepVerifier.create(repository.findByExecutionId(executionId))
                .verifyComplete();
    }

    @Test
    void shouldCountByStatusSince() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime since = OffsetDateTime.now().minusHours(1);

        when(repository.countByStatusSince(tenantId, "RUNNING", since))
                .thenReturn(Mono.just(5L));

        StepVerifier.create(repository.countByStatusSince(tenantId, "RUNNING", since))
                .assertNext(count -> assertThat(count).isEqualTo(5L))
                .verifyComplete();
    }
}