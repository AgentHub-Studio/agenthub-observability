package dev.cezar.agenthub.observability.repository;

import dev.cezar.agenthub.observability.domain.ToolExecutionTrace;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Repository para ToolExecutionTrace.
 *
 * @since 1.0.0
 */
@Repository
public interface ToolExecutionTraceRepository extends R2dbcRepository<ToolExecutionTrace, UUID> {

    /**
     * Busca tools por skill slug.
     */
    Flux<ToolExecutionTrace> findByTenantIdAndSkillSlugOrderByStartedAtDesc(UUID tenantId, String skillSlug);

    /**
     * Busca tools por tipo.
     */
    Flux<ToolExecutionTrace> findByTenantIdAndToolTypeOrderByStartedAtDesc(UUID tenantId, String toolType);

    /**
     * Estatísticas de performance por skill.
     */
    @Query("""
        SELECT 
            skill_slug,
            tool_type,
            status,
            COUNT(*) as invocation_count,
            AVG(duration_ms) as avg_duration_ms,
            MIN(duration_ms) as min_duration_ms,
            MAX(duration_ms) as max_duration_ms
        FROM tool_execution_traces
        WHERE tenant_id = :tenantId
          AND started_at >= :since
        GROUP BY skill_slug, tool_type, status
        """)
    Flux<Object> getPerformanceStats(UUID tenantId, OffsetDateTime since);
}
