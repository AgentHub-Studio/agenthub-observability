# Integration Guide - AgentHub Observability

Guia de integração para enviar traces e métricas ao Observability Service.

## 📡 Service URLs

- **Local**: http://localhost:8083
- **Docker**: http://observability:8083

## 🔧 Integration Options

### 1. REST API (Recommended)

Use REST API directly via HTTP client.

### 2. WebClient (Spring Boot)

Use Spring WebClient for reactive integration.

### 3. Batch Mode

Send traces/metrics in batches for high-throughput scenarios.

---

## 📊 Orchestrator Integration

### Send Execution Trace

**Start Execution:**

```java
// Orchestrator - quando inicia execução
POST http://observability:8083/api/v1/traces/executions
Content-Type: application/json

{
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "agentId": "agent-001",
  "executionId": "exec-001",
  "status": "RUNNING",
  "startedAt": "2026-03-14T10:00:00Z",
  "inputData": {"query": "Hello"},
  "triggerSource": "API"
}
```

**Complete Execution:**

```java
// Orchestrator - quando completa execução
PUT http://observability:8083/api/v1/traces/executions/exec-001
Content-Type: application/json

{
  "status": "COMPLETED",
  "completedAt": "2026-03-14T10:00:15Z",
  "durationMs": 15000,
  "outputData": {"result": "Hello World"}
}
```

### WebClient Example (Java)

```java
@Service
@RequiredArgsConstructor
public class ObservabilityClient {

    private final WebClient webClient;

    public ObservabilityClient() {
        this.webClient = WebClient.builder()
                .baseUrl("http://observability:8083")
                .build();
    }

    public Mono<Void> startExecution(
            UUID tenantId,
            UUID agentId,
            UUID executionId,
            Map<String, Object> inputData) {

        var request = Map.of(
                "tenantId", tenantId,
                "agentId", agentId,
                "executionId", executionId,
                "status", "RUNNING",
                "startedAt", OffsetDateTime.now(),
                "inputData", inputData,
                "triggerSource", "API"
        );

        return webClient.post()
                .uri("/api/v1/traces/executions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> log.error("Failed to send trace", error))
                .onErrorResume(error -> Mono.empty()); // Don't fail if observability is down
    }

    public Mono<Void> completeExecution(
            UUID executionId,
            String status,
            long durationMs,
            Map<String, Object> outputData) {

        var request = Map.of(
                "status", status,
                "completedAt", OffsetDateTime.now(),
                "durationMs", durationMs,
                "outputData", outputData
        );

        return webClient.put()
                .uri("/api/v1/traces/executions/{executionId}", executionId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> log.error("Failed to update trace", error))
                .onErrorResume(error -> Mono.empty());
    }
}
```

---

## 🛠️ Skill Runtime Integration

### Send Tool Execution Trace

```java
// Skill Runtime - após executar tool
POST http://observability:8083/api/v1/traces/tools
Content-Type: application/json

{
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "skillSlug": "document-search",
  "toolType": "DOCUMENT_SEARCH",
  "status": "SUCCESS",
  "startedAt": "2026-03-14T10:00:05Z",
  "completedAt": "2026-03-14T10:00:05.245Z",
  "durationMs": 245,
  "attemptNumber": 1,
  "maxRetries": 3
}
```

### WebClient Example (Java)

```java
@Service
public class ObservabilityClient {

    private final WebClient webClient;

    public ObservabilityClient() {
        this.webClient = WebClient.builder()
                .baseUrl("http://observability:8083")
                .build();
    }

    public Mono<Void> sendToolTrace(
            UUID tenantId,
            String skillSlug,
            String toolType,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            long durationMs) {

        var request = Map.of(
                "tenantId", tenantId,
                "skillSlug", skillSlug,
                "toolType", toolType,
                "status", status,
                "startedAt", startedAt,
                "completedAt", completedAt,
                "durationMs", durationMs,
                "attemptNumber", 1
        );

        return webClient.post()
                .uri("/api/v1/traces/tools")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> log.error("Failed to send tool trace", error))
                .onErrorResume(error -> Mono.empty());
    }
}
```

---

## 📈 Metrics Integration

