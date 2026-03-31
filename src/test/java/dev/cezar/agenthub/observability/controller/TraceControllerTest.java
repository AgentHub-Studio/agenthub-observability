package dev.cezar.agenthub.observability.controller;

import dev.cezar.agenthub.observability.api.CreateExecutionTraceRequest;
import dev.cezar.agenthub.observability.api.CreateToolExecutionTraceRequest;
import dev.cezar.agenthub.observability.api.UpdateExecutionTraceRequest;
import dev.cezar.agenthub.observability.domain.ExecutionTrace;
import dev.cezar.agenthub.observability.domain.NodeExecutionTrace;
import dev.cezar.agenthub.observability.domain.ToolExecutionTrace;
import dev.cezar.agenthub.observability.service.TraceService;
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
 * Unit tests for TraceController.
 */
@ExtendWith(MockitoExtension.class)
class TraceControllerTest {

    @Mock
    private TraceService traceService;

    private TraceController controller;

    @BeforeEach
    void setUp() {
        controller = new TraceController(traceService);
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

        ExecutionTrace saved = buildExecutionTrace(tenantId, executionId, agentId, "RUNNING");
        when(traceService.createExecutionTrace(any(CreateExecutionTraceRequest.class)))
                .thenReturn(Mono.just(saved));

        StepVerifier.create(controller.createExecutionTrace(request))
                .assertNext(trace -> {
                    assertThat(trace.getExecutionId()).isEqualTo(executionId);
                    assertThat(trace.getStatus()).isEqualTo(ExecutionTrace.ExecutionStatus.RUNNING);
                })
                .verifyComplete();

        verify(traceService).createExecutionTrace(request);
    }

    @Test
    void shouldGetExecutionTraceById() {
        UUID executionId = UUID.randomUUID();
        ExecutionTrace trace = buildExecutionTrace(UUID.randomUUID(), executionId, UUID.randomUUID(), "COMPLETED");

        when(traceService.getExecutionTrace(executionId)).thenReturn(Mono.just(trace));

        StepVerifier.create(controller.getExecutionTrace(executionId))
                .assertNext(t -> {
                    assertThat(t.getExecutionId()).isEqualTo(executionId);
                    assertThat(t.getStatus()).isEqualTo(ExecutionTrace.ExecutionStatus.COMPLETED);
                })
                .verifyComplete();

        verify(traceService).getExecutionTrace(executionId);
    }

    @Test
    void shouldListExecutionTracesByTenant() {
        UUID tenantId = UUID.randomUUID();
        ExecutionTrace trace1 = buildExecutionTrace(tenantId, UUID.randomUUID(), UUID.randomUUID(), "COMPLETED");
        ExecutionTrace trace2 = buildExecutionTrace(tenantId, UUID.randomUUID(), UUID.randomUUID(), "FAILED");

        when(traceService.listExecutionTraces(tenantId, 100)).thenReturn(Flux.just(trace1, trace2));

        StepVerifier.create(controller.listExecutionTraces(tenantId, 100))
                .expectNextCount(2)
                .verifyComplete();

        verify(traceService).listExecutionTraces(tenantId, 100);
    }

    @Test
    void shouldListNodeTracesForExecution() {
        UUID executionTraceId = UUID.randomUUID();
        NodeExecutionTrace node1 = NodeExecutionTrace.builder()
                .id(UUID.randomUUID())
                .executionTraceId(executionTraceId)
                .nodeId("input-node")
                .status(NodeExecutionTrace.NodeStatus.COMPLETED)
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        NodeExecutionTrace node2 = NodeExecutionTrace.builder()
                .id(UUID.randomUUID())
                .executionTraceId(executionTraceId)
                .nodeId("output-node")
                .status(NodeExecutionTrace.NodeStatus.COMPLETED)
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(traceService.listNodeTraces(executionTraceId)).thenReturn(Flux.just(node1, node2));

        StepVerifier.create(controller.listNodeTraces(executionTraceId))
                .assertNext(n -> assertThat(n.getNodeId()).isEqualTo("input-node"))
                .assertNext(n -> assertThat(n.getNodeId()).isEqualTo("output-node"))
                .verifyComplete();

        verify(traceService).listNodeTraces(executionTraceId);
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

        when(traceService.createToolExecutionTrace(any(CreateToolExecutionTraceRequest.class)))
                .thenReturn(Mono.just(saved));

        StepVerifier.create(controller.createToolExecutionTrace(request))
                .assertNext(trace -> {
                    assertThat(trace.getSkillSlug()).isEqualTo("document_search");
                    assertThat(trace.getToolType()).isEqualTo("HTTP");
                    assertThat(trace.getStatus()).isEqualTo("SUCCESS");
                })
                .verifyComplete();

        verify(traceService).createToolExecutionTrace(request);
    }

    @Test
    void shouldCountExecutionsByStatus() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime since = OffsetDateTime.now().minusHours(24);

        when(traceService.countExecutionsByStatus(tenantId, "COMPLETED", since))
                .thenReturn(Mono.just(17L));

        StepVerifier.create(controller.countExecutionsByStatus(tenantId, "COMPLETED", since))
                .assertNext(count -> assertThat(count).isEqualTo(17L))
                .verifyComplete();

        verify(traceService).countExecutionsByStatus(tenantId, "COMPLETED", since);
    }

    @Test
    void shouldListToolTracesBySkill() {
        UUID tenantId = UUID.randomUUID();
        ToolExecutionTrace trace = ToolExecutionTrace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .skillSlug("document_search")
                .toolType("DOCUMENT_SEARCH")
                .status("SUCCESS")
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();

        when(traceService.listToolTracesBySkill(tenantId, "document_search", 100))
                .thenReturn(Flux.just(trace));

        StepVerifier.create(controller.listToolTracesBySkill(tenantId, "document_search", 100))
                .assertNext(t -> assertThat(t.getSkillSlug()).isEqualTo("document_search"))
                .verifyComplete();

        verify(traceService).listToolTracesBySkill(tenantId, "document_search", 100);
    }

    @Test
    void shouldListExecutionTracesByAgent() {
        UUID tenantId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        ExecutionTrace trace = buildExecutionTrace(tenantId, UUID.randomUUID(), agentId, "COMPLETED");

        when(traceService.listExecutionTracesByAgent(tenantId, agentId, 50))
                .thenReturn(Flux.just(trace));

        StepVerifier.create(controller.listExecutionTracesByAgent(tenantId, agentId, 50))
                .assertNext(t -> assertThat(t.getAgentId()).isEqualTo(agentId))
                .verifyComplete();

        verify(traceService).listExecutionTracesByAgent(tenantId, agentId, 50);
    }

    // Helpers

    private ExecutionTrace buildExecutionTrace(UUID tenantId, UUID executionId, UUID agentId, String status) {
        return ExecutionTrace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .executionId(executionId)
                .agentId(agentId)
                .status(ExecutionTrace.ExecutionStatus.valueOf(status))
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
