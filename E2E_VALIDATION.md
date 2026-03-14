# AgentHub Observability - E2E Validation Guide

Este guia descreve como validar end-to-end o AgentHub Observability Service após build e deploy.

## 📋 Pré-requisitos

1. **Build dos projetos Java:**
   ```bash
   # Backend
   cd agenthub-backend
   ./build.sh package
   
   # Skill Runtime
   cd ../agenthub-skill-runtime
   ./build.sh package
   
   # Observability
   cd ../agenthub-observability
   ./build.sh package
   ```

2. **Build das imagens Docker:**
   ```bash
   # Backend
   cd agenthub-backend
   docker build -t agenthub-backend:latest .
   
   # Skill Runtime
   cd ../agenthub-skill-runtime
   docker build -t agenthub-skill-runtime:latest .
   
   # Observability
   cd ../agenthub-observability
   docker build -t agenthub-observability:latest .
   ```

3. **Orchestrator (Go):**
   ```bash
   cd agenthub-orchestrator
   docker build -t agenthub-orchestrator:latest .
   ```

## 🚀 Deploy da Stack Completa

```bash
# Subir todos os serviços
docker-compose -f docker-compose.observability.yml up -d

# Verificar status
docker-compose -f docker-compose.observability.yml ps

# Logs
docker-compose -f docker-compose.observability.yml logs -f observability
```

## ✅ Validações

### 1. Health Checks

```bash
# PostgreSQL
docker exec -it agenthub-postgres pg_isready -U postgres

# Backend
curl http://localhost:8080/actuator/health

# Skill Runtime
curl http://localhost:8082/actuator/health

# Observability
curl http://localhost:8083/actuator/health

# Orchestrator
curl http://localhost:9090/health
```

### 2. Schema PostgreSQL

Verificar se as tabelas foram criadas:

```bash
docker exec -it agenthub-postgres psql -U postgres -d agenthub -c "
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_name LIKE '%trace%' OR table_name LIKE '%metric%';
"
```

Deve retornar:
- `execution_traces`
- `node_execution_traces`
- `tool_execution_traces`
- `metric_events`
- `aggregated_metrics`

### 3. APIs REST - Observability

**Swagger UI:**
```
http://localhost:8083/swagger-ui.html
```

**Health:**
```bash
curl http://localhost:8083/actuator/health
```

**Criar Execution Trace:**
```bash
curl -X POST http://localhost:8083/api/traces/executions \
  -H "Content-Type: application/json" \
  -d '{
    "executionId": "exec-test-001",
    "tenantId": 1,
    "agentId": 100,
    "pipelineId": 200,
    "status": "RUNNING",
    "inputParameters": {"query": "test"}
  }'
```

**Listar Execution Traces:**
```bash
curl http://localhost:8083/api/traces/executions?tenantId=1
```

**Criar Metric Event:**
```bash
curl -X POST http://localhost:8083/api/metrics/events \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": 1,
    "metricName": "api.request.count",
    "metricType": "COUNTER",
    "value": 1.0,
    "dimensions": {"endpoint": "/api/agents"}
  }'
```

**Listar Metric Events:**
```bash
curl http://localhost:8083/api/metrics/events?tenantId=1
```

### 4. Integração Orchestrator → Observability

**Cenário:** Orchestrator inicia execução de agent

```bash
# 1. Criar agent no backend
curl -X POST http://localhost:8080/api/agents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Agent",
    "description": "Agent for E2E test",
    "tenantId": 1
  }'

# 2. Criar pipeline
curl -X POST http://localhost:8080/api/pipelines \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Pipeline",
    "tenantId": 1,
    "nodes": [
      {
        "id": "node-1",
        "type": "TOOL",
        "toolId": 1,
        "configuration": {}
      }
    ]
  }'

# 3. Executar via orchestrator (se disponível)
# O orchestrator deve criar trace automaticamente em:
# POST http://observability:8083/api/traces/executions

# 4. Verificar trace criado
curl http://localhost:8083/api/traces/executions?tenantId=1
```

