
La recomendación es utilizar Spring Boot con Spring MVC, JPA/Hibernate para operaciones generales, `JdbcTemplate` o SQL nativo para el flujo crítico de transferencia, PostgreSQL como base de datos transaccional y NATS JetStream para desacoplar los procesos de IA, sincronización y auditoría.

## Stack tecnológico Java

| Componente | Tecnología seleccionada | Propósito y justificación |
|---|---|---|
| Lenguaje | Java 21 LTS | Versión estable con mejoras modernas de concurrencia y compatibilidad con virtual threads |
| Framework backend | Spring Boot 3.x | Desarrollo rápido de APIs empresariales, inyección de dependencias, validación, seguridad y observabilidad |
| API REST | Spring Web MVC | Adecuado para endpoints transaccionales síncronos y de baja complejidad operativa |
| Validación | Jakarta Validation + Hibernate Validator | Validación declarativa de DTOs con anotaciones como `@NotNull`, `@DecimalMin` y `@Valid` |
| Seguridad | Spring Security + JWT | Autenticación y autorización basada en roles para proteger operaciones y endpoints administrativos |
| Persistencia general | Spring Data JPA + Hibernate | Mapeo de entidades, repositorios, auditoría y operaciones CRUD |
| Persistencia crítica | Spring JDBC / `JdbcTemplate` | Control explícito de SQL, bloqueos por fila y validación de actualizaciones en transferencias |
| Base de datos | PostgreSQL 16+ | Transacciones ACID, bloqueos, índices, restricciones y concurrencia robusta |
| Migraciones | Flyway | Versionamiento de DDL, DML inicial y evolución controlada del esquema |
| Mensajería | NATS JetStream | Procesamiento asíncrono persistente para IA, Outbox y sincronización Bancs |
| Servicio IA | Python 3.12 + FastAPI | Servicio independiente para mock avanzado, reglas de riesgo o futura integración de modelo |
| ETL | Python + Pandas + SQLAlchemy | Limpieza de datos, estandarización, manejo de nulos y generación de reportes |
| Contenedores | Docker + Docker Compose | Entorno reproducible ejecutable con un comando |
| Pruebas unitarias | JUnit 5 + Mockito + AssertJ | Pruebas de servicios, validadores, reglas de negocio y errores |
| Pruebas de integración | Testcontainers | Pruebas reales contra PostgreSQL y NATS en contenedores temporales |
| Pruebas de carga | k6 | Simulación de concurrencia, TPS, latencia p95/p99 y escenarios de estrés |
| Métricas | Spring Boot Actuator + Micrometer + Prometheus | Publicación de métricas de aplicación y negocio |
| Trazabilidad | Micrometer Tracing + OpenTelemetry + Tempo | Trazas distribuidas desde la API hasta PostgreSQL, NATS e IA |
| Logs | SLF4J + Logback + Loki | Logs JSON centralizados y correlacionados por `traceId` |
| Dashboards | Grafana | Paneles de disponibilidad, rendimiento, base de datos, IA y ETL |
| CI/CD | GitHub Actions | Ejecución automática de pruebas, análisis estático y construcción Docker |

