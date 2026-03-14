package dev.cezar.agenthub.observability.controller;

import dev.cezar.agenthub.observability.api.CreateExecutionTraceRequest;
import dev.cezar.agenthub.observability.api.CreateToolExecutionTraceRequest;
import dev.cezar.agenthub.observability.api.UpdateExecutionTraceRequest;
import dev.cezar.agenthub.observability.domain.ExecutionTrace;
import dev.cezar.agenthub.observability.domain.NodeExecutionTrace;
import dev.cezar.agenthub.observability.domain.ToolExecutionTrace;
import dev.cezar.agenthub.observability.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Controller REST para traces de execução.
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/traces")
@RequiredArgsConstructor
@Tag(name = "Traces", description = "Execution trace collection and retrieval")
public class TraceController {

    private final TraceService traceService;

    // =============================================================================
    // EXECUTION TRACES
    // =============================================================================

    @PostMapping("/executions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create execution trace", description = "Records a new agent execution trace")
    public Mono<ExecutionTrace> createExecutionTrace(@Valid @RequestBody CreateExecutionTraceRequest request) {
        return traceService.createExecutionTrace(request);
    }

    @PutMapping("/executions/{executionId}")
    @Operation(summary = "Update execution trace", description = "Updates an existing execution trace (complete execution)")
    public Mono<ExecutionTrace> updateExecutionTrace(
            @PathVariable UUID executionId,
            @Valid @RequestBody UpdateExecutionTraceRequest request) {
        return traceService.updateExecutionTrace(executionId, request);
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "Get execution trace", description = "Retrieves execution trace by execution ID")
    public Mono<ExecutionTrace> getExecutionTrace(@PathVariable UUID executionId) {
        return traceService.getExecutionTrace(executionId);
    }

    @GetMapping("/executions")
    @Operation(summary = "List execution traces", description = "Lists execution traces by tenant")
    public Flux<ExecutionTrace> listExecutionTraces(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "100") int limit) {
        return traceService.listExecutionTraces(tenantId, limit);
    }

    @GetMapping("/executions/by-agent")
    @Operation(summary = "List execution traces by agent", description = "Lists execution traces filtered by agent")
    public Flux<ExecutionTrace> listExecutionTracesByAgent(
            @RequestParam UUID tenantId,
            @RequestParam UUID agentId,
            @RequestParam(defaultValue = "100") int limit) {
        return traceService.listExecutionTracesByAgent(tenantId, agentId, limit);
    }

    @GetMapping("/executions/by-period")
    @Operation(summary = "List execution traces by period", description = "Lists execution traces in date range")
    public Flux<ExecutionTrace> listExecutionTracesByPeriod(
            @RequestParam UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "100") int limit) {
        return traceService.listExecutionTracesByPeriod(tenantId, startDate, endDate, limit);
    }

    // =============================================================================
    // NODE EXECUTION TRACES
    // =============================================================================

    @GetMapping("/executions/{executionTraceId}/nodes")
    @Operation(summary = "List node traces", description = "Lists node execution traces for an execution")
    public Flux<NodeExecutionTrace> listNodeTraces(@PathVariable UUID executionTraceId) {
        return traceService.listNodeTraces(executionTraceId);
    }

    // =============================================================================
    // TOOL EXECUTION TRACES
    // =============================================================================

    @PostMapping("/tools")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create tool execution trace", description = "Records a new tool execution trace")
    public Mono<ToolExecutionTrace> createToolExecutionTrace(@Valid @RequestBody CreateToolExecutionTraceRequest request) {
        return traceService.createToolExecutionTrace(request);
    }

    @GetMapping("/tools/by-skill")
    @Operation(summary = "List tool traces by skill", description = "Lists tool execution traces filtered by skill slug")
    public Flux<ToolExecutionTrace> listToolTracesBySkill(
            @RequestParam UUID tenantId,
            @RequestParam String skillSlug,
            @RequestParam(defaultValue = "100") int limit) {
        return traceService.listToolTracesBySkill(tenantId, skillSlug, limit);
    }

    @GetMapping("/tools/by-type")
    @Operation(summary = "List tool traces by type", description = "Lists tool execution traces filtered by tool type")
    public Flux<ToolExecutionTrace> listToolTracesByType(
            @RequestParam UUID tenantId,
            @RequestParam String toolType,
            @RequestParam(defaultValue = "100") int limit) {
        return traceService.listToolTracesByType(tenantId, toolType, limit);
    }

    // =============================================================================
    // STATISTICS
    // =============================================================================

    @GetMapping("/stats/executions/count")
    @Operation(summary = "Count executions by status", description = "Counts executions by status since a date")
    public Mono<Long> countExecutionsByStatus(
            @RequestParam UUID tenantId,
            @RequestParam String status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since) {
        return traceService.countExecutionsByStatus(tenantId, status, since);
    }
}
