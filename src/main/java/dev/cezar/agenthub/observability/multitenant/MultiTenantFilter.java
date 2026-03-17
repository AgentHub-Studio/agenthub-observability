package dev.cezar.agenthub.observability.multitenant;

import lombok.extern.java.Log;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * A filter implementation for handling multi-tenancy in a web application.
 * This class intercepts incoming web requests to identify and set the tenant-specific
 * context. It achieves this by extracting the tenant identifier from the "Authorization"
 * header of the HTTP request, parsing the token, and setting the tenant information
 * in a thread-local {@link TenantContext} using {@link TenantContextHolder}.
 * <p>
 * If the "Authorization" header is not present in the request, the filter defaults
 * to using the schema defined by {@link MultiTenant#DEFAULT_SCHEMA}.
 * <p>
 * This implementation ensures that tenant-specific details are available for the duration
 * of the request lifecycle, enabling effective multi-tenant operations.
 */
@Log
@Component
public class MultiTenantFilter implements WebFilter {

    /**
     * Prefix for bearer token in authorization header.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Header name for authorization.
     */
    private static final String AUTHORIZATION = "Authorization";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final var authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION);
        final var tenantId = resolveTenantId(authHeader);
        final var userId = resolveUserId(authHeader);
        final var tenantContext = new TenantContext(tenantId, userId);
        TenantContextHolder.setContext(tenantContext);
        exchange.getRequest().getAttributes().put(TenantContextHolder.class.getName(), tenantContext);
        log.info("Tenant context set to: " + tenantContext);
        return chain.filter(exchange)
                .contextWrite(TenantContextHolder.withTenantContext(tenantContext))
                .contextWrite(ctx -> {
                    var c = ctx.put("schema", tenantContext.getSchemaName())
                               .put("tenantId", tenantContext.getTenantId());
                    if (tenantContext.getUserId() != null) {
                        c = c.put("userId", tenantContext.getUserId());
                    }
                    return c;
                })
                .doOnSubscribe(subscription -> TenantContextHolder.setContext(tenantContext))
                .doFinally(signalType -> TenantContextHolder.clear());
    }

    private String resolveTenantId(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            final var token = authorizationHeader.substring(BEARER_PREFIX.length());
            return TokenExtractorUtils.getTenantIdFromToken(token);
        }
        return MultiTenant.DEFAULT_SCHEMA;
    }

    private String resolveUserId(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            final var token = authorizationHeader.substring(BEARER_PREFIX.length());
            return TokenExtractorUtils.getUserIdFromToken(token);
        }
        return null;
    }
}
