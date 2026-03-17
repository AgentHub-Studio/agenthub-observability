package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.ExecutionTrace;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Repository for ExecutionTrace.
 *
 * @since 1.0.0
 */
@Repository
public interface ExecutionTraceRepository extends ReactiveCrudRepository<ExecutionTrace, UUID> {

    /**
     * Finds traces by execution_id.
     */
    @Query("SELECT * FROM execution_traces WHERE execution_id = :executionId LIMIT 1")
    Mono<ExecutionTrace> findByExecutionId(UUID executionId);

    /**
     * Finds traces by tenant ordered by start time descending.
     */
    @Query("SELECT * FROM execution_traces WHERE tenant_id = :tenantId ORDER BY started_at DESC")
    Flux<ExecutionTrace> findByTenantIdOrderByStartedAtDesc(UUID tenantId);

    /**
     * Finds traces by tenant and agent ordered by start time descending.
     */
    @Query("SELECT * FROM execution_traces WHERE tenant_id = :tenantId AND agent_id = :agentId ORDER BY started_at DESC")
    Flux<ExecutionTrace> findByTenantIdAndAgentIdOrderByStartedAtDesc(UUID tenantId, UUID agentId);

    /**
     * Finds traces by tenant and status ordered by start time descending.
     */
    @Query("SELECT * FROM execution_traces WHERE tenant_id = :tenantId AND status = :status ORDER BY started_at DESC")
    Flux<ExecutionTrace> findByTenantIdAndStatusOrderByStartedAtDesc(UUID tenantId, String status);

    /**
     * Finds traces for a specific time period.
     */
    @Query("SELECT * FROM execution_traces WHERE tenant_id = :tenantId AND started_at >= :startDate AND started_at < :endDate ORDER BY started_at DESC LIMIT :limit")
    Flux<ExecutionTrace> findByPeriod(UUID tenantId, OffsetDateTime startDate, OffsetDateTime endDate, int limit);

    /**
     * Counts executions by status since a given time.
     */
    @Query("SELECT count() FROM execution_traces WHERE tenant_id = :tenantId AND status = :status AND started_at >= :since")
    Mono<Long> countByStatusSince(UUID tenantId, String status, OffsetDateTime since);
}