### 5. Integração Skill Runtime → Observability

**Cenário:** Skill Runtime executa tool e envia trace

```bash
# 1. Executar tool via skill-runtime
curl -X POST http://localhost:8082/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "toolId": 1,
    "tenantId": 1,
    "parameters": {
      "url": "https://api.example.com/test",
      "method": "GET"
    }
  }'

# Skill runtime deve enviar tool trace para:
# POST http://observability:8083/api/traces/tools

# 2. Verificar tool trace
curl http://localhost:8083/api/traces/tools?tenantId=1
```

### 6. Aggregation Engine

**Verificar se scheduled jobs estão rodando:**

```bash
# Logs do observability
docker-compose -f docker-compose.observability.yml logs observability | grep -i "aggregat"

# Deve aparecer logs como:
# "Starting minute aggregation..."
# "Starting hour aggregation..."
# "Aggregated X metrics for period Y"
```

**Criar dados de teste e aguardar aggregation:**

```bash
# Criar 10 metric events
for i in {1..10}; do
  curl -X POST http://localhost:8083/api/metrics/events \
    -H "Content-Type: application/json" \
    -d '{
      "tenantId": 1,
      "metricName": "test.counter",
      "metricType": "COUNTER",
      "value": '$i'.0,
      "dimensions": {"test": "true"}
    }'
  sleep 1
done

# Aguardar 1 minuto (próximo ciclo de aggregation)
sleep 60

# Consultar métricas agregadas
curl "http://localhost:8083/api/metrics/aggregated?tenantId=1&metricName=test.counter&period=minute"
```

### 7. Retention Policies

**Verificar cleanup de dados antigos:**

```bash
# Criar trace antigo (via SQL direto)
docker exec -it agenthub-postgres psql -U postgres -d agenthub -c "
INSERT INTO execution_traces (execution_id, tenant_id, agent_id, pipeline_id, status, start_time, created_at)
VALUES ('old-trace-001', 1, 100, 200, 'COMPLETED', NOW() - INTERVAL '35 days', NOW() - INTERVAL '35 days');
"

# Aguardar job de cleanup (daily @ 2am, ou forçar manualmente via código)
# Ou verificar manualmente:
docker exec -it agenthub-postgres psql -U postgres -d agenthub -c "
SELECT COUNT(*) FROM execution_traces WHERE start_time < NOW() - INTERVAL '30 days';
"

# Deve retornar 0 após cleanup
```

### 8. Métricas e Stats

**Stats de execução:**
```bash
curl "http://localhost:8083/api/traces/executions/stats?tenantId=1&agentId=100"
```

Deve retornar:
```json
{
  "total": 5,
  "completed": 3,
  "failed": 1,
  "running": 1,
  "avgDurationMs": 1234.5
}
```

**Tool performance:**
```bash
curl "http://localhost:8083/api/traces/tools/performance?tenantId=1&toolName=HTTP_CLIENT"
```

### 9. Batch Operations

**Criar múltiplos metric events:**
```bash
curl -X POST http://localhost:8083/api/metrics/events/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "tenantId": 1,
      "metricName": "batch.metric.1",
      "metricType": "COUNTER",
      "value": 10.0
    },
    {
      "tenantId": 1,
      "metricName": "batch.metric.2",
      "metricType": "GAUGE",
      "value": 20.0
    },
    {
      "tenantId": 1,
      "metricName": "batch.metric.3",
      "metricType": "TIMER",
      "value": 150.0
    }
  ]'
```

### 10. Helper Methods

**Counter:**
```bash
curl -X POST "http://localhost:8083/api/metrics/counter?tenantId=1&metricName=api.calls&value=1"
```

**Gauge:**
```bash
curl -X POST "http://localhost:8083/api/metrics/gauge?tenantId=1&metricName=memory.usage&value=1024"
```

**Timer:**
```bash
curl -X POST "http://localhost:8083/api/metrics/timer?tenantId=1&metricName=response.time&durationMs=250"
```

**Histogram:**
```bash
curl -X POST "http://localhost:8083/api/metrics/histogram?tenantId=1&metricName=request.size&value=2048"
```

## 🧪 Testes Unitários

Executar testes (quando build estiver disponível):

```bash
cd agenthub-observability
./build.sh test

# Ou via Docker:
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  maven:3.9.12-amazoncorretto-25 \
  mvn clean test
```

**Testes criados:**
- `ExecutionTraceRepositoryTest` - 7 testes
- `MetricEventRepositoryTest` - 6 testes
- `TraceServiceTest` - 9 testes
- `MetricServiceTest` - 8 testes
- `AggregationServiceTest` - 10 testes
- `AggregationEngineTest` - 10 testes

**Total: 50 testes, ~1,400 linhas**

## 📊 Métricas de Sucesso

✅ **Infraestrutura:**
- Todos os 6 serviços healthy (postgres, backend, orchestrator, skill-runtime, observability, embedding)
- Schema PostgreSQL completo (5 tabelas, 2 views)
- Health checks passando

✅ **APIs:**
- 21 endpoints REST funcionando (12 traces + 9 metrics)
- Swagger UI acessível e completo
- Response time < 500ms (p95)

✅ **Integração:**
- Orchestrator → Observability (traces de execução)
- Skill Runtime → Observability (traces de tools)
- Backend ← Observability (queries de stats)

✅ **Background Jobs:**
- 6 scheduled jobs rodando (minute, hour, day, week, month, cleanup)
- Aggregations sendo geradas corretamente
- Retention policies executando

✅ **Dados:**
- Traces sendo persistidos
- Metrics sendo agregadas
- Cleanup automático funcionando

## 🐛 Troubleshooting

**Serviço não sobe:**
```bash
docker-compose -f docker-compose.observability.yml logs <service-name>
```

**Schema não criado:**
```bash
docker exec -it agenthub-postgres psql -U postgres -d agenthub -f /docker-entrypoint-initdb.d/01-observability-schema.sql
```

**Porta já em uso:**
```bash
# Verificar portas em uso
sudo lsof -i :8080
sudo lsof -i :8082
sudo lsof -i :8083
sudo lsof -i :9090

# Matar processo
sudo kill -9 <PID>
```

**Limpar tudo e recomeçar:**
```bash
docker-compose -f docker-compose.observability.yml down -v
docker volume rm agenthub-postgres-data
docker-compose -f docker-compose.observability.yml up -d
```

## 📝 Checklist Final

- [ ] Build de todos os projetos Java completado
- [ ] Imagens Docker criadas
- [ ] Stack subiu com sucesso (6 serviços)
- [ ] Health checks passando
- [ ] Schema PostgreSQL criado
- [ ] APIs REST respondendo
- [ ] Swagger UI acessível
- [ ] Integração orchestrator funcional
- [ ] Integração skill-runtime funcional
- [ ] Aggregation engine rodando
- [ ] Testes unitários passando (50 testes)
- [ ] Retention policies funcionando
- [ ] Documentação completa (README + INTEGRATION + este doc)

## 🎯 Próximos Passos

Após validação E2E completa:

1. **Performance Testing:**
   - Load test com 1000 req/s
   - Validar response times
   - Otimizar queries se necessário

2. **Monitoring:**
   - Adicionar Prometheus metrics
   - Grafana dashboards
   - Alerting rules

3. **Security:**
   - Autenticação/autorização
   - Rate limiting
   - Input validation

4. **Production:**
   - Kubernetes manifests
   - CI/CD pipelines
   - Backup/restore procedures

---

**Status:** Sprint 5 - 98% completo! 🎉

**Falta apenas:** Build + Deploy + Validação E2E