### Send Metrics

**Counter (increment):**

```bash
curl -X POST "http://observability:8083/api/v1/metrics/counter?tenantId=uuid&metricName=execution.count&value=1"
```

**Timer (duration):**

```bash
curl -X POST "http://observability:8083/api/v1/metrics/timer?tenantId=uuid&metricName=execution.duration&durationMs=15000"
```

**Gauge (absolute value):**

```bash
curl -X POST "http://observability:8083/api/v1/metrics/gauge?tenantId=uuid&metricName=queue.size&value=42&unit=count"
```

### WebClient Example (Java)

```java
@Service
public class MetricsClient {

    private final WebClient webClient;

    public MetricsClient() {
        this.webClient = WebClient.builder()
                .baseUrl("http://observability:8083")
                .build();
    }

    public Mono<Void> recordCounter(UUID tenantId, String metricName, double value) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/metrics/counter")
                        .queryParam("tenantId", tenantId)
                        .queryParam("metricName", metricName)
                        .queryParam("value", value)
                        .build())
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(error -> Mono.empty());
    }

    public Mono<Void> recordTimer(UUID tenantId, String metricName, long durationMs) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/metrics/timer")
                        .queryParam("tenantId", tenantId)
                        .queryParam("metricName", metricName)
                        .queryParam("durationMs", durationMs)
                        .build())
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(error -> Mono.empty());
    }

    public Mono<Void> recordGauge(UUID tenantId, String metricName, double value, String unit) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/metrics/gauge")
                        .queryParam("tenantId", tenantId)
                        .queryParam("metricName", metricName)
                        .queryParam("value", value)
                        .queryParam("unit", unit)
                        .build())
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(error -> Mono.empty());
    }
}
```

---

## 🎯 Best Practices

### 1. Fire and Forget

Observability calls should NOT block main flow:

```java
// ✅ Good - non-blocking
observabilityClient.sendTrace(...)
        .subscribe(); // Fire and forget

// ❌ Bad - blocking
observabilityClient.sendTrace(...)
        .block(); // Blocks execution
```

### 2. Error Handling

Always handle errors gracefully:

```java
observabilityClient.sendTrace(...)
        .doOnError(error -> log.warn("Failed to send trace", error))
        .onErrorResume(error -> Mono.empty()) // Continue on error
        .subscribe();
```

### 3. Batch Mode

For high-throughput scenarios, batch traces:

```java
Flux<CreateToolExecutionTraceRequest> traces = ...;

webClient.post()
        .uri("/api/v1/traces/tools/batch")
        .body(traces, CreateToolExecutionTraceRequest.class)
        .retrieve()
        .bodyToMono(Void.class)
        .subscribe();
```

### 4. Conditional Sending

Only send if observability is enabled:

```java
@Value("${agenthub.observability.enabled:true}")
private boolean observabilityEnabled;

public void sendTrace(...) {
    if (observabilityEnabled) {
        observabilityClient.sendTrace(...)
                .subscribe();
    }
}
```

---

## 🧪 Testing

### Local Testing

```bash
# Start observability service
cd agenthub-observability
./build.sh clean package
docker build -t agenthub-observability .
docker run -p 8083:8083 agenthub-observability

# Send test trace
curl -X POST http://localhost:8083/api/v1/traces/executions \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "agentId": "agent-001",
    "executionId": "exec-test-001",
    "status": "RUNNING",
    "startedAt": "2026-03-14T10:00:00Z",
    "triggerSource": "MANUAL"
  }'

# Query trace
curl http://localhost:8083/api/v1/traces/executions/exec-test-001
```

### Integration Testing

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ObservabilityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldSendExecutionTrace() {
        var request = Map.of(
                "tenantId", UUID.randomUUID(),
                "agentId", UUID.randomUUID(),
                "executionId", UUID.randomUUID(),
                "status", "RUNNING",
                "startedAt", OffsetDateTime.now()
        );

        webTestClient.post()
                .uri("/api/v1/traces/executions")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();
    }
}
```

---

## 📊 Monitoring

### Health Check

```bash
curl http://observability:8083/actuator/health
```

### Metrics Endpoint

```bash
curl http://observability:8083/actuator/prometheus
```

### Swagger UI

http://observability:8083/swagger-ui.html

---

## 🐛 Troubleshooting

### Observability Service Down

If observability is down, services should continue working:

```java
// Use circuit breaker pattern
observabilityClient.sendTrace(...)
        .timeout(Duration.ofSeconds(1)) // Fast timeout
        .onErrorResume(error -> Mono.empty()) // Fail silently
        .subscribe();