Spring Boot proporciona observabilidad basada en tres pilares: logs, métricas y trazas. Para ello utiliza Micrometer Observation, mientras que Actuator permite exponer datos operativos y OpenTelemetry puede integrarse para exportar trazas hacia un backend como Tempo. [docs.spring](https://docs.spring.io/spring-boot/reference/actuator/observability.html)

## Arquitectura modificada

La arquitectura no debe dividirse en demasiados microservicios. Para la entrega de siete días, el backend Java se organiza como un servicio principal modular y tres procesos secundarios desacoplados.

```text
                         ┌────────────────────────┐
                         │ Cliente / k6 / Postman │
                         └───────────┬────────────┘
                                     │ REST / HTTPS
                                     ▼
                    ┌────────────────────────────────┐
                    │ API Gateway / Nginx             │
                    │ Rate limiting y enrutamiento    │
                    └───────────────┬────────────────┘
                                    │
                                    ▼
        ┌───────────────────────────────────────────────────┐
        │ Transaction Service                                 │
        │ Java 21 + Spring Boot + Spring Security            │
        │                                                     │
        │ ├── API REST                                        │
        │ ├── Validación de solicitudes                       │
        │ ├── JWT y autorización                              │
        │ ├── Idempotencia                                    │
        │ ├── Reglas transaccionales                          │
        │ ├── Ledger / auditoría                              │
        │ ├── Patrón Outbox                                   │
        │ └── Métricas y trazas                               │
        └───────────────┬──────────────────┬─────────────────┘
                        │                  │
                        │ SQL transaccional │ Evento asíncrono
                        ▼                  ▼
            ┌─────────────────┐   ┌─────────────────────┐
            │ PostgreSQL      │   │ NATS JetStream      │
            │ accounts        │   │ transaction.events  │
            │ transactions    │   │ ai.requests         │
            │ ledger_entries  │   │ bancs.sync          │
            │ outbox_events   │   └──────┬──────┬───────┘
            └─────────────────┘          │      │
                                         ▼      ▼
                              ┌────────────┐  ┌──────────────┐
                              │ AI Service │  │ Sync Worker  │
                              │ FastAPI    │  │ Java/Python  │
                              └────────────┘  └──────┬───────┘
                                                       │
                                                       ▼
                                             ┌───────────────────┐
                                             │ Bancs Mock / ETL  │
                                             └───────────────────┘

        ┌───────────────────────────────────────────────────┐
        │ Prometheus + Grafana + Loki + Tempo               │
        │ Métricas + Logs + Trazabilidad distribuida        │
        └───────────────────────────────────────────────────┘
```

La transferencia se ejecuta únicamente entre el `Transaction Service` y PostgreSQL. NATS, la IA y Bancs se invocan posteriormente mediante eventos, por lo que su indisponibilidad no debe impedir la confirmación del movimiento financiero.

JetStream debe utilizarse en lugar de NATS Core para eventos relevantes, porque añade persistencia, reproducción y semántica de entrega al menos una vez. Debido a esa posibilidad de reentrega, los consumidores deben ser idempotentes. [docs.nats](https://docs.nats.io/concepts/jetstream)

## Estructura del backend Java

Se recomienda aplicar una arquitectura por capas con enfoque modular. No es necesario implementar una arquitectura hexagonal completa si el tiempo es limitado, pero sí separar claramente API, aplicación, dominio e infraestructura.

```text
transaction-service/
├── src/
│   ├── main/
│   │   ├── java/com/financial/platform/
│   │   │   ├── FinancialApplication.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── NatsConfig.java
│   │   │   │   ├── ObservabilityConfig.java
│   │   │   │   └── AsyncConfig.java
│   │   │   │
│   │   │   ├── transaction/
│   │   │   │   ├── api/
│   │   │   │   │   ├── TransactionController.java
│   │   │   │   │   ├── TransferRequest.java
│   │   │   │   │   ├── TransferResponse.java
│   │   │   │   │   └── TransactionExceptionHandler.java
│   │   │   │   │
│   │   │   │   ├── application/
│   │   │   │   │   ├── TransferService.java
│   │   │   │   │   ├── TransferUseCase.java
│   │   │   │   │   └── TransactionMapper.java
│   │   │   │   │
│   │   │   │   ├── domain/
│   │   │   │   │   ├── Transaction.java
│   │   │   │   │   ├── TransactionStatus.java
│   │   │   │   │   ├── LedgerEntry.java
│   │   │   │   │   ├── Account.java
│   │   │   │   │   └── TransferBusinessException.java
│   │   │   │   │
│   │   │   │   └── infrastructure/
│   │   │   │       ├── TransactionJdbcRepository.java
│   │   │   │       ├── AccountJdbcRepository.java
│   │   │   │       ├── LedgerJpaRepository.java
│   │   │   │       ├── OutboxJpaRepository.java
│   │   │   │       └── NatsEventPublisher.java
│   │   │   │
│   │   │   ├── outbox/
│   │   │   │   ├── OutboxPublisherJob.java
│   │   │   │   ├── OutboxEvent.java
│   │   │   │   └── OutboxEventStatus.java
│   │   │   │
│   │   │   ├── audit/
│   │   │   │   ├── AuditLog.java
│   │   │   │   └── AuditService.java
│   │   │   │
│   │   │   └── shared/
│   │   │       ├── exception/
│   │   │       ├── security/
│   │   │       └── observability/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-docker.yml
│   │       └── db/migration/
│   │           ├── V1__create_core_tables.sql
│   │           ├── V2__create_outbox_tables.sql
│   │           └── V3__seed_test_data.sql
│   │
│   └── test/
│       └── java/com/financial/platform/
│           ├── transaction/
│           ├── integration/
│           └── architecture/
│
├── Dockerfile
├── pom.xml
└── README.md
```

La organización anterior permite que `transaction` sea un módulo funcional completo, mientras que `outbox`, `audit` y `shared` atienden responsabilidades transversales.

## Fase 1: Infraestructura Java

**Objetivo:** levantar el backend Spring Boot, PostgreSQL, NATS y las herramientas de observabilidad mediante un comando.

Servicios de `docker-compose.yml`:

```text
transaction-service
postgres
nats
ai-service
prometheus
grafana
loki
promtail
tempo
postgres-exporter
cadvisor
```

Archivo `Dockerfile` del backend:

```dockerfile
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline

COPY src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Dependencias Maven principales:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>

    <dependency>
        <groupId>io.nats</groupId>
        <artifactId>jnats</artifactId>
        <version>2.20.5</version>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

El backend debe exponer como mínimo:

```text
GET  /actuator/health
GET  /actuator/info
GET  /actuator/prometheus
GET  /swagger-ui/index.html
POST /api/v1/transactions/transfers
GET  /api/v1/transactions/{transactionId}
GET  /api/v1/recommendations/{transactionId}
```

## Fase 2: Core transaccional con Spring Boot

El servicio principal debe implementar transferencias con aislamiento, consistencia, idempotencia y auditoría. En este punto no se debe depender únicamente de JPA para el débito y crédito, porque la operación requiere controlar exactamente los bloqueos, el orden de actualización y las filas afectadas.

Spring Framework proporciona manejo declarativo y programático de transacciones. Para flujos imperativos, el propio framework recomienda `TransactionTemplate`; también es posible usar directamente un `PlatformTransactionManager`. [docs.spring](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)

### Modelo de datos mínimo

| Tabla | Función |
|---|---|
| `accounts` | Estado y saldo disponible de cada cuenta |
| `transactions` | Cabecera e identificación de cada transferencia |
| `ledger_entries` | Movimientos contables de débito y crédito |
| `idempotency_keys` | Prevención de solicitudes duplicadas |
| `outbox_events` | Eventos persistidos antes de publicarse en NATS |
| `audit_logs` | Registro auditable de acciones y resultados |
| `recommendations` | Recomendaciones generadas por el servicio IA |

DDL simplificado:

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    account_number VARCHAR(32) NOT NULL UNIQUE,
    balance NUMERIC(18, 2) NOT NULL CHECK (balance >= 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    source_account_id UUID NOT NULL REFERENCES accounts(id),
    destination_account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
```

### DTO de transferencia

```java
public record TransferRequest(

    @NotNull(message = "La cuenta de origen es obligatoria")
    UUID sourceAccountId,

    @NotNull(message = "La cuenta de destino es obligatoria")
    UUID destinationAccountId,

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    @Digits(integer = 16, fraction = 2)
    BigDecimal amount,

    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe usar formato ISO-4217")
    String currency,

    @Size(max = 250)
    String description
) {}
```

### Endpoint principal

```java
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransferService transferService;

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        return transferService.executeTransfer(idempotencyKey, request);
    }
}
```

### Flujo interno obligatorio

```text
1. Validar DTO y JWT.
2. Verificar que la cuenta origen y destino sean distintas.
3. Consultar si la Idempotency-Key fue procesada.
4. Abrir transacción SQL.
5. Bloquear las dos cuentas con SELECT ... FOR UPDATE.
6. Bloquear siempre en orden ascendente de UUID o account_id.
7. Verificar saldo, estado y moneda.
8. Debitar la cuenta origen.
9. Acreditar la cuenta destino.
10. Registrar transactions.
11. Registrar dos ledger_entries: DEBIT y CREDIT.
12. Registrar el evento en outbox_events.
13. Confirmar COMMIT.
14. Devolver respuesta inmediatamente.
15. Publicar eventos desde un worker posterior.
```

Para proteger datos ante escrituras concurrentes, PostgreSQL permite utilizar bloqueos explícitos como `SELECT ... FOR UPDATE`, además de niveles de aislamiento y detección de deadlocks. Esto resulta esencial cuando varias transferencias podrían modificar la misma cuenta simultáneamente. [postgresql](https://www.postgresql.org/docs/current/mvcc.html)

### Implementación recomendada del servicio

```java
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionTemplate transactionTemplate;
    private final AccountJdbcRepository accountRepository;
    private final TransactionJdbcRepository transactionRepository;
    private final LedgerJdbcRepository ledgerRepository;
    private final OutboxJdbcRepository outboxRepository;
    private final IdempotencyRepository idempotencyRepository;

    public TransferResponse executeTransfer(
            String idempotencyKey,
            TransferRequest request
    ) {
        var existingTransaction = idempotencyRepository
                .findTransactionByKey(idempotencyKey);

        if (existingTransaction.isPresent()) {
            return TransferResponse.from(existingTransaction.get(), true);
        }

        return transactionTemplate.execute(status -> {
            validateDifferentAccounts(request);

            var lockedAccounts = accountRepository.lockAccountsInStableOrder(
                    request.sourceAccountId(),
                    request.destinationAccountId()
            );

            var sourceAccount = lockedAccounts.source();
            var destinationAccount = lockedAccounts.destination();

            validateTransfer(sourceAccount, destinationAccount, request);

            accountRepository.debit(sourceAccount.id(), request.amount());
            accountRepository.credit(destinationAccount.id(), request.amount());

            var transaction = transactionRepository.create(
                    sourceAccount.id(),
                    destinationAccount.id(),
                    request.amount(),
                    request.currency(),
                    idempotencyKey
            );

            ledgerRepository.createDebit(
                    transaction.id(),
                    sourceAccount.id(),
                    request.amount()
            );

            ledgerRepository.createCredit(
                    transaction.id(),
                    destinationAccount.id(),
                    request.amount()
            );

            outboxRepository.storeTransactionCompletedEvent(transaction);

            return TransferResponse.from(transaction, false);
        });
    }
}
```

La implementación debe incluir reintentos controlados para errores transitorios, como serialización fallida o deadlocks. No se recomienda reintentar indefinidamente; una estrategia razonable para la demostración es aplicar entre dos y tres reintentos con backoff corto y registrar cada intento en las métricas.

## Fase 3: Integración con Bancs y ETL

La integración con Bancs debe diseñarse como un proceso desacoplado. El backend Java no debe consultar Bancs durante la solicitud de transferencia, porque una dependencia lenta o no disponible afectaría directamente la latencia y disponibilidad del core.

Flujo recomendado:

```text
Transferencia confirmada
        │
        ▼
outbox_events
        │
        ▼
OutboxPublisherJob en Spring Boot
        │
        ▼
NATS: transaction.completed
        │
        ▼
Sync Worker
        │
        ▼
Bancs Mock / API heredada / lote de sincronización
```

El `OutboxPublisherJob` puede ejecutarse mediante `@Scheduled` cada pocos segundos. Debe recuperar solo eventos pendientes, marcarlos como publicados únicamente después de confirmar la publicación en NATS y aumentar el contador de reintentos si ocurre un error.

```java
@Component
@RequiredArgsConstructor
public class OutboxPublisherJob {

    private final OutboxService outboxService;

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:3000}")
    public void publishPendingEvents() {
        outboxService.publishPendingEvents();
    }
}
```

Para el ETL, se mantiene Python porque Pandas resulta más apropiado para procesar archivos de lote y transformar datos heterogéneos. Java puede consumir el resultado del proceso ETL mediante tablas staging o archivos CSV validados.

El ETL debe realizar:

- Lectura de CSV, JSON o tablas de Bancs Mock.
- Estandarización de fechas a UTC e ISO 8601.
- Conversión de montos a `Decimal`.
- Detección de campos nulos.
- Eliminación o marcación de duplicados.
- Validación de moneda, formato de cuenta y códigos de operación.
- Separación entre registros válidos y rechazados.
- Persistencia de los errores con su motivo técnico.

## Fase 4: Servicio de IA y asincronía

El servicio de IA debe permanecer en Python con FastAPI, mientras que Spring Boot consume los resultados mediante eventos o peticiones no bloqueantes desde un worker.

Flujo recomendado:

```text
transaction.completed
        │
        ▼
NATS JetStream
        │
        ▼
AI Consumer Worker
        │
        ├── Envía datos anonimizados a AI Service
        │
        ├── Almacena recommendation en PostgreSQL
        │
        └── Publica ai.recommendation.completed
```

No se recomienda que `TransactionController` invoque al servicio de IA durante el `POST /transfers`. La respuesta al cliente debe depender solo de la confirmación de la transacción financiera.

El mock de IA debe permitir demostrar:

- Latencia variable entre 100 y 800 ms.
- Errores configurables.
- Puntaje de riesgo generado por reglas simples.
- Versionado de modelo, por ejemplo `mock-risk-v1`.
- Métricas de solicitudes, errores y tiempo de procesamiento.
- Persistencia de resultados asociados a `transactionId`.

En Java, las operaciones de ejecución secundaria pueden implementarse con `@Async`, pero para procesos críticos se debe preferir NATS JetStream, ya que una tarea local en memoria se perdería si la instancia se reinicia. Java 21 permite virtual threads y Spring Boot puede habilitarlos mediante `spring.threads.virtual.enabled=true`; sin embargo, esta opción no sustituye una cola persistente ni resuelve la necesidad de idempotencia. [docs.spring](https://docs.spring.io/spring-boot/reference/features/spring-application.html)

## Fase 5: Observabilidad Java

Spring Boot debe incluir Actuator desde el inicio. La configuración mínima en `application.yml` debe exponer salud, información y métricas Prometheus.

```yaml
spring:
  application:
    name: transaction-service

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
  metrics:
    tags:
      application: transaction-service
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://tempo:4318/v1/traces

logging:
  pattern:
    level: "%5p [traceId=%X{traceId:-}, spanId=%X{spanId:-}]"
```

Spring Boot instrumenta observaciones con Micrometer para solicitudes HTTP, clientes HTTP, tareas programadas y otros componentes; cada observación puede producir métricas y spans que facilitan seguir una operación entre servicios. [docs.spring](https://docs.spring.io/spring-framework/reference/integration/observability.html)

Métricas de negocio personalizadas:

```java
@Component
@RequiredArgsConstructor
public class TransactionMetrics {

    private final MeterRegistry meterRegistry;

    public void registerSuccessfulTransfer() {
        meterRegistry.counter(
                "financial_transactions_total",
                "status", "completed"
        ).increment();
    }

    public void registerRejectedTransfer(String reason) {
        meterRegistry.counter(
                "financial_transactions_total",
                "status", "rejected",
                "reason", reason
        ).increment();
    }

    public void registerDeadlock() {
        meterRegistry.counter("financial_database_deadlocks_total").increment();
    }
}
```

No se deben usar etiquetas de alta cardinalidad en Prometheus. Por ejemplo, no se debe incluir `transactionId`, `accountId`, correo o número de cuenta como etiqueta de métrica. Esos identificadores deben aparecer únicamente en logs estructurados y trazas, idealmente enmascarados cuando contengan datos sensibles.

Dashboards Grafana:

| Dashboard | Indicadores principales |
|---|---|
| Salud de infraestructura | Estado de contenedores, CPU, RAM, reinicios |
| API Spring Boot | RPS, TPS, p50, p95, p99, HTTP 4xx y 5xx |
| Operación financiera | Transferencias completadas, rechazadas, idempotentes y reintentadas |
| PostgreSQL | Pool, conexiones activas, locks, deadlocks, consultas lentas |
| NATS JetStream | Mensajes pendientes, consumidores, reintentos y redeliveries |
| IA | Latencia, errores, recomendaciones generadas y profundidad de cola |
| ETL | Duración de lote, registros válidos, rechazados, nulos y duplicados |

## Fase 6: Pruebas e incidente

Las pruebas deben realizarse en tres niveles.

| Nivel | Herramientas | Casos mínimos |
|---|---|---|
| Unitarias | JUnit 5, Mockito, AssertJ | Validación, saldo insuficiente, reglas de negocio, idempotencia |
| Integración | Spring Boot Test, Testcontainers, PostgreSQL | Persistencia, Flyway, transacciones, bloqueo de cuentas y Outbox |
| Carga | k6 | Transferencias concurrentes, p95/p99, tasa de error y comportamiento ante saturación |

Pruebas de concurrencia prioritarias:

1. Crear una cuenta origen con saldo de 100 USD.
2. Enviar diez transferencias concurrentes de 20 USD.
3. Verificar que solo cinco se completen.
4. Verificar que el saldo final sea 0 USD.
5. Verificar que no exista saldo negativo.
6. Verificar que cada transacción completada tenga un débito y un crédito.
7. Verificar que los eventos Outbox coincidan con las transacciones confirmadas.

Incidente controlado recomendado:

```text
Pico de solicitudes concurrentes
        │
        ▼
Aumento de bloqueos en cuentas
        │
        ▼
Deadlocks o agotamiento del pool JDBC
        │
        ▼
Latencia p95 supera 2 segundos
        │
        ▼
Alertas Grafana y trazas en Tempo
        │
        ▼
Mitigación: rate limiting + ajuste de pool + orden estable de locks
```

El informe post mortem debe documentar impacto, línea de tiempo, evidencia de Grafana/Tempo/Loki, causa raíz, acciones inmediatas y medidas preventivas.

## Cronograma de siete días

| Día | Actividades principales | Entregable |
|---|---|---|
| Día 1 | Crear proyecto Spring Boot, Docker Compose, PostgreSQL, NATS, Prometheus y Grafana | Entorno levantado con `docker compose up --build` |
| Día 2 | Flyway, tablas, entidades, Spring Security, JWT, Swagger y validación | API base protegida y base de datos versionada |
| Día 3 | Transferencias con `TransactionTemplate`, SQL bloqueante, ledger e idempotencia | Core financiero correcto bajo casos normales |
| Día 4 | Patrón Outbox, publicador NATS, Bancs Mock y ETL Python | Eventos persistentes y proceso de sincronización desacoplado |
| Día 5 | Servicio IA FastAPI, consumidor NATS y almacenamiento de recomendaciones | IA asíncrona sin bloquear transferencias |
| Día 6 | Actuator, Micrometer, OpenTelemetry, Loki, Tempo, k6 y simulación de incidente | Dashboards, trazas, alertas y reporte de carga |
| Día 7 | Documentación, evidencias, video, revisión de seguridad y entrega | Repositorio reproducible y documentación final |

## Resultado esperado

El resultado final debe ser una plataforma transaccional demostrable donde Spring Boot administra el núcleo financiero y PostgreSQL garantiza consistencia. La principal evidencia de calidad no será únicamente el volumen alcanzado, sino demostrar que el sistema:

- Procesa transferencias de forma atómica.
- Evita saldo negativo y doble débito bajo concurrencia.
- Reutiliza una respuesta ante solicitudes con la misma clave de idempotencia.
- Persiste eventos mediante Outbox antes de su publicación.
- No espera a la IA ni a Bancs para confirmar la transferencia.
- Expone métricas operativas y de negocio.
- Permite rastrear una operación completa con `traceId` y `transactionId`.
- Tiene pruebas de integración contra PostgreSQL real mediante Testcontainers.
- Documenta un incidente con evidencia de logs, métricas y trazas.