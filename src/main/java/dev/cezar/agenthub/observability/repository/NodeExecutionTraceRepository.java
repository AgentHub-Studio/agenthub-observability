package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.NodeExecutionTrace;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repository for NodeExecutionTrace.
 *
 * @since 1.0.0
 */
@Repository
public interface NodeExecutionTraceRepository extends ReactiveCrudRepository<NodeExecutionTrace, UUID> {

    /**
     * Finds node traces for an execution ordered by start time ascending.
     */
    @Query("SELECT * FROM node_execution_traces WHERE execution_trace_id = :executionTraceId ORDER BY started_at ASC")
    Flux<NodeExecutionTrace> findByExecutionTraceIdOrderByStartedAtAsc(UUID executionTraceId);

    /**
     * Finds node traces by tenant and node type ordered by start time descending.
     */
    @Query("SELECT * FROM node_execution_traces WHERE tenant_id = :tenantId AND node_type = :nodeType ORDER BY started_at DESC")
    Flux<NodeExecutionTrace> findByTenantIdAndNodeTypeOrderByStartedAtDesc(UUID tenantId, String nodeType);

    /**
     * Finds a node trace by execution_id and node_id.
     */
    @Query("SELECT * FROM node_execution_traces WHERE execution_id = :executionId AND node_id = :nodeId LIMIT 1")
    Mono<NodeExecutionTrace> findByExecutionIdAndNodeId(UUID executionId, String nodeId);
}
