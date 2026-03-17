package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.ToolExecutionTrace;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Repository for ToolExecutionTrace.
 *
 * @since 1.0.0
 */
@Repository
public interface ToolExecutionTraceRepository extends ReactiveCrudRepository<ToolExecutionTrace, UUID> {

    /**
     * Finds tool traces by tenant and skill slug ordered by start time descending.
     */
    @Query("SELECT * FROM tool_execution_traces WHERE tenant_id = :tenantId AND skill_slug = :skillSlug ORDER BY started_at DESC")
    Flux<ToolExecutionTrace> findByTenantIdAndSkillSlugOrderByStartedAtDesc(UUID tenantId, String skillSlug);

    /**
     * Finds tool traces by tenant and tool type ordered by start time descending.
     */
    @Query("SELECT * FROM tool_execution_traces WHERE tenant_id = :tenantId AND tool_type = :toolType ORDER BY started_at DESC")
    Flux<ToolExecutionTrace> findByTenantIdAndToolTypeOrderByStartedAtDesc(UUID tenantId, String toolType);

    /**
     * Returns performance statistics per skill.
     * Uses ClickHouse aggregate functions.
     */
    @Query("""
        SELECT
            skill_slug,
            tool_type,
            status,
            count() as invocation_count,
            avg(duration_ms) as avg_duration_ms,
            min(duration_ms) as min_duration_ms,
            max(duration_ms) as max_duration_ms
        FROM tool_execution_traces
        WHERE tenant_id = :tenantId
          AND started_at >= :since
        GROUP BY skill_slug, tool_type, status
        """)
    Flux<Object> getPerformanceStats(UUID tenantId, OffsetDateTime since);
}
