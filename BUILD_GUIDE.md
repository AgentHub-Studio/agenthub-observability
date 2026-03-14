# AgentHub Observability - Build Guide

Guia completo para compilar e executar o AgentHub Observability Service.

## 🔧 Pré-requisitos

- **Java 21** ou superior
- **Maven 3.9+** ou Docker
- **PostgreSQL 16** (para execução local)

## 📦 Build Options

### Opção 1: Maven Local (Recomendado)

Se você tem Maven instalado localmente:

```bash
# Compilar
mvn clean compile

# Rodar testes
mvn test

# Empacotar (gera JAR)
mvn clean package

# Pular testes ao empacotar (mais rápido)
mvn clean package -DskipTests
```

### Opção 2: Docker Maven (Quando Docker volume mount funciona)

```bash
# Usando o script build.sh
./build.sh compile   # Compilar
./build.sh test      # Rodar testes
./build.sh package   # Empacotar

# Ou diretamente com Docker
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  maven:3.9.12-amazoncorretto-21 \
  mvn clean package
```

### Opção 3: Maven Wrapper (se disponível)

```bash
# Se houver mvnw no projeto
./mvnw clean package
```

### Opção 4: Build em Container Separado (Workaround para problemas de volume)

```bash
# 1. Criar um container temporário
docker create --name maven-build maven:3.9.12-amazoncorretto-21

# 2. Copiar código para o container
docker cp . maven-build:/app

# 3. Rodar build dentro do container
docker start maven-build
docker exec maven-build sh -c "cd /app && mvn clean package"

# 4. Copiar JAR compilado de volta
docker cp maven-build:/app/target/agenthub-observability-0.0.1-SNAPSHOT.jar ./target/

# 5. Limpar
docker rm -f maven-build
```

## 🧪 Testes

### Rodar Todos os Testes

```bash
mvn test
```

### Rodar Testes Específicos

```bash
# Apenas testes de repository
mvn test -Dtest=*RepositoryTest

# Apenas testes de service
mvn test -Dtest=*ServiceTest

# Teste específico
mvn test -Dtest=ExecutionTraceRepositoryTest
```

### Coverage Report

```bash
mvn test jacoco:report

# Relatório em: target/site/jacoco/index.html
```

### Testes com Logs Detalhados

```bash
mvn test -X  # Debug mode
```

## 🐳 Docker Image

### Build da Imagem

```bash
# Certifique-se que o JAR foi gerado primeiro
mvn clean package -DskipTests

# Build da imagem Docker
docker build -t agenthub-observability:latest .

# Ou com tag específica
docker build -t agenthub-observability:1.0.0 .
```

### Testar a Imagem

```bash
# Rodar container standalone (requer PostgreSQL)
docker run -d \
  --name agenthub-observability \
  -p 8083:8083 \
  -e SPRING_R2DBC_URL=r2dbc:postgresql://host.docker.internal:5432/agenthub \
  -e SPRING_R2DBC_USERNAME=postgres \
  -e SPRING_R2DBC_PASSWORD=postgres \
  agenthub-observability:latest

# Ver logs
docker logs -f agenthub-observability

# Health check
curl http://localhost:8083/actuator/health

# Parar e remover
docker stop agenthub-observability
docker rm agenthub-observability
```

## 🚀 Execução Local

### Com Maven

```bash
# Executar direto (sem build)
mvn spring-boot:run

# Com profile específico
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Com variáveis de ambiente
SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5432/agenthub \
SPRING_R2DBC_USERNAME=postgres \
SPRING_R2DBC_PASSWORD=postgres \
mvn spring-boot:run
```

### Com JAR Compilado

```bash
# Após mvn package
java -jar target/agenthub-observability-0.0.1-SNAPSHOT.jar

# Com profile
java -jar target/agenthub-observability-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# Com variáveis de ambiente
export SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5432/agenthub
export SPRING_R2DBC_USERNAME=postgres
export SPRING_R2DBC_PASSWORD=postgres
java -jar target/agenthub-observability-0.0.1-SNAPSHOT.jar
```

## 🐘 PostgreSQL Setup

### Via Docker

