package dev.cezar.agenthub.observability.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração OpenAPI 3.0 para documentação da API.
 *
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI observabilityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AgentHub Observability API")
                        .description("""
                                **AgentHub Observability Service** - Trace and metric collection for agent executions.
                                
                                ## Architecture
                                
                                This service provides centralized observability for the AgentHub platform:
                                - **Traces**: Agent execution traces (orchestrator → pipeline → tools)
                                - **Metrics**: Time-series events and pre-aggregated metrics
                                - **Storage**: PostgreSQL with optimized indexes for queries
                                
                                ## Features
                                
                                - ✅ **Execution Traces**: Track agent executions from start to finish
                                - ✅ **Node Traces**: Track pipeline node executions
                                - ✅ **Tool Traces**: Track skill/tool invocations
                                - ✅ **Metric Events**: Time-series metric data (counters, gauges, timers, histograms)
                                - ✅ **Aggregated Metrics**: Pre-computed aggregations (SUM, AVG, P50, P95, P99)
                                - ✅ **Flexible Queries**: Filter by tenant, agent, skill, period, status
                                - ✅ **100% Reactive**: Non-blocking with Spring WebFlux + R2DBC
                                
                                ## Trace Levels
                                
                                1. **Execution Trace** (orchestrator level)
                                   - Agent execution metadata
                                   - Input/output data
                                   - Trigger source (API, webhook, schedule)
                                   - Overall status and duration
                                
                                2. **Node Execution Trace** (pipeline level)
                                   - Pipeline node execution
                                   - Context mutation (before/after)
                                   - Node type (TOOL, LLM, DECISION, LOOP, PARALLEL)
                                   - Retry attempts
                                
                                3. **Tool Execution Trace** (skill runtime level)
                                   - Skill/tool invocation
                                   - Tool type (HTTP, SQL, DOCUMENT_SEARCH, SCRIPT, MCP)
                                   - Performance metrics
                                   - Input/output validation
                                
                                ## Metric Types
                                
                                | Type | Description | Use Case |
                                |------|-------------|----------|
                                | **COUNTER** | Monotonically increasing value | Request count, errors |
                                | **GAUGE** | Absolute value at a point in time | Active connections, queue size |
                                | **TIMER** | Duration measurements | Execution time, latency |
                                | **HISTOGRAM** | Value distribution | Response times, payload sizes |
                                
                                ## Aggregation Periods
                                
                                - **MINUTE**: 1-minute aggregations
                                - **HOUR**: 1-hour aggregations
                                - **DAY**: Daily aggregations
                                - **WEEK**: Weekly aggregations
                                - **MONTH**: Monthly aggregations
                                
                                ## Aggregation Types
                                
                                - **SUM**: Total sum of values
                                - **AVG**: Average value
                                - **MIN**: Minimum value
                                - **MAX**: Maximum value
                                - **COUNT**: Number of samples
                                - **P50**: 50th percentile (median)
                                - **P95**: 95th percentile
                                - **P99**: 99th percentile
                                
                                ## Retention Policies
                                
                                - **Execution Traces**: 30 days (configurable)
                                - **Metric Events**: 7 days (raw data)
                                - **Aggregated Metrics**: 1 year
                                
                                ## Integration
                                
                                Services send traces/metrics via REST API:
                                - **Orchestrator** → `POST /traces/executions`
                                - **Skill Runtime** → `POST /traces/tools`
                                - **Any Service** → `POST /metrics/events`
                                
                                ## Performance
                                
                                - Optimized PostgreSQL indexes
                                - JSONB dimensions for flexible filtering
                                - Views for common queries (recent executions, tool performance)
                                - Batch insert support for high-throughput scenarios
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AgentHub Team")
                                .email("dev@agenthub.dev")
                                .url("https://github.com/cezardev/agenthub"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8083")
                                .description("Local Development"),
                        new Server()
                                .url("http://agenthub-observability:8083")
                                .description("Docker Internal Network"),
                        new Server()
                                .url("https://api.agenthub.dev/observability")
                                .description("Production API")
                ));
    }
}
