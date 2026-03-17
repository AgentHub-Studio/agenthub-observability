package dev.cezar.agenthub.observability.multitenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MultiTenantFilter.
 */
@ExtendWith(MockitoExtension.class)
class MultiTenantFilterTest {

    /**
     * Fake JWT with payload: {"iss": "http://keycloak/realms/test-tenant-uuid", "sub": "user-123"}
     * Format: header.payload.signature (signature is ignored during extraction)
     */
    private static final String FAKE_JWT_PAYLOAD = "eyJpc3MiOiAiaHR0cDovL2tleWNsb2FrL3JlYWxtcy90ZXN0LXRlbmFudC11dWlkIiwgInN1YiI6ICJ1c2VyLTEyMyJ9";
    private static final String FAKE_JWT = "eyJhbGciOiJSUzI1NiJ9." + FAKE_JWT_PAYLOAD + ".fake-signature";
    private static final String EXPECTED_TENANT_ID = "test-tenant-uuid";

    private MultiTenantFilter multiTenantFilter;

    @BeforeEach
    void setUp() {
        multiTenantFilter = new MultiTenantFilter();
    }

    @Test
    void shouldExtractTenantIdFromValidJwtBearerToken() {
        AtomicReference<String> capturedTenantId = new AtomicReference<>();

        ServerWebExchange exchange = mockExchange("Bearer " + FAKE_JWT);

        // Chain captures the tenantId from Reactor context when the filter invokes it
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            capturedTenantId.set(ctx.getOrDefault("tenantId", null));
            return Mono.empty();
        });

        StepVerifier.create(multiTenantFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedTenantId.get()).isEqualTo(EXPECTED_TENANT_ID);
    }

    @Test
    void shouldUseDefaultSchemaWhenNoAuthorizationHeaderPresent() {
        AtomicReference<String> capturedTenantId = new AtomicReference<>();

        ServerWebExchange exchange = mockExchange(null);

        // Chain captures the tenantId from Reactor context when the filter invokes it
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            capturedTenantId.set(ctx.getOrDefault("tenantId", null));
            return Mono.empty();
        });

        StepVerifier.create(multiTenantFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedTenantId.get()).isEqualTo(MultiTenant.DEFAULT_SCHEMA);
    }

    private ServerWebExchange mockExchange(String authorizationHeader) {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeader != null) {
            headers.add("Authorization", authorizationHeader);
        }
        Map<String, Object> attributes = new HashMap<>();

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getAttributes()).thenReturn(attributes);

        return exchange;
    }
}
