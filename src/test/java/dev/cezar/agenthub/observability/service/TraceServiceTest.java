package dev.cezar.agenthub.observability.service;

import dev.cezar.agenthub.observability.api.CreateExecutionTraceRequest;
import dev.cezar.agenthub.observability.api.CreateToolExecutionTraceRequest;
import dev.cezar.agenthub.observability.api.UpdateExecutionTraceRequest;
import dev.cezar.agenthub.observability.domain.ExecutionTrace;
import dev.cezar.agenthub.observability.domain.NodeExecutionTrace;
import dev.cezar.agenthub.observability.domain.ToolExecutionTrace;
import dev.cezar.agenthub.observability.repository.ExecutionTraceRepository;
import dev.cezar.agenthub.observability.repository.NodeExecutionTraceRepository;
import dev.cezar.agenthub.observability.repository.ToolExecutionTraceRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TraceService.
 */
@ExtendWith(MockitoExtension.class)
class TraceServiceTest {

    @Mock
    private ExecutionTraceRepository executionTraceRepository;

    @Mock
    private NodeExecutionTraceRepository nodeExecutionTraceRepository;

    @Mock
    private ToolExecutionTraceRepository toolExecutionTraceRepository;

    private TraceService traceService;

    @BeforeEach
    void setUp() {
        traceService = new TraceService(
            executionTraceRepository,
            nodeExecutionTraceRepository,
            toolExecutionTraceRepository
        );
    }

    @Test
    void shouldCreateExecutionTrace() {
        UUID tenantId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        CreateExecutionTraceRequest request = new CreateExecutionTraceRequest(
            tenantId, agentId, null, null, executionId,
            "RUNNING", OffsetDateTime.now(), null, null, null
        );

        ExecutionTrace saved = ExecutionTrace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .executionId(executionId)
                .agentId(agentId)
                .status(ExecutionTrace.ExecutionStatus.RUNNING)
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(executionTraceRepository.save(any(ExecutionTrace.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(traceService.createExecutionTrace(request))
                .assertNext(trace -> {
                    assertThat(trace.getExecutionId()).isEqualTo(executionId);
                    assertThat(trace.getStatus()).isEqualTo(ExecutionTrace.ExecutionStatus.RUNNING);
                })
                .verifyComplete();

        verify(executionTraceRepository).save(any(ExecutionTrace.class));
    }

    @Test
    void shouldGetExecutionTrace() {
        UUID executionId = UUID.randomUUID();
        ExecutionTrace trace = ExecutionTrace.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .status(ExecutionTrace.ExecutionStatus.COMPLETED)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(executionTraceRepository.findByExecutionId(executionId)).thenReturn(Mono.just(trace));

        StepVerifier.create(traceService.getExecutionTrace(executionId))
                .assertNext(found -> assertThat(found.getExecutionId()).isEqualTo(executionId))
                .verifyComplete();

        verify(executionTraceRepository).findByExecutionId(executionId);
    }

    @Test
    void shouldListExecutionTracesByTenant() {
        UUID tenantId = UUID.randomUUID();
        ExecutionTrace trace1 = ExecutionTrace.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .status(ExecutionTrace.ExecutionStatus.COMPLETED)
                .startedAt(OffsetDateTime.now().minusMinutes(10))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        ExecutionTrace trace2 = ExecutionTrace.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .status(ExecutionTrace.ExecutionStatus.FAILED)
                .startedAt(OffsetDateTime.now().minusMinutes(5))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        when(executionTraceRepository.findByTenantIdOrderByStartedAtDesc(tenantId))
                .thenReturn(Flux.just(trace1, trace2));

        StepVerifier.create(traceService.listExecutionTraces(tenantId, 10))
                .expectNextCount(2)
                .verifyComplete();

        verify(executionTraceRepository).findByTenantIdOrderByStartedAtDesc(tenantId);
    }

    @Test
    void shouldListNodeTraces() {
        UUID executionTraceId = UUID.randomUUID();
        NodeExecutionTrace node1 = NodeExecutionTrace.builder()
                .id(UUID.randomUUID()).executionTraceId(executionTraceId)
                .nodeId("input").status(NodeExecutionTrace.NodeStatus.COMPLETED)
                .startedAt(OffsetDateTime.now()).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        NodeExecutionTrace node2 = NodeExecutionTrace.builder()
                .id(UUID.randomUUID()).executionTraceId(executionTraceId)
                .nodeId("output").status(NodeExecutionTrace.NodeStatus.COMPLETED)
                .startedAt(OffsetDateTime.now()).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        when(nodeExecutionTraceRepository.findByExecutionTraceIdOrderByStartedAtAsc(executionTraceId))
                .thenReturn(Flux.just(node1, node2));

        StepVerifier.create(traceService.listNodeTraces(executionTraceId))
                .expectNextCount(2)
                .verifyComplete();

        verify(nodeExecutionTraceRepository).findByExecutionTraceIdOrderByStartedAtAsc(executionTraceId);
    }

    @Test
    void shouldCreateToolExecutionTrace() {
        UUID tenantId = UUID.randomUUID();

        CreateToolExecutionTraceRequest request = new CreateToolExecutionTraceRequest(
            tenantId, null, null, "document_search", null, "HTTP", "SUCCESS",
            OffsetDateTime.now(), OffsetDateTime.now(), 150L, null, null, null, 1, 3
        );

        ToolExecutionTrace saved = ToolExecutionTrace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .skillSlug("document_search")
                .toolType("HTTP")
                .status("SUCCESS")
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();

        when(toolExecutionTraceRepository.save(any(ToolExecutionTrace.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(traceService.createToolExecutionTrace(request))
                .assertNext(trace -> {
                    assertThat(trace.getSkillSlug()).isEqualTo("document_search");
                    assertThat(trace.getToolType()).isEqualTo("HTTP");
                })
                .verifyComplete();

        verify(toolExecutionTraceRepository).save(any(ToolExecutionTrace.class));
    }

    @Test
    void shouldCountExecutionsByStatus() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime since = OffsetDateTime.now().minusHours(1);

        when(executionTraceRepository.countByStatusSince(tenantId, "COMPLETED", since))
                .thenReturn(Mono.just(42L));

        StepVerifier.create(traceService.countExecutionsByStatus(tenantId, "COMPLETED", since))
                .assertNext(count -> assertThat(count).isEqualTo(42L))
                .verifyComplete();

        verify(executionTraceRepository).countByStatusSince(tenantId, "COMPLETED", since);
    }

    @Test
    void shouldReturnEmptyWhenExecutionTraceNotFound() {
        UUID executionId = UUID.randomUUID();
        when(executionTraceRepository.findByExecutionId(executionId)).thenReturn(Mono.empty());

        StepVerifier.create(traceService.getExecutionTrace(executionId))
                .verifyComplete();
    }
}