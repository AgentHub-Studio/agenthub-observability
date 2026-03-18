package dev.cezar.agenthub.observability.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.MySqlDialect;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import java.util.Optional;

/**
 * SPI provider that returns a {@link R2dbcDialect} for ClickHouse connections.
 *
 * <p>Registered via {@code META-INF/spring.factories} under
 * {@code org.springframework.data.r2dbc.dialect.DialectResolver$R2dbcDialectProvider}.
 * {@code DialectResolver} loads these providers before {@code R2dbcDataAutoConfiguration}
 * runs its constructor, so the "Cannot determine a dialect for ClickHouse" error is
 * prevented at the source.
 *
 * <p>ClickHouse uses anonymous {@code ?} positional bind markers — identical to MySQL —
 * so {@link MySqlDialect#INSTANCE} is used as the closest built-in dialect.
 *
 * @since 1.0.0
 */
public class ClickHouseDialectProvider implements DialectResolver.R2dbcDialectProvider {

    @Override
    public Optional<R2dbcDialect> getDialect(ConnectionFactory connectionFactory) {
        if (connectionFactory.getMetadata().getName().contains("ClickHouse")) {
            return Optional.of(MySqlDialect.INSTANCE);
        }
        return Optional.empty();
    }
}
