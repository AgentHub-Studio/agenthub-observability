package dev.cezar.agenthub.observability.multitenant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Objects;

/**
 * A utility class for managing the storage of {@link TenantContext} instances.
 * This class provides methods to set, retrieve, and clear {@link TenantContext} information,
 * supporting both thread-local storage for traditional blocking operations and
 * Reactor Context for reactive operations.
 * <p>
 * In a reactive environment, it leverages Reactor's {@code Context} to maintain
 * tenant-specific information across asynchronous boundaries. For blocking calls,
 * it falls back to a {@code ThreadLocal} variable.
 * <p>
 * This class cannot be instantiated or extended.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TenantContextHolder {

    /**
     * Key used to store the {@link TenantContext} in the Reactor Context.
     */
    public static final String TENANT_CONTEXT_KEY = "TENANT_CONTEXT_KEY";

    /**
     * A ThreadLocal variable used to hold the {@link TenantContext} instance for the current thread.
     */
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    /**
     * Sets the {@link TenantContext} instance for the current thread.
     */
    public static void setContext(TenantContext tenantContext) {
        TenantContextHolder.CONTEXT.set(tenantContext);
    }

    /**
     * Retrieves the {@link TenantContext} instance associated with the current thread or Reactor Context.
     */
    public static TenantContext getContext() {
        TenantContext tenantContext = CONTEXT.get();
        if (tenantContext == null) {
            try {
                return Mono.deferContextual(ctx -> Mono.justOrEmpty(ctx.<TenantContext>getOrEmpty(TENANT_CONTEXT_KEY)))
                        .block();
            } catch (Exception e) {
                return null;
            }
        }
        return tenantContext;
    }

    /**
     * Retrieves the {@link TenantContext} from the Reactor Context.
     *
     * @return a Mono emitting the TenantContext or empty if not found.
     */
    public static Mono<TenantContext> getContextFromReactor() {
        return Mono.deferContextual(ctx -> Mono.justOrEmpty(ctx.<TenantContext>getOrEmpty(TENANT_CONTEXT_KEY)));
    }

    /**
     * Returns a Reactor Mono that emits the provided TenantContext.
     */
    public static Mono<TenantContext> getContextReactor(TenantContext tenantContext) {
        return Mono.just(tenantContext).contextWrite(ctx -> ctx.put("schema", tenantContext.getSchemaName()));
    }

    /**
     * Clears the {@link TenantContext} instance associated with the current thread.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Creates a Reactor {@link Context} containing the given {@link TenantContext}.
     *
     * @param tenantContext the tenant context to include.
     * @return a Reactor Context.
     */
    public static Context withTenantContext(TenantContext tenantContext) {
        return Context.of(TENANT_CONTEXT_KEY, tenantContext);
    }

    /**
     * Constructs a Reactor {@link Context} containing the current thread's {@link TenantContext}.
     */
    public static Context withTenantContext() {
        return Context.of(TENANT_CONTEXT_KEY, Objects.requireNonNull(TenantContextHolder.getContext()));
    }
}
