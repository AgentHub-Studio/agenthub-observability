package dev.cezar.agenthub.observability.multitenant;

/**
 * The {@code MultiTenant} class serves as a centralized definition for the tenant identifier key used
 * throughout the application to manage multi-tenancy. The tenant identifier is typically used in
 * HTTP headers, request attributes, and database schema resolutions to differentiate data among
 * different tenants in a multi-tenant environment.
 */
public class MultiTenant {

    /**
     * Represents the default database schema used in a multi-tenant architecture when no specific
     * tenant schema is explicitly provided or identified.
     */
    public static final String DEFAULT_SCHEMA = "public";

    /**
     * Represents the prefix for tenant-specific database schemas.
     */
    public static final String SCHEMA_PREFIX = "ah_";
}
