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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service para gerenciar traces de execução.
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceService {

    private final ExecutionTraceRepository executionTraceRepository;
    private final NodeExecutionTraceRepository nodeExecutionTraceRepository;
    private final ToolExecutionTraceRepository toolExecutionTraceRepository;

    // =============================================================================
    // EXECUTION TRACES
    // =============================================================================

    /**
     * Cria execution trace.
     */
    public Mono<ExecutionTrace> createExecutionTrace(CreateExecutionTraceRequest request) {
        log.info("Creating execution trace: executionId={}, agentId={}",
                request.executionId(), request.agentId());

        ExecutionTrace trace = ExecutionTrace.builder()
                .tenantId(request.tenantId())
                .agentId(request.agentId())
                .agentVersionId(request.agentVersionId())
                .userId(request.userId())
                .executionId(request.executionId())
                .status(request.status())
                .startedAt(request.startedAt())
                .inputData(request.inputData())
                .triggerSource(request.triggerSource())
                .triggerMetadata(request.triggerMetadata())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        return executionTraceRepository.save(trace);
    }

    /**
     * Atualiza execution trace (completa execução).
     */
    public Mono<ExecutionTrace> updateExecutionTrace(UUID executionId, UpdateExecutionTraceRequest request) {
        log.info("Updating execution trace: executionId={}, status={}", executionId, request.status());

        return executionTraceRepository.findByExecutionId(executionId)
                .flatMap(trace -> {
                    if (request.status() != null) trace.setStatus(request.status());
                    if (request.completedAt() != null) trace.setCompletedAt(request.completedAt());
                    if (request.durationMs() != null) trace.setDurationMs(request.durationMs());
                    if (request.outputData() != null) trace.setOutputData(request.outputData());
                    if (request.errorMessage() != null) trace.setErrorMessage(request.errorMessage());
                    if (request.errorStackTrace() != null) trace.setErrorStackTrace(request.errorStackTrace());
                    trace.setUpdatedAt(OffsetDateTime.now());

                    return executionTraceRepository.save(trace);
                });
    }

    /**
     * Busca execution trace por ID.
     */
    public Mono<ExecutionTrace> getExecutionTrace(UUID executionId) {
        return executionTraceRepository.findByExecutionId(executionId);
    }

    /**
     * Lista execution traces por tenant.
     */
    public Flux<ExecutionTrace> listExecutionTraces(UUID tenantId, int limit) {
        return executionTraceRepository.findByTenantIdOrderByStartedAtDesc(tenantId)
                .take(limit);
    }

    /**
     * Lista execution traces por agent.
     */
    public Flux<ExecutionTrace> listExecutionTracesByAgent(UUID tenantId, UUID agentId, int limit) {
        return executionTraceRepository.findByTenantIdAndAgentIdOrderByStartedAtDesc(tenantId, agentId)
                .take(limit);
    }

    /**
     * Lista execution traces por período.
     */
    public Flux<ExecutionTrace> listExecutionTracesByPeriod(
            UUID tenantId,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            int limit) {
        return executionTraceRepository.findByPeriod(tenantId, startDate, endDate, limit);
    }

    // =============================================================================
    // NODE EXECUTION TRACES
    // =============================================================================

    /**
     * Lista node traces de uma execução.
     */
    public Flux<NodeExecutionTrace> listNodeTraces(UUID executionTraceId) {
        return nodeExecutionTraceRepository.findByExecutionTraceIdOrderByStartedAtAsc(executionTraceId);
    }

    // =============================================================================
    // TOOL EXECUTION TRACES
    // =============================================================================

    /**
     * Cria tool execution trace.
     */
    public Mono<ToolExecutionTrace> createToolExecutionTrace(CreateToolExecutionTraceRequest request) {
        log.info("Creating tool execution trace: skillSlug={}, toolType={}",
                request.skillSlug(), request.toolType());

        ToolExecutionTrace trace = ToolExecutionTrace.builder()
                .tenantId(request.tenantId())
                .nodeExecutionTraceId(request.nodeExecutionTraceId())
                .skillId(request.skillId())
                .skillSlug(request.skillSlug())
                .toolId(request.toolId())
                .toolType(request.toolType())
                .status(request.status())
                .startedAt(request.startedAt())
                .completedAt(request.completedAt())
                .durationMs(request.durationMs())
                .inputData(request.inputData())
                .outputData(request.outputData())
                .errorMessage(request.errorMessage())
                .attemptNumber(request.attemptNumber())
                .maxRetries(request.maxRetries())
                .createdAt(OffsetDateTime.now())
                .build();

        return toolExecutionTraceRepository.save(trace);
    }

    /**
     * Lista tool traces por skill slug.
     */
    public Flux<ToolExecutionTrace> listToolTracesBySkill(UUID tenantId, String skillSlug, int limit) {
        return toolExecutionTraceRepository.findByTenantIdAndSkillSlugOrderByStartedAtDesc(tenantId, skillSlug)
                .take(limit);
    }

    /**
     * Lista tool traces por tipo.
     */
    public Flux<ToolExecutionTrace> listToolTracesByType(UUID tenantId, String toolType, int limit) {
        return toolExecutionTraceRepository.findByTenantIdAndToolTypeOrderByStartedAtDesc(tenantId, toolType)
                .take(limit);
    }

    // =============================================================================
    // STATISTICS
    // =============================================================================

    /**
     * Conta execuções por status.
     */
    public Mono<Long> countExecutionsByStatus(UUID tenantId, String status, OffsetDateTime since) {
        return executionTraceRepository.countByStatusSince(tenantId, status, since);
    }
}
