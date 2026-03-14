# RabbitMQ Integration - AgentHub Observability

Event-driven integration between Orchestrator and Observability Service.

## 📋 Overview

The Observability Service can **automatically create and update traces** by consuming events published by the Orchestrator through RabbitMQ. This provides a **decoupled, asynchronous, and scalable** integration.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     AgentHub Platform                        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐         ┌──────────────┐                  │
│  │ Orchestrator │────────>│   RabbitMQ   │                  │
│  │              │ Publish │              │                  │
│  │ - Executes   │ Events  │ Exchange:    │                  │
│  │   Pipelines  │         │ agenthub.    │                  │
│  │              │         │ orchestrator.│                  │
│  │ - Publishes  │         │ events       │                  │
│  │   Events     │         └──────┬───────┘                  │
│  └──────────────┘                │                          │
│                                   │ Routes                   │
│                                   │                          │
│                                   v                          │
│                          ┌─────────────────┐                │
│                          │  Observability  │                │
│                          │     Service     │                │
│                          │                 │                │
│                          │  - Consumes     │                │
│                          │    Events       │                │
│                          │  - Creates      │                │
│                          │    Traces       │                │
│                          │  - Updates      │                │
│                          │    Status       │                │
│                          └─────────────────┘                │
│                                   │                          │
│                                   v                          │
│                          ┌─────────────────┐                │
│                          │   PostgreSQL    │                │
│                          │   (Traces DB)   │                │
│                          └─────────────────┘                │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## 📡 Event Flow

### 1. Execution Lifecycle

```
Orchestrator                RabbitMQ                Observability
     |                          |                          |
     |--execution.queued------->|                          |
     |                          |--execution.queued------->|
     |                          |                          |--CREATE ExecutionTrace (RUNNING)
     |                          |                          |
     |--execution.started------>|                          |
     |                          |--execution.started------>|
     |                          |                          |--UPDATE ExecutionTrace (RUNNING)
     |                          |                          |
     |--node.started----------->|                          |
     |                          |--node.started----------->|
     |                          |                          |--CREATE NodeTrace (RUNNING)
     |                          |                          |
     |--node.completed--------->|                          |
     |                          |--node.completed--------->|
     |                          |                          |--UPDATE NodeTrace (COMPLETED)
     |                          |                          |
     |--execution.completed---->|                          |
     |                          |--execution.completed---->|
     |                          |                          |--UPDATE ExecutionTrace (COMPLETED)
     |                          |                          |  --Calculate duration
```

### 2. Event Types

#### Execution Events

| Event Type | Published By | Action in Observability |
|-----------|--------------|-------------------------|
| `execution.queued` | Orchestrator | CREATE ExecutionTrace (RUNNING) |
| `execution.started` | Orchestrator | UPDATE ExecutionTrace (RUNNING) |
| `execution.completed` | Orchestrator | UPDATE ExecutionTrace (COMPLETED) + set completedAt + calculate duration |
| `execution.failed` | Orchestrator | UPDATE ExecutionTrace (FAILED) + set completedAt + calculate duration |
| `execution.cancelled` | Orchestrator | UPDATE ExecutionTrace (CANCELLED) + set completedAt + calculate duration |
| `execution.timed_out` | Orchestrator | UPDATE ExecutionTrace (FAILED) + set completedAt + calculate duration |

#### Node Events

| Event Type | Published By | Action in Observability |
|-----------|--------------|-------------------------|
| `node.started` | Orchestrator | CREATE NodeExecutionTrace (RUNNING) |
| `node.completed` | Orchestrator | UPDATE NodeExecutionTrace (COMPLETED) + set completedAt + calculate duration |
| `node.failed` | Orchestrator | UPDATE NodeExecutionTrace (FAILED) + set completedAt + calculate duration |

## 🔧 Configuration

### Prerequisites

1. **RabbitMQ** running and accessible
2. **Orchestrator** configured to publish events
3. **Observability Service** configured to consume events

### Enable in Orchestrator

Set environment variable:

```bash
AGENTHUB_EVENTS_RABBITMQ_ENABLED=true
```

Or in `application.properties`:

```properties
agenthub.orchestrator.events.rabbitmq-enabled=true
agenthub.orchestrator.events.exchange=agenthub.orchestrator.events
```

### Enable in Observability Service

Set environment variable:

```bash
AGENTHUB_OBSERVABILITY_RABBITMQ_ENABLED=true
```

Or in `application.properties`:

```properties
agenthub.observability.rabbitmq.enabled=true
agenthub.observability.rabbitmq.exchange=agenthub.orchestrator.events
agenthub.observability.rabbitmq.queue=agenthub.observability.events
```

### RabbitMQ Connection Settings

**Environment Variables:**

```bash
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=agenthub
RABBITMQ_PASSWORD=agenthub_dev
```

**application.properties:**

```properties
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USER:agenthub}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:agenthub_dev}
```

## 🎯 Queue Configuration

### Exchange

**Name:** `agenthub.orchestrator.events`  
**Type:** Topic  
**Durable:** Yes  
**Auto-delete:** No

### Queue

**Name:** `agenthub.observability.events`  
**Durable:** Yes  
**Auto-delete:** No  
**Arguments:**
- `x-dead-letter-exchange`: `agenthub.orchestrator.events.dlx`

### Bindings

| Routing Key | Description |
|-------------|-------------|
| `execution.*` | All execution events (queued, started, completed, failed, cancelled, timed_out) |
| `node.*` | All node events (started, completed, failed) |

### Consumer Settings

