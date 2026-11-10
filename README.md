# PAC Base Service

Base microservice template of the **PAC 2.0** platform. Every new PAC microservice starts as a copy
of this project. Engineering rules are in [CLAUDE.md](CLAUDE.md).

## Technology

| Area | Choice |
|---|---|
| Runtime | OpenJDK 21, Spring Boot 4.0.9, Spring Cloud 2025.1.4 |
| Web | Spring MVC on **virtual threads**; WebClient (Reactor Netty, pooled) for outbound calls |
| Database | AWS RDS PostgreSQL through **RDS Proxy**, HikariCP, Flyway, Spring Data JPA |
| Cache | Redis (Spring Cache, JSON values, per-cache TTL, transaction-aware) |
| Discovery / config | Consul (service registration, client-side load balancing, KV config) |
| Secrets | AWS Secrets Manager (`spring.config.import`) |
| Resilience | Resilience4j annotations: `@Retry`, `@CircuitBreaker`, `@Bulkhead`, `@RateLimiter`, `@TimeLimiter` |
| Observability | Actuator, Prometheus metrics, structured JSON logs (ECS), correlation ids |
| API docs | springdoc OpenAPI – `/swagger-ui.html` (disabled in preprod/prod) |
| Quality | JaCoCo gate (line ≥ 80 %, branch ≥ 60 %), Sonar Maven plugin, Maven Enforcer |

## Response format

Every response – success or failure – uses the same envelope:

```json
{
  "status": "SUCCESS",
  "statusCode": 200,
  "message": "Customer fetched successfully",
  "data": { "id": 1, "email": "jane@test.com" },
  "timestamp": "2026-11-10T10:15:30.123Z",
  "correlationId": "a1b2c3d4-..."
}
```

```json
{
  "status": "FAILURE",
  "statusCode": 400,
  "message": "Validation failed",
  "data": { "email": "must be a well-formed email address" },
  "timestamp": "2026-11-10T10:15:30.123Z",
  "correlationId": "a1b2c3d4-..."
}
```

`data` is `{}` when there is nothing to return. Send `X-Correlation-Id` to correlate your call; it
is returned in the response and forwarded to downstream services.

## Profiles

| Profile | Purpose | Secrets / infra |
|---|---|---|
| `local` (default) | developer machine | local PostgreSQL + Redis, Consul off, downstream via static URLs |
| `dev`, `qa`, `uat` | AWS environments | Consul, RDS Proxy, Redis (TLS), Secrets Manager |
| `preprod`, `prod` | production-like / production | as above + Swagger off, reduced actuator exposure, method trace at DEBUG |
| `test` | automated tests only (`src/test/resources`) | H2 (PostgreSQL mode), in-memory cache, no network |

### Environment variables (AWS profiles)

| Variable | Description |
|---|---|
| `DB_PROXY_ENDPOINT` | RDS Proxy endpoint host |
| `DB_PORT` | default `5432` |
| `DB_NAME` | database name |
| `DB_SECRET_NAME` | Secrets Manager secret with keys `username` / `password` |
| `DB_SSL_MODE` | default `require` |
| `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE` | Hikari pool sizing (defaults per profile) |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED` | Redis / ElastiCache |
| `CONSUL_HOST`, `CONSUL_PORT` | Consul agent |
| `AWS_REGION` | AWS region (default `eu-central-1`) |
| `SERVER_PORT` | HTTP port (default `8080`) |

## Running locally

Prerequisites: JDK 21, a local PostgreSQL (database `pac_base`, user/password `pac`/`pac` – override
with `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`) and a local Redis.

```bash
./mvnw spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html · Health: http://localhost:8080/actuator/health

## Build & test

```bash
./mvnw clean verify
```

Runs unit tests (`*Test`), integration tests (`*IT`) and the coverage gate. No Docker or network
access is needed.

## Sample feature

`/api/v1/customers` demonstrates every convention:

| Method | Path | Shows |
|---|---|---|
| POST | `/customers` | validation, duplicate check, cache put |
| POST | `/customers/bulk` | all-or-nothing transaction (rollback), JDBC batching |
| GET | `/customers/{id}` | Redis cache, 404 handling |
| GET | `/customers?page=0&size=20&sort=lastName,asc` | pagination |
| PUT | `/customers/{id}` | optimistic locking, cache update |
| DELETE | `/customers/{id}` | cache eviction |
| GET | `/customers/{id}/credit-score` | async processing + Consul-discovered downstream call with retry / circuit breaker / bulkhead |

## Creating a new service from this template

1. Copy the project, rename the `artifactId`, `spring.application.name` and the base package
   (`com.allianz.pac.baseservice` → `com.allianz.pac.<service>`), including the pointcuts in `LoggingAspect`.
2. Remove or replace the `Customer` sample (keep it until your first feature follows the same pattern).
3. Follow the checklist in [CLAUDE.md](CLAUDE.md).
