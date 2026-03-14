package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.NodeExecutionTrace;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Repository para NodeExecutionTrace.
 *
 * @since 1.0.0
 */
@Repository
public interface NodeExecutionTraceRepository extends R2dbcRepository<NodeExecutionTrace, UUID> {

    /**
     * Busca nodes por execution trace.
     */
    Flux<NodeExecutionTrace> findByExecutionTraceIdOrderByStartedAtAsc(UUID executionTraceId);

    /**
     * Busca nodes por tipo.
     */
    Flux<NodeExecutionTrace> findByTenantIdAndNodeTypeOrderByStartedAtDesc(UUID tenantId, String nodeType);
}
