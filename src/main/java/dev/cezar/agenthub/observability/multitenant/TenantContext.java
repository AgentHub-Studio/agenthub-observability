package dev.cezar.agenthub.observability.multitenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.util.Objects;

/**
 * Represents contextual information specific to a tenant.
 * This class encapsulates data that can be associated with a particular
 * tenant in a multi-tenant application, allowing tenant-specific
 * configurations or operations.
 */
@Getter
public class TenantContext {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String tenantId;
    private final String userId;
    private String schemaName;

    public TenantContext(String tenantId) {
        this(tenantId, null);
    }

    public TenantContext(String tenantId, String userId) {
        this.tenantId = tenantId;
        this.userId = userId;
    }

    /**
     * Retrieves the database schema name associated with the current tenant.
     * The schema name is constructed dynamically based on the tenant identifier.
     */
    public String getSchemaName() {
        Objects.requireNonNull(this.tenantId, "Tenant ID cannot be null");
        if (this.schemaName == null) {
            this.schemaName = MultiTenant.DEFAULT_SCHEMA.equalsIgnoreCase(this.tenantId) ?
                    this.tenantId :
                    MultiTenant.SCHEMA_PREFIX + this.tenantId;
        }
        return this.schemaName;
    }

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception ignore) {
            return super.toString();
        }
    }
}