```

### High Latency

Use async batching:

```java
private final Sinks.Many<TraceRequest> traceSink = Sinks.many().multicast().onBackpressureBuffer();

public void sendTrace(TraceRequest request) {
    traceSink.tryEmitNext(request);
}

@PostConstruct
public void init() {
    traceSink.asFlux()
            .bufferTimeout(100, Duration.ofSeconds(1)) // Batch 100 or 1s
            .flatMap(this::sendBatch)
            .subscribe();
}
```

---

## 🐰 RabbitMQ Integration (Event-Driven)

### Automatic Trace Creation via Events

The Observability Service can **automatically create traces** by consuming events from the Orchestrator via RabbitMQ.

#### Enable RabbitMQ Integration

Set environment variable:

```bash
AGENTHUB_OBSERVABILITY_RABBITMQ_ENABLED=true
```

#### How it Works

```
┌─────────────────┐
│  Orchestrator   │
└────────┬────────┘
         │ Publishes events
         ↓
┌─────────────────┐
│   RabbitMQ      │
│   Exchange:     │
│   agenthub.     │
│   orchestrator. │
│   events        │
└────────┬────────┘
         │ Routes events
         ↓
┌─────────────────────────┐
│  Observability Service  │
│  - Consumes events      │
│  - Creates traces auto  │
│  - Updates status       │
└─────────────────────────┘
```

#### Events Consumed

**Execution Events:**
- `execution.queued` → Creates ExecutionTrace (RUNNING)
- `execution.started` → Updates ExecutionTrace (RUNNING)
- `execution.completed` → Completes ExecutionTrace (COMPLETED)
- `execution.failed` → Completes ExecutionTrace (FAILED)
- `execution.cancelled` → Completes ExecutionTrace (CANCELLED)
- `execution.timed_out` → Completes ExecutionTrace (FAILED)

**Node Events:**
- `node.started` → Creates NodeExecutionTrace (RUNNING)
- `node.completed` → Completes NodeExecutionTrace (COMPLETED)
- `node.failed` → Completes NodeExecutionTrace (FAILED)

#### Configuration

**Orchestrator** (enable event publishing):

```properties
AGENTHUB_EVENTS_RABBITMQ_ENABLED=true
```

**Observability Service** (enable event consumption):

```properties
AGENTHUB_OBSERVABILITY_RABBITMQ_ENABLED=true
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=agenthub
RABBITMQ_PASSWORD=agenthub_dev
```

#### Benefits of Event-Driven Integration

✅ **No Code Changes** - Orchestrator doesn't need to call REST APIs  
✅ **Decoupled** - Services are loosely coupled  
✅ **Async** - Non-blocking, fire-and-forget  
✅ **Resilient** - RabbitMQ handles retries and DLQ  
✅ **Scalable** - Multiple consumers can process events  
✅ **Automatic** - Traces created without explicit calls

#### Queue Configuration

**Exchange:** `agenthub.orchestrator.events` (topic)  
**Queue:** `agenthub.observability.events` (durable)  
**Routing Keys:**
- `execution.*` - All execution events
- `node.*` - All node events

**Consumer Settings:**
- Concurrency: 3-10 concurrent consumers
- Prefetch: 10 messages
- Retry: 3 attempts with exponential backoff (1s, 2s, 4s)

#### Fallback

If RabbitMQ integration is **disabled** (default), you can still use the REST API to manually create traces (see sections above).

---

## 📚 References

- [Swagger UI](http://localhost:8083/swagger-ui.html)
- [OpenAPI Spec](http://localhost:8083/api-docs)
- [README](README.md)
- [Schema SQL](src/main/resources/schema.sql)
- [RabbitMQ Management](http://localhost:15672) (guest/guest)