```properties
spring.rabbitmq.listener.simple.concurrency=3
spring.rabbitmq.listener.simple.max-concurrency=10
spring.rabbitmq.listener.simple.prefetch=10
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.initial-interval=1000
spring.rabbitmq.listener.simple.retry.max-attempts=3
spring.rabbitmq.listener.simple.retry.multiplier=2.0
```

**Explanation:**
- **Concurrency:** 3-10 concurrent consumers for parallel processing
- **Prefetch:** Each consumer fetches 10 messages at a time
- **Retry:** 3 attempts with exponential backoff (1s → 2s → 4s)

## 📦 Event Payload Format

### ExecutionEvent

```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "123e4567-e89b-12d3-a456-426614174000"
}
```

**Fields:**
- `executionId` (UUID): Unique execution identifier
- `tenantId` (UUID): Tenant identifier for multi-tenancy

### NodeEvent

```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "nodeId": "node-001"
}
```

**Fields:**
- `executionId` (UUID): Execution identifier (links to execution)
- `nodeId` (String): Node identifier within the pipeline

## 🔍 Monitoring

### RabbitMQ Management UI

Access: http://localhost:15672  
Default credentials: `guest / guest`

**What to check:**
- Exchange `agenthub.orchestrator.events` exists
- Queue `agenthub.observability.events` exists
- Bindings are configured correctly
- Message rates (published vs consumed)
- Consumer count (should be 3-10)
- Messages in queue (should be low if processing is fast)

### Observability Service Logs

Look for log entries:

```
Received execution.started event: executionId=..., tenantId=...
Created execution trace for queued event: <trace-id>
Updated execution trace status to RUNNING: <trace-id>
Completed execution trace: <trace-id>
```

### Metrics

Check Prometheus metrics:

```bash
curl http://localhost:8083/actuator/prometheus | grep rabbitmq
```

**Key metrics:**
- `spring_rabbitmq_listener_messages_received_total`
- `spring_rabbitmq_listener_messages_processed_total`
- `spring_rabbitmq_listener_messages_failed_total`

## 🐛 Troubleshooting

### Events not being consumed

**Check 1:** Is RabbitMQ running?

```bash
docker ps | grep rabbitmq
```

**Check 2:** Is Observability Service connected?

```bash
# Check logs for connection
docker logs agenthub-observability | grep -i rabbit
```

**Check 3:** Is feature enabled?

```bash
# Verify environment variable
echo $AGENTHUB_OBSERVABILITY_RABBITMQ_ENABLED
```

**Check 4:** Are bindings configured?

Open RabbitMQ Management UI → Exchanges → `agenthub.orchestrator.events` → Bindings

### Messages stuck in queue

**Possible causes:**
1. Consumer errors (check logs)
2. Database connection issues
3. Invalid event payload

**Solutions:**
```bash
# Check dead letter queue
# Access RabbitMQ Management UI
# Check queue: agenthub.orchestrator.events.dlx

# Purge queue (CAUTION: deletes messages)
rabbitmqadmin purge queue name=agenthub.observability.events
```

### Duplicate traces

**Cause:** Event redelivered after consumer crash

**Solution:** Idempotency is built-in:
- `findByExecutionId()` checks if trace exists
- Updates existing trace instead of creating duplicate

## ✅ Benefits

### Decoupling
- Orchestrator doesn't need to know about Observability Service
- Services can evolve independently
- Easy to add/remove consumers

### Resilience
- RabbitMQ provides durability and persistence
- Automatic retries on failure
- Dead Letter Queue for failed messages
- Continues working even if Observability is down temporarily

### Scalability
- Multiple Observability instances can consume in parallel
- Load balancing across consumers
- Backpressure handling

### Observability
- Event history in RabbitMQ
- Easy to add new consumers for different purposes
- Audit trail of all execution events

## 🚀 Testing

### Local Testing

1. **Start RabbitMQ:**

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

2. **Enable in Orchestrator:**

```bash
export AGENTHUB_EVENTS_RABBITMQ_ENABLED=true
```

3. **Enable in Observability:**

```bash
export AGENTHUB_OBSERVABILITY_RABBITMQ_ENABLED=true
```

4. **Trigger an execution:**

```bash
curl -X POST http://localhost:8080/api/v1/executions \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-001",
    "tenantId": "123e4567-e89b-12d3-a456-426614174000"
  }'
```

5. **Check RabbitMQ:**

Open http://localhost:15672 → Queues → `agenthub.observability.events`

6. **Check Observability logs:**

```bash
docker logs agenthub-observability -f
```

7. **Query traces:**

```bash
curl http://localhost:8083/api/v1/traces/executions
```

### Integration Test Scenario

```gherkin
Given Orchestrator is running with RabbitMQ enabled
And Observability Service is running with RabbitMQ enabled
When I trigger a pipeline execution
Then I should see:
  - execution.queued event published to RabbitMQ
  - ExecutionTrace created in Observability DB
  - execution.started event published
  - ExecutionTrace updated to RUNNING
  - node.started events for each node
  - NodeExecutionTrace created for each node
  - node.completed events for each node
  - NodeExecutionTrace updated to COMPLETED
  - execution.completed event published
  - ExecutionTrace updated to COMPLETED with duration
```

## 📚 References

- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [Spring AMQP](https://spring.io/projects/spring-amqp)
- [INTEGRATION.md](INTEGRATION.md) - REST API integration
- [README.md](README.md) - Service overview

---

**Status:** ✅ Implemented and Ready  
**Version:** 1.0.0  
**Last Updated:** 2026-03-14
