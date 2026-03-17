package dev.cezar.agenthub.observability.event;

import dev.cezar.agenthub.observability.domain.ExecutionTrace;
import dev.cezar.agenthub.observability.domain.NodeExecutionTrace;
import dev.cezar.agenthub.observability.repository.ExecutionTraceRepository;
import dev.cezar.agenthub.observability.repository.NodeExecutionTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

/**
 * Listens to orchestrator events from RabbitMQ and creates/updates traces.
 * 
 * <p>Consumes events from the orchestrator exchange and automatically creates
 * execution and node traces in the observability database.
 * 
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agenthub.observability.rabbitmq.enabled", havingValue = "true")
public class OrchestratorEventListener {

    private final ExecutionTraceRepository executionTraceRepository;
    private final NodeExecutionTraceRepository nodeExecutionTraceRepository;

    // ========== Execution Events ==========

    /**
     * Handle execution.queued event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "execution.queued")
    public void onExecutionQueued(ExecutionEventDto event) {
        log.info("Received execution.queued event: executionId={}, tenantId={}", 
                event.executionId(), event.tenantId());
        
        // Create execution trace with RUNNING status (queued means it will start soon)
        createExecutionTrace(event, ExecutionTrace.ExecutionStatus.RUNNING)
                .contextWrite(ctx -> ctx.put("tenantId", event.tenantId().toString()).put("schema", "agenthub"))
                .subscribe(
                    trace -> log.debug("Created execution trace for queued event: {}", trace.getId()),
                    error -> log.error("Failed to create execution trace for queued event", error)
                );
    }

    /**
     * Handle execution.started event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "execution.started")
    public void onExecutionStarted(ExecutionEventDto event) {
        log.info("Received execution.started event: executionId={}, tenantId={}", 
                event.executionId(), event.tenantId());
        
        updateExecutionStatus(event.executionId(), ExecutionTrace.ExecutionStatus.RUNNING)
                .contextWrite(ctx -> ctx.put("tenantId", event.tenantId().toString()).put("schema", "agenthub"))
                .subscribe(
                    trace -> log.debug("Updated execution trace status to RUNNING: {}", trace.getId()),
                    error -> log.error("Failed to update execution trace for started event", error)
                );
    }

    /**
     * Handle execution.completed event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "execution.completed")
    public void onExecutionCompleted(ExecutionEventDto event) {
        log.info("Received execution.completed event: executionId={}, tenantId={}", 
                event.executionId(), event.tenantId());
        
        completeExecutionTrace(event.executionId(), ExecutionTrace.ExecutionStatus.COMPLETED)
                .contextWrite(ctx -> ctx.put("tenantId", event.tenantId().toString()).put("schema", "agenthub"))
                .subscribe(
                    trace -> log.debug("Completed execution trace: {}", trace.getId()),
                    error -> log.error("Failed to complete execution trace", error)
                );
    }

    /**
     * Handle execution.failed event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "execution.failed")
    public void onExecutionFailed(ExecutionEventDto event) {
        log.info("Received execution.failed event: executionId={}, tenantId={}", 
                event.executionId(), event.tenantId());
        
        completeExecutionTrace(event.executionId(), ExecutionTrace.ExecutionStatus.FAILED)
                .contextWrite(ctx -> ctx.put("tenantId", event.tenantId().toString()).put("schema", "agenthub"))
                .subscribe(
                    trace -> log.debug("Failed execution trace: {}", trace.getId()),
                    error -> log.error("Failed to update execution trace for failed event", error)
                );
    }

    /**
     * Handle execution.cancelled event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "execution.cancelled")
    public void onExecutionCancelled(ExecutionEventDto event) {
        log.info("Received execution.cancelled event: executionId={}, tenantId={}", 
                event.executionId(), event.tenantId());
        
        completeExecutionTrace(event.executionId(), ExecutionTrace.ExecutionStatus.CANCELLED)
                .contextWrite(ctx -> ctx.put("tenantId", event.tenantId().toString()).put("schema", "agenthub"))
                .subscribe(
                    trace -> log.debug("Cancelled execution trace: {}", trace.getId()),
                    error -> log.error("Failed to update execution trace for cancelled event", error)
                );
    }

    /**
     * Handle execution.timed_out event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "execution.timed_out")
    public void onExecutionTimedOut(ExecutionEventDto event) {
        log.info("Received execution.timed_out event: executionId={}, tenantId={}", 
                event.executionId(), event.tenantId());
        
        completeExecutionTrace(event.executionId(), ExecutionTrace.ExecutionStatus.FAILED)
                .contextWrite(ctx -> ctx.put("tenantId", event.tenantId().toString()).put("schema", "agenthub"))
                .subscribe(
                    trace -> log.debug("Timed out execution trace: {}", trace.getId()),
                    error -> log.error("Failed to update execution trace for timeout event", error)
                );
    }

    // ========== Node Events ==========

    /**
     * Handle node.started event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "node.started")
    public void onNodeStarted(NodeEventDto event) {
        log.info("Received node.started event: executionId={}, nodeId={}", 
                event.executionId(), event.nodeId());
        
        createNodeTrace(event, NodeExecutionTrace.NodeStatus.RUNNING)
                .subscribe(
                    trace -> log.debug("Created node trace: {}", trace.getId()),
                    error -> log.error("Failed to create node trace for started event", error)
                );
    }

    /**
     * Handle node.completed event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "node.completed")
    public void onNodeCompleted(NodeEventDto event) {
        log.info("Received node.completed event: executionId={}, nodeId={}", 
                event.executionId(), event.nodeId());
        
        completeNodeTrace(event.executionId(), event.nodeId(), NodeExecutionTrace.NodeStatus.COMPLETED)
                .subscribe(
                    trace -> log.debug("Completed node trace: {}", trace.getId()),
                    error -> log.error("Failed to complete node trace", error)
                );
    }

    /**
     * Handle node.failed event.
     */
    @RabbitListener(queues = "${agenthub.observability.rabbitmq.queue}", id = "node.failed")
    public void onNodeFailed(NodeEventDto event) {
        log.info("Received node.failed event: executionId={}, nodeId={}", 
                event.executionId(), event.nodeId());
        
        completeNodeTrace(event.executionId(), event.nodeId(), NodeExecutionTrace.NodeStatus.FAILED)
                .subscribe(
                    trace -> log.debug("Failed node trace: {}", trace.getId()),
                    error -> log.error("Failed to update node trace for failed event", error)
                );
    }

    // ========== Helper Methods ==========

    private Mono<ExecutionTrace> createExecutionTrace(ExecutionEventDto event, ExecutionTrace.ExecutionStatus status) {
        ExecutionTrace trace = new ExecutionTrace();
        trace.setTenantId(event.tenantId());
        trace.setExecutionId(event.executionId());
        trace.setStatus(status);
        trace.setStartedAt(OffsetDateTime.now());
        trace.setCreatedAt(OffsetDateTime.now());
        trace.setUpdatedAt(OffsetDateTime.now());
        
        return executionTraceRepository.save(trace);
    }

    private Mono<ExecutionTrace> updateExecutionStatus(java.util.UUID executionId, ExecutionTrace.ExecutionStatus status) {
        return executionTraceRepository.findByExecutionId(executionId)
                .flatMap(trace -> {
                    trace.setStatus(status);
                    trace.setUpdatedAt(OffsetDateTime.now());
                    return executionTraceRepository.save(trace);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Execution trace not found for executionId: {}", executionId);
                    return Mono.empty();
                }));
    }

    private Mono<ExecutionTrace> completeExecutionTrace(java.util.UUID executionId, ExecutionTrace.ExecutionStatus status) {
        return executionTraceRepository.findByExecutionId(executionId)
                .flatMap(trace -> {
                    trace.setStatus(status);
                    trace.setCompletedAt(OffsetDateTime.now());
                    
                    if (trace.getStartedAt() != null) {
                        long durationMs = java.time.Duration.between(trace.getStartedAt(), trace.getCompletedAt()).toMillis();
                        trace.setDurationMs(durationMs);
                    }
                    
                    trace.setUpdatedAt(OffsetDateTime.now());
                    return executionTraceRepository.save(trace);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Execution trace not found for executionId: {}", executionId);
                    return Mono.empty();
                }));
    }

    private Mono<NodeExecutionTrace> createNodeTrace(NodeEventDto event, NodeExecutionTrace.NodeStatus status) {
        NodeExecutionTrace trace = new NodeExecutionTrace();
        trace.setExecutionId(event.executionId());
        trace.setNodeId(event.nodeId());
        trace.setStatus(status);
        trace.setStartedAt(OffsetDateTime.now());
        trace.setCreatedAt(OffsetDateTime.now());
        trace.setUpdatedAt(OffsetDateTime.now());
        
        return nodeExecutionTraceRepository.save(trace);
    }

    private Mono<NodeExecutionTrace> completeNodeTrace(
            java.util.UUID executionId, 
            String nodeId, 
            NodeExecutionTrace.NodeStatus status) {
        
        return nodeExecutionTraceRepository.findByExecutionIdAndNodeId(executionId, nodeId)
                .flatMap(trace -> {
                    trace.setStatus(status);
                    trace.setCompletedAt(OffsetDateTime.now());
                    
                    if (trace.getStartedAt() != null) {
                        long durationMs = java.time.Duration.between(trace.getStartedAt(), trace.getCompletedAt()).toMillis();
                        trace.setDurationMs(durationMs);
                    }
                    
                    trace.setUpdatedAt(OffsetDateTime.now());
                    return nodeExecutionTraceRepository.save(trace);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Node trace not found for executionId: {}, nodeId: {}", executionId, nodeId);
                    return Mono.empty();
                }));
    }
}
