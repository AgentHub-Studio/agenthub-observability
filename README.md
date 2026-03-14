# AgentHub Observability

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Centralized trace and metric collection** for AgentHub platform.

## 📋 Overview

AgentHub Observability Service provides centralized storage and querying for:
- **Execution Traces**: Agent executions from orchestrator
- **Node Traces**: Pipeline node executions
- **Tool Traces**: Skill/tool invocations from skill-runtime
- **Metrics**: Time-series events and aggregations

## 🏗️ Architecture

```
┌─────────────────┐
│  Orchestrator   │ ──┐
└─────────────────┘   │
                      │ POST /traces/executions
┌─────────────────┐   │
│  Skill Runtime  │ ──┤ POST /traces/tools
└─────────────────┘   │
                      │ POST /metrics/events
┌─────────────────┐   │
│  Any Service    │ ──┘
└─────────────────┘   
          │
          ↓
┌──────────────────────────────┐
│  Observability Service       │
│  - TraceService              │
│  - MetricService             │
│  - PostgreSQL (R2DBC)        │
└──────────────────────────────┘
          │
          ↓
┌──────────────────────────────┐
│  PostgreSQL Database         │
│  - execution_traces          │
│  - node_execution_traces     │
│  - tool_execution_traces     │
│  - metric_events             │
│  - aggregated_metrics        │
└──────────────────────────────┘
```

## 🚀 Features

- ✅ **3-Level Tracing**: Execution → Node → Tool
- ✅ **4 Metric Types**: Counter, Gauge, Timer, Histogram
- ✅ **Pre-Aggregations**: SUM, AVG, MIN, MAX, COUNT, P50, P95, P99
- ✅ **Flexible Queries**: Filter by tenant, agent, skill, period, status
- ✅ **100% Reactive**: Spring WebFlux + R2DBC
- ✅ **OpenAPI 3.0**: Complete Swagger UI documentation
- ✅ **Retention Policies**: Automatic cleanup of old data

## 📊 Data Model

### Execution Trace (Orchestrator Level)
```json
{
  "id": "uuid",
  "tenantId": "uuid",
  "agentId": "uuid",
  "executionId": "uuid",
  "status": "COMPLETED",
  "startedAt": "2026-03-14T10:00:00Z",
  "completedAt": "2026-03-14T10:00:15Z",
  "durationMs": 15000,
  "inputData": {...},
  "outputData": {...},
  "triggerSource": "API"
}
```

### Node Execution Trace (Pipeline Level)
```json
{
  "id": "uuid",
  "executionTraceId": "uuid",
  "nodeId": "search-documents",
  "nodeType": "TOOL",
  "status": "COMPLETED",
  "durationMs": 250,
  "contextBefore": {...},
  "contextAfter": {...}
}
```

### Tool Execution Trace (Skill Runtime Level)
```json
{
  "id": "uuid",
  "skillSlug": "document-search",
  "toolType": "DOCUMENT_SEARCH",
  "status": "SUCCESS",
  "durationMs": 245,
  "inputData": {...},
  "outputData": {...},
  "attemptNumber": 1
}
```

### Metric Event
```json
{
  "id": "uuid",
  "tenantId": "uuid",
  "metricName": "execution.duration",
  "metricType": "TIMER",
  "metricValue": 15000,
  "metricUnit": "ms",
  "dimensions": {
    "agentId": "uuid",
    "status": "COMPLETED"
  },
  "timestamp": "2026-03-14T10:00:15Z"
}
```

## 📚 API Endpoints

### Trace APIs

```bash
# Create execution trace
POST /api/v1/traces/executions
{
  "tenantId": "uuid",
  "agentId": "uuid",
  "executionId": "uuid",
  "status": "RUNNING",
  "startedAt": "2026-03-14T10:00:00Z",
  "inputData": {...}
}

# Update execution trace (complete)
PUT /api/v1/traces/executions/{executionId}
{
  "status": "COMPLETED",
  "completedAt": "2026-03-14T10:00:15Z",
  "durationMs": 15000,
  "outputData": {...}
}

# Get execution trace
GET /api/v1/traces/executions/{executionId}

# List executions by tenant
GET /api/v1/traces/executions?tenantId=uuid&limit=100

# List executions by agent
GET /api/v1/traces/executions/by-agent?tenantId=uuid&agentId=uuid

# List executions by period
GET /api/v1/traces/executions/by-period?tenantId=uuid&startDate=...&endDate=...

# List node traces for execution
GET /api/v1/traces/executions/{executionTraceId}/nodes

# Create tool execution trace
POST /api/v1/traces/tools
{
  "tenantId": "uuid",
  "skillSlug": "document-search",
  "toolType": "DOCUMENT_SEARCH",
  "status": "SUCCESS",
  "startedAt": "2026-03-14T10:00:00Z",
  "completedAt": "2026-03-14T10:00:00.245Z",
  "durationMs": 245
}

# List tool traces by skill
GET /api/v1/traces/tools/by-skill?tenantId=uuid&skillSlug=document-search

# List tool traces by type
GET /api/v1/traces/tools/by-type?tenantId=uuid&toolType=DOCUMENT_SEARCH

# Count executions by status
GET /api/v1/traces/stats/executions/count?tenantId=uuid&status=COMPLETED&since=...
```