```bash
# Subir PostgreSQL
docker run -d \
  --name postgres-observability \
  -p 5432:5432 \
  -e POSTGRES_DB=agenthub \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:16-alpine

# Aguardar PostgreSQL iniciar (5-10 segundos)
sleep 10

# Criar schema
docker exec -i postgres-observability psql -U postgres -d agenthub < src/main/resources/schema.sql

# Verificar tabelas criadas
docker exec -it postgres-observability psql -U postgres -d agenthub -c "\dt"
```

### Via docker-compose

```bash
# Usar o docker-compose.observability.yml
docker-compose -f docker-compose.observability.yml up -d postgres

# Criar schema (se não foi criado automaticamente)
docker exec -i agenthub-postgres psql -U postgres -d agenthub < src/main/resources/schema.sql
```

## 🔍 Troubleshooting

### Problema: "The goal you specified requires a project to execute but there is no POM"

**Causa:** Volume mount do Docker não está funcionando corretamente.

**Solução:** Use a Opção 4 (Build em Container Separado) ou instale Maven localmente.

### Problema: "Cannot find symbol" ou erros de compilação

**Causa:** Lombok não está processando anotações corretamente.

**Solução:**
```bash
# Limpar e rebuildar
mvn clean install -DskipTests

# Se ainda não funcionar, verificar se IDE tem plugin Lombok
# Para IntelliJ: Settings → Plugins → Lombok
# Para Eclipse: Instalar Lombok via https://projectlombok.org/
```

### Problema: Testes falhando com "Connection refused"

**Causa:** Banco de dados de teste (H2) não está configurado.

**Solução:** Os testes usam H2 em memória, não PostgreSQL. Se ainda falhar:
```bash
# Verificar se application-test.properties existe
cat src/test/resources/application-test.properties

# Deve ter:
# spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1
```

### Problema: "Java version mismatch"

**Causa:** Projeto requer Java 21.

**Solução:**
```bash
# Verificar versão do Java
java -version

# Se < 21, instalar Java 21:
# - sdkman: sdk install java 21-tem
# - homebrew (Mac): brew install openjdk@21
# - apt (Linux): apt install openjdk-21-jdk

# Configurar JAVA_HOME
export JAVA_HOME=/path/to/java21
export PATH=$JAVA_HOME/bin:$PATH
```

### Problema: "Port 8083 already in use"

**Causa:** Porta já está sendo usada.

**Solução:**
```bash
# Verificar processo usando a porta
lsof -i :8083
# ou
netstat -an | grep 8083

# Matar processo
kill -9 <PID>

# Ou usar porta diferente
java -jar target/agenthub-observability-0.0.1-SNAPSHOT.jar --server.port=8084
```

## ✅ Validação do Build

### Checklist Pós-Build

```bash
# 1. Verificar que JAR foi criado
ls -lh target/agenthub-observability-*.jar

# 2. Verificar que JAR contém classes
jar tf target/agenthub-observability-0.0.1-SNAPSHOT.jar | grep ObservabilityApplication

# 3. Verificar que testes passaram
grep "Tests run" target/surefire-reports/*.txt

# 4. Verificar dependências
mvn dependency:tree | head -50

# 5. Health check após iniciar
curl http://localhost:8083/actuator/health

# 6. Swagger UI
curl http://localhost:8083/swagger-ui.html

# 7. Endpoint de traces
curl http://localhost:8083/api/traces/executions?tenantId=1

# 8. Endpoint de metrics
curl http://localhost:8083/api/metrics/events?tenantId=1
```

## 📊 Métricas de Build

**Build esperado:**
- Compile time: ~30-60s (primeira vez), ~10-20s (subsequente)
- Test time: ~20-30s (50 testes)
- Package time: ~40-90s total
- JAR size: ~40-50 MB

**Resultados esperados dos testes:**
```
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
```

## 🚢 Deploy

Após build bem-sucedido, siga o guia [E2E_VALIDATION.md](E2E_VALIDATION.md) para deploy e validação E2E completa.

---

**Status:** Sprint 5 - 98% → 100% após build bem-sucedido  
**Última atualização:** 2026-03-14
