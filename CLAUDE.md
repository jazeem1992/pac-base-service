# CLAUDE.md – Engineering rules for PAC 2.0 microservices

This file is the contract for AI-assisted development (Claude Code, Copilot, …) and humans alike.
Every change MUST follow these rules. When in doubt, copy the sample `Customer` feature.

## Stack

Java 21 (OpenJDK) · Spring Boot 4.0.x · Spring Cloud 2025.1.x (Consul discovery/config, LoadBalancer,
Resilience4j) · WebClient (Reactor Netty) · AWS RDS PostgreSQL via RDS Proxy · Flyway · Redis cache ·
AWS Secrets Manager · MapStruct · Lombok · JUnit 5 / Mockito / AssertJ · H2 (tests only) · Maven wrapper.

Upgrade Spring Boot and Spring Cloud **together** (official compatibility matrix).

## Commands

```bash
./mvnw clean verify          # build + unit tests + integration tests + coverage gate (must pass before every commit)
./mvnw test                  # unit + slice tests only (*Test.java)
./mvnw spring-boot:run       # run locally (profile "local": local PostgreSQL + Redis)
./mvnw sonar:sonar -Dsonar.host.url=... -Dsonar.token=...
```

## Package structure (`com.allianz.pac.<service>`)

| Package | Content |
|---|---|
| `client` | Outbound HTTP clients extending `BaseWebClient` (+ `WebClientFilters`) |
| `common.annotation` | `@TransactionalWrite`, `@TransactionalRead`, `@Loggable` |
| `common.constants` | `AppConstants`, `MessageKeys`, `CacheNames`, `ResilienceInstances` |
| `common.filter` | Servlet filters (correlation id, request logging) |
| `common.logging` | `LoggingAspect`, `MdcTaskDecorator`, lifecycle logger |
| `common.response` | `ApiResponse` envelope + `ApiResponseFactory` |
| `config` | `@Configuration` classes; `config.properties` = `@ConfigurationProperties` records |
| `controller` | REST controllers (thin, no business logic) |
| `dto.request` / `dto.response` | Java records, validated with Jakarta Validation |
| `exception` | `ErrorCode`, `BaseException` hierarchy, `GlobalExceptionHandler`, `CustomErrorController` |
| `mapper` | MapStruct mappers (entity ⇄ DTO) |
| `model` | JPA entities extending `BaseEntity` |
| `repository` | Spring Data repositories |
| `service` / `service.impl` | Service interfaces and implementations |
| `util` | Stateless helpers (`final` class, private constructor) |

## Mandatory rules

### Responses
- Every endpoint returns `ResponseEntity<ApiResponse<T>>` built with `ApiResponseFactory` (`ok`, `created`, `success`).
- Envelope: `status` (SUCCESS/FAILURE), `statusCode`, `message`, `data`, `timestamp`, `correlationId`.
- `data` is never null (`{}` when empty). On validation errors `data` = `{field: message}`.
- The HTTP status code always equals `statusCode`.

### Exceptions
- Throw only subclasses of `BaseException` (`ResourceNotFoundException`, `DuplicateResourceException`,
  `BusinessException`, `ExternalServiceException`, `ExternalServiceClientException`) – never `RuntimeException`.
- New error case → add an `ErrorCode` entry + message key in `messages.properties`.
- Never catch and swallow exceptions; never log and rethrow (the handler logs once).
- Never return stack traces or internal messages to clients.

### No hard-coded values
- URLs, timeouts, sizes, limits, TTLs, retry counts, page sizes → `application*.yml`, bound via
  `@ConfigurationProperties` records in `config.properties` (validated with `@Validated`).
- User-facing texts → `messages.properties` (keys in `MessageKeys` / `ErrorCode`).
- Secrets → environment variables / AWS Secrets Manager only. Never commit secrets.

### Logging
- Do NOT write manual "method start/end" logs: `LoggingAspect` logs START/END/FAILED + duration for
  controllers, services, clients and repositories (`@Loggable` for other beans).
- Use Lombok `@Slf4j`; parameterized messages (`log.info("x {}", v)`), never string concatenation.
- Pass user input through `LogSanitizer.clean(...)`; never log secrets or PII (masked fields: `app.logging.masked-fields`).
- No `System.out` / `printStackTrace`.

