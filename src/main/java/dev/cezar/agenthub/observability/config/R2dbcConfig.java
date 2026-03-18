package dev.cezar.agenthub.observability.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.binding.BindMarkersFactory;

/**
 * R2DBC configuration for ClickHouse.
 *
 * <p>Two separate issues must be fixed for ClickHouse support in Spring R2DBC:
 *
 * <ol>
 *   <li><b>spring-data-r2dbc dialect:</b> {@code R2dbcDataAutoConfiguration} calls
 *       {@code DialectResolver.getDialect()} in its constructor — before any bean condition
 *       can apply. Handled by {@link ClickHouseDialectProvider} registered via
 *       {@code META-INF/spring.factories}.</li>
 *   <li><b>spring-r2dbc bind markers:</b> {@code ConnectionFactoryDependentConfiguration}
 *       calls {@code BindMarkersFactoryResolver.resolve()} when building {@code DatabaseClient}.
 *       Handled here by providing a {@code DatabaseClient} bean with an explicit
 *       {@link BindMarkersFactory}, so the resolver is never called.</li>
 * </ol>
 *
 * @since 1.0.0
 */
@Configuration
public class R2dbcConfig {

    /**
     * Provides a {@link DatabaseClient} with ClickHouse-compatible {@code ?} bind markers.
     *
     * <p>When {@code bindMarkersFactory} is set explicitly on {@code DefaultDatabaseClientBuilder},
     * it skips {@code BindMarkersFactoryResolver.resolve()} entirely.
     *
     * @param connectionFactory the auto-configured ClickHouse connection factory
     * @return configured {@link DatabaseClient}
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseClient.class)
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.builder()
                .connectionFactory(connectionFactory)
                .bindMarkers(BindMarkersFactory.anonymous("?"))
                .build();
    }
}