### Metric APIs

```bash
# Create metric event
POST /api/v1/metrics/events
{
  "tenantId": "uuid",
  "metricName": "execution.duration",
  "metricType": "TIMER",
  "metricValue": 15000,
  "metricUnit": "ms",
  "dimensions": {...}
}

# Create metrics in batch
POST /api/v1/metrics/events/batch
[{...}, {...}, ...]

# List metric events
GET /api/v1/metrics/events?tenantId=uuid&metricName=execution.duration&startDate=...&endDate=...

# Get aggregated metrics
GET /api/v1/metrics/aggregated?tenantId=uuid&metricName=execution.duration&aggregationType=AVG&aggregationPeriod=HOUR&startDate=...&endDate=...

# Convenience endpoints
POST /api/v1/metrics/counter?tenantId=uuid&metricName=execution.count&value=1
POST /api/v1/metrics/gauge?tenantId=uuid&metricName=queue.size&value=42&unit=count
POST /api/v1/metrics/timer?tenantId=uuid&metricName=execution.duration&durationMs=15000
POST /api/v1/metrics/histogram?tenantId=uuid&metricName=payload.size&value=1024&unit=bytes
```

## 🔧 Configuration

```properties
# Application
spring.application.name=agenthub-observability
server.port=8083

# R2DBC PostgreSQL
spring.r2dbc.url=r2dbc:postgresql://postgres:5432/agenthub
spring.r2dbc.username=postgres
spring.r2dbc.password=postgres

# Observability Settings
agenthub.observability.trace-retention-days=30
agenthub.observability.metric-aggregation-interval=60
agenthub.observability.batch-size=100
```

## 📖 Swagger UI

Access interactive API documentation:

**Local**: http://localhost:8083/swagger-ui.html  
**Docker**: http://agenthub-observability:8083/swagger-ui.html

## 🧪 Examples

### Send Execution Trace from Orchestrator

```java
// Start execution
POST /api/v1/traces/executions
{
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "agentId": "agent-001",
  "executionId": "exec-001",
  "status": "RUNNING",
  "startedAt": "2026-03-14T10:00:00Z",
  "inputData": {"query": "Hello"}
}

// Complete execution
PUT /api/v1/traces/executions/exec-001
{
  "status": "COMPLETED",
  "completedAt": "2026-03-14T10:00:15Z",
  "durationMs": 15000,
  "outputData": {"result": "Hello World"}
}
```

### Send Tool Trace from Skill Runtime

```java
POST /api/v1/traces/tools
{
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "skillSlug": "document-search",
  "toolType": "DOCUMENT_SEARCH",
  "status": "SUCCESS",
  "startedAt": "2026-03-14T10:00:05Z",
  "completedAt": "2026-03-14T10:00:05.245Z",
  "durationMs": 245,
  "attemptNumber": 1
}
```

### Send Metrics

```bash
# Counter - increment execution count
curl -X POST "http://localhost:8083/api/v1/metrics/counter?tenantId=uuid&metricName=execution.count&value=1"

# Timer - record execution duration
curl -X POST "http://localhost:8083/api/v1/metrics/timer?tenantId=uuid&metricName=execution.duration&durationMs=15000"

# Gauge - record active connections
curl -X POST "http://localhost:8083/api/v1/metrics/gauge?tenantId=uuid&metricName=active.connections&value=42&unit=count"
```

### Query Aggregated Metrics

```bash
# Average execution duration per hour (last 24h)
GET /api/v1/metrics/aggregated?tenantId=uuid&metricName=execution.duration&aggregationType=AVG&aggregationPeriod=HOUR&startDate=2026-03-13T10:00:00Z&endDate=2026-03-14T10:00:00Z

# P95 latency per day (last week)
GET /api/v1/metrics/aggregated?tenantId=uuid&metricName=tool.latency&aggregationType=P95&aggregationPeriod=DAY&startDate=2026-03-07T00:00:00Z&endDate=2026-03-14T00:00:00Z
```

## 🗄️ Database Schema

See `src/main/resources/schema.sql` for complete schema definition:
- 5 tables: execution_traces, node_execution_traces, tool_execution_traces, metric_events, aggregated_metrics
- 2 views: v_recent_executions, v_tool_performance
- Functions: cleanup_old_traces(), cleanup_old_metric_events()
- Optimized indexes for queries

## 🧹 Retention & Cleanup

Automatic cleanup functions:

```sql
-- Cleanup traces older than 30 days
SELECT cleanup_old_traces(30);

-- Cleanup metric events older than 7 days
SELECT cleanup_old_metric_events(7);
```

Run as scheduled job (e.g., daily cron).

## 🐳 Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build
./build.sh clean package

# Run
docker build -t agenthub-observability .
docker run -p 8083:8083 agenthub-observability
```

## 📝 License

MIT License - see [LICENSE](LICENSE) for details.
