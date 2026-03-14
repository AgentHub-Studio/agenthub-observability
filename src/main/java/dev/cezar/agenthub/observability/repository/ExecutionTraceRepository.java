package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.ExecutionTrace;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Repository para ExecutionTrace.
 *
 * @since 1.0.0
 */
@Repository
public interface ExecutionTraceRepository extends R2dbcRepository<ExecutionTrace, UUID> {

    /**
     * Busca traces por execution_id.
     */
    Mono<ExecutionTrace> findByExecutionId(UUID executionId);

    /**
     * Busca traces por tenant.
     */
    Flux<ExecutionTrace> findByTenantIdOrderByStartedAtDesc(UUID tenantId);

    /**
     * Busca traces por agent.
     */
    Flux<ExecutionTrace> findByTenantIdAndAgentIdOrderByStartedAtDesc(UUID tenantId, UUID agentId);

    /**
     * Busca traces por status.
     */
    Flux<ExecutionTrace> findByTenantIdAndStatusOrderByStartedAtDesc(UUID tenantId, String status);

    /**
     * Busca traces por período.
     */
    @Query("""
        SELECT * FROM execution_traces
        WHERE tenant_id = :tenantId
          AND started_at >= :startDate
          AND started_at < :endDate
        ORDER BY started_at DESC
        LIMIT :limit
        """)
    Flux<ExecutionTrace> findByPeriod(UUID tenantId, OffsetDateTime startDate, OffsetDateTime endDate, int limit);

    /**
     * Conta execuções por status.
     */
    @Query("""
        SELECT COUNT(*) FROM execution_traces
        WHERE tenant_id = :tenantId
          AND status = :status
          AND started_at >= :since
        """)
    Mono<Long> countByStatusSince(UUID tenantId, String status, OffsetDateTime since);
}
