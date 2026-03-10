# AgentHub Observability

**Traces, Metrics and Logs** - Sistema de observabilidade completa.

## Responsabilidades
- Capturar traces de execuções
- Registrar métricas (latência, tokens, custos)
- Armazenar prompts/responses LLM
- Dashboard operacional
- APIs de consulta

## API
- GET /api/v1/executions/{id}/trace
- GET /api/v1/executions/{id}/metrics
- GET /api/v1/tenants/{id}/metrics

MIT License
