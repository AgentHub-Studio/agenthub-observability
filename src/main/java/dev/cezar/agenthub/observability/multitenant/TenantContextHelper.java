package dev.cezar.agenthub.observability.multitenant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Helper to access tenant context information reactively.
 *
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TenantContextHelper {

    /**
     * Retrieves the tenantId from the reactive context.
     *
     * @return {@link Mono} with tenantId or error if not available
     */
    public static Mono<UUID> getTenantId() {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault("tenantId", null);
            if (tenantId == null) {
                return Mono.error(new IllegalStateException("TenantId não disponível no contexto"));
            }
            try {
                return Mono.just(UUID.fromString(tenantId));
            } catch (IllegalArgumentException e) {
                return Mono.error(new IllegalStateException("TenantId inválido: " + tenantId));
            }
        });
    }

    /**
     * Retrieves the userId from the reactive context.
     *
     * @return {@link Mono} with userId or error if not available
     */
    public static Mono<UUID> getUserId() {
        return Mono.deferContextual(ctx -> {
            String userId = ctx.getOrDefault("userId", null);
            if (userId == null) {
                return Mono.error(new IllegalStateException("UserId não disponível no contexto"));
            }
            try {
                return Mono.just(UUID.fromString(userId));
            } catch (IllegalArgumentException e) {
                return Mono.error(new IllegalStateException("UserId inválido: " + userId));
            }
        });
    }

    /**
     * Retrieves the full TenantContext from the reactive context.
     *
     * @return {@link Mono} with TenantContext
     */
    public static Mono<TenantContext> getContext() {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault("tenantId", null);
            String userId = ctx.getOrDefault("userId", null);
            if (tenantId == null) {
                return Mono.error(new IllegalStateException("Contexto de tenant não disponível"));
            }
            return Mono.just(new TenantContext(tenantId, userId));
        });
    }
}
