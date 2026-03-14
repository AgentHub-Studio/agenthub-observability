package dev.cezar.agenthub.observability.service;

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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        // Given
        ExecutionTrace trace = createExecutionTrace();
        when(executionTraceRepository.save(any(ExecutionTrace.class)))
            .thenReturn(Mono.just(trace));

        // When & Then
        StepVerifier.create(traceService.createExecutionTrace(trace))
            .assertNext(saved -> {
                assertThat(saved.getExecutionId()).isEqualTo(trace.getExecutionId());
                assertThat(saved.getTenantId()).isEqualTo(1L);
                assertThat(saved.getStatus()).isEqualTo("RUNNING");
            })
            .verifyComplete();

        verify(executionTraceRepository).save(any(ExecutionTrace.class));
    }

    @Test
    void shouldGetExecutionTraceByExecutionId() {
        // Given
        String executionId = UUID.randomUUID().toString();
        ExecutionTrace trace = createExecutionTrace();
        when(executionTraceRepository.findByExecutionId(executionId))
            .thenReturn(Mono.just(trace));

        // When & Then
        StepVerifier.create(traceService.getExecutionTrace(executionId))
            .assertNext(found -> assertThat(found.getExecutionId()).isEqualTo(trace.getExecutionId()))
            .verifyComplete();

        verify(executionTraceRepository).findByExecutionId(executionId);
    }

    @Test
    void shouldUpdateExecutionTrace() {
        // Given
        String executionId = UUID.randomUUID().toString();
        ExecutionTrace existing = createExecutionTrace();
        existing.setStatus("RUNNING");
        
        ExecutionTrace updated = createExecutionTrace();
        updated.setStatus("COMPLETED");
        updated.setEndTime(Instant.now());
        updated.setOutputData(Map.of("result", "success"));

        when(executionTraceRepository.findByExecutionId(executionId))
            .thenReturn(Mono.just(existing));
        when(executionTraceRepository.save(any(ExecutionTrace.class)))
            .thenReturn(Mono.just(updated));

        // When & Then
        StepVerifier.create(traceService.updateExecutionTrace(executionId, updated))
            .assertNext(result -> {
                assertThat(result.getStatus()).isEqualTo("COMPLETED");
                assertThat(result.getEndTime()).isNotNull();
                assertThat(result.getOutputData()).containsEntry("result", "success");
            })
            .verifyComplete();

        verify(executionTraceRepository).findByExecutionId(executionId);
        verify(executionTraceRepository).save(any(ExecutionTrace.class));
    }

    @Test
    void shouldListExecutionTracesByTenant() {
        // Given
        Long tenantId = 1L;
        ExecutionTrace trace1 = createExecutionTrace();
        ExecutionTrace trace2 = createExecutionTrace();
        when(executionTraceRepository.findByTenantId(tenantId))
            .thenReturn(Flux.just(trace1, trace2));

        // When & Then
        StepVerifier.create(traceService.listExecutionTraces(tenantId))
            .expectNextCount(2)
            .verifyComplete();

        verify(executionTraceRepository).findByTenantId(tenantId);
    }

    @Test
    void shouldCreateNodeExecutionTrace() {
        // Given
        NodeExecutionTrace trace = createNodeTrace();
        when(nodeExecutionTraceRepository.save(any(NodeExecutionTrace.class)))
            .thenReturn(Mono.just(trace));

        // When & Then
        StepVerifier.create(traceService.createNodeExecutionTrace(trace))
            .assertNext(saved -> {
                assertThat(saved.getExecutionId()).isEqualTo(trace.getExecutionId());
                assertThat(saved.getNodeId()).isEqualTo(trace.getNodeId());
            })
            .verifyComplete();

        verify(nodeExecutionTraceRepository).save(any(NodeExecutionTrace.class));
    }

    @Test
    void shouldGetNodeTracesByExecutionId() {
        // Given
        String executionId = UUID.randomUUID().toString();
        NodeExecutionTrace trace1 = createNodeTrace();
        NodeExecutionTrace trace2 = createNodeTrace();
        when(nodeExecutionTraceRepository.findByExecutionId(executionId))
            .thenReturn(Flux.just(trace1, trace2));

        // When & Then
        StepVerifier.create(traceService.getNodeTraces(executionId))
            .expectNextCount(2)
            .verifyComplete();

        verify(nodeExecutionTraceRepository).findByExecutionId(executionId);
    }

    @Test
    void shouldCreateToolExecutionTrace() {
        // Given
        ToolExecutionTrace trace = createToolTrace();
        when(toolExecutionTraceRepository.save(any(ToolExecutionTrace.class)))
            .thenReturn(Mono.just(trace));

        // When & Then
        StepVerifier.create(traceService.createToolExecutionTrace(trace))
            .assertNext(saved -> {
                assertThat(saved.getToolName()).isEqualTo(trace.getToolName());
                assertThat(saved.getExecutionId()).isEqualTo(trace.getExecutionId());
            })
            .verifyComplete();

        verify(toolExecutionTraceRepository).save(any(ToolExecutionTrace.class));
    }

    @Test
    void shouldGetToolTracesByExecutionId() {
        // Given
        String executionId = UUID.randomUUID().toString();
        ToolExecutionTrace trace1 = createToolTrace();
        ToolExecutionTrace trace2 = createToolTrace();
        when(toolExecutionTraceRepository.findByExecutionId(executionId))
            .thenReturn(Flux.just(trace1, trace2));

        // When & Then
        StepVerifier.create(traceService.getToolTraces(executionId))
            .expectNextCount(2)
            .verifyComplete();

        verify(toolExecutionTraceRepository).findByExecutionId(executionId);
    }

    @Test
    void shouldGetExecutionStats() {
        // Given
        Long tenantId = 1L;
        Long agentId = 100L;
        
        ExecutionTrace completed1 = createExecutionTrace();
        completed1.setStatus("COMPLETED");
        completed1.setDurationMs(1000L);
        
        ExecutionTrace completed2 = createExecutionTrace();
        completed2.setStatus("COMPLETED");
        completed2.setDurationMs(2000L);
        
        ExecutionTrace failed = createExecutionTrace();
        failed.setStatus("FAILED");
        failed.setDurationMs(500L);

        when(executionTraceRepository.findByTenantIdAndAgentId(tenantId, agentId))
            .thenReturn(Flux.just(completed1, completed2, failed));

        // When & Then
        StepVerifier.create(traceService.getExecutionStats(tenantId, agentId))
            .assertNext(stats -> {
                assertThat(stats).containsEntry("total", 3L);
                assertThat(stats).containsEntry("completed", 2L);
                assertThat(stats).containsEntry("failed", 1L);
                assertThat(stats).containsEntry("avgDurationMs", 1500.0); // (1000 + 2000) / 2
            })
            .verifyComplete();

        verify(executionTraceRepository).findByTenantIdAndAgentId(tenantId, agentId);
    }

    private ExecutionTrace createExecutionTrace() {
        ExecutionTrace trace = new ExecutionTrace();
        trace.setId(1L);
        trace.setExecutionId(UUID.randomUUID().toString());
        trace.setTenantId(1L);
        trace.setAgentId(100L);
        trace.setPipelineId(200L);
        trace.setStatus("RUNNING");
        trace.setStartTime(Instant.now());
        trace.setInputParameters(Map.of("param", "value"));
        return trace;
    }

    private NodeExecutionTrace createNodeTrace() {
        NodeExecutionTrace trace = new NodeExecutionTrace();
        trace.setId(1L);
        trace.setExecutionId(UUID.randomUUID().toString());
        trace.setNodeId("node-1");
        trace.setNodeType("TOOL");
        trace.setStatus("COMPLETED");
        trace.setStartTime(Instant.now());
        trace.setEndTime(Instant.now());
        return trace;
    }

    private ToolExecutionTrace createToolTrace() {
        ToolExecutionTrace trace = new ToolExecutionTrace();
        trace.setId(1L);
        trace.setExecutionId(UUID.randomUUID().toString());
        trace.setToolName("HTTP_CLIENT");
        trace.setStatus("SUCCESS");
        trace.setStartTime(Instant.now());
        trace.setEndTime(Instant.now());
        return trace;
    }
}