### Transactions & rollback
- Service classes: `@TransactionalRead` at class level; modifying methods `@TransactionalWrite`
  (rolls back on ANY exception, timeout from `app.transaction.timeout-seconds`).
- Never hold a transaction while calling a remote service (`@Transactional(propagation = NOT_SUPPORTED)`).
- Multi-item operations are all-or-nothing in one transaction (see `createCustomers`).
- Cross-service consistency: use compensation (saga) – there are no distributed transactions.

### Resilience (annotation driven, configured in `resilience4j.*.instances.<name>`)
- Every outbound call: `@Retry` + `@CircuitBreaker` + `@Bulkhead` with an instance name from `ResilienceInstances`.
- Public APIs under heavy load: `@RateLimiter` (429 on rejection).
- Async (CompletableFuture) operations may add `@TimeLimiter`.
- Only `ExternalServiceException` (5xx/timeouts) is retried; `ExternalServiceClientException` (4xx) is not.

### High volume / long-running work
- Virtual threads are enabled (`spring.threads.virtual.enabled`).
- Slow work → `@Async` service method returning `CompletableFuture<T>`; controller returns the future
  (servlet thread released; timeout `spring.mvc.async.request-timeout`).
- Always paginate list endpoints (`Pageable`, limits in `spring.data.web.pageable.*`).
- Bulk inserts: sequence ids with `allocationSize` = sequence increment (enables JDBC batching).

### Persistence
- Entities extend `BaseEntity` (audit columns + `@Version` optimistic locking). No Lombok `@Data` on entities.
- Schema changes only through new Flyway files `V<n>__<description>.sql` (never edit applied ones).
  SQL must run on PostgreSQL and H2 (PostgreSQL mode).
- `ddl-auto=validate`, `open-in-view=false`.

### Caching
- Every cache: constant in `CacheNames`, value type registered in `CacheConfig.CACHE_VALUE_TYPES`,
  TTL in `app.cache.ttl.<name>`. Cached values are DTO records, never entities.

### JSON
- Unknown JSON properties are ignored (`spring.jackson.deserialization.fail-on-unknown-properties=false`),
  for incoming requests and downstream responses.

### Code style (Sonar)
- Constructor injection only (`@RequiredArgsConstructor`), `private final` fields, no field `@Autowired`.
- **Every class and every method (including private and test methods) has Javadoc** describing its purpose,
  parameters, return value and thrown exceptions.
- DTOs are records; collections are copied defensively.
- Methods ≤ ~30 lines, cognitive complexity ≤ 15, no duplicated literals (use constants).
- No unused code, no commented-out code, no wildcard imports.

## Testing (mandatory for every requirement)
- Every new/changed class gets tests in the same change; `./mvnw verify` must be green.
- Coverage gate (JaCoCo, unit + IT merged): line ≥ 80 %, branch ≥ 60 %.
- Test types:
  - Unit (`*Test`): services with Mockito, utilities, filters, clients with stubbed `ExchangeFunction`.
  - Web slice (`@WebMvcTest`): request mapping, validation, envelope, error mapping.
  - JPA slice (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + profile `test`).
  - Integration (`*IT`, `@SpringBootTest` + profile `test`): full flows, rollback, async, resilience.
- No Docker/network required: H2 (PostgreSQL mode), simple cache, Consul disabled, downstream stubbed.
- Test names: `@DisplayName` describing behaviour; AAA structure (arrange / act / assert).

## Adding a new feature – checklist
1. Flyway migration `V<n>__*.sql`.
2. Entity in `model` (extends `BaseEntity`), repository in `repository`.
3. Request/response records in `dto`, MapStruct mapper in `mapper`.
4. Service interface + `impl` with transactions, caching, exceptions.
5. Downstream calls: endpoint in `app.downstream.services`, WebClient bean in `WebClientConfig`,
   client extending `BaseWebClient` with resilience annotations + `resilience4j` instance config.
6. Controller using `ApiResponseFactory`; message keys in `MessageKeys` + `messages.properties`.
7. New error cases → `ErrorCode` + messages.
8. Unit, slice and integration tests; run `./mvnw verify`.
9. Update `README.md` if configuration/env variables changed.
