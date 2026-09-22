# SmartBancsApp - Plataforma Financiera Transaccional en Tiempo Real con IA y Observabilidad

[![Java 21](https://img.shields.io/badge/Java-21_LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.4.1](https://img.shields.io/badge/Spring_Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![NATS JetStream](https://img.shields.io/badge/NATS-JetStream-green.svg)](https://nats.io/)
[![Docker Compose](https://img.shields.io/badge/Docker_Compose-12_Services-blue.svg)](https://www.docker.com/)

**SmartBancsApp** es una solución financiera orientada a procesar transferencias bancarias atómicas de alta concurrencia en tiempo real y ofrecer evaluaciones de riesgo y recomendaciones personalizadas impulsadas por Inteligencia Artificial (**Google Gemini API**).

---

## 📋 Contexto de Negocio y Desafíos Resueltos

1. **Alta Concurrencia y Rendimiento**: Soporte de picos elevados de transferencias en milisegundos mediante **Java 21 Virtual Threads (Project Loom)** y bloqueo pesimista ordenado de filas (`SELECT FOR UPDATE ORDER BY id ASC`) para prevenir saldos negativos y colisiones de bloqueo (deadlocks).
2. **Integración Desacoplada con el Core Legado (Bancs)**: Implementación del **Patrón Transactional Outbox** y **NATS JetStream** para desacoplar el procesamiento bancario central sin sobrecargarlo con peticiones síncronas directas.
3. **Procesamiento de IA No Bloqueante**: La transferencia bancaria responde en menos de 200 ms (HTTP `201 Created`). El servicio de IA en Python FastAPI (`ai-service`) y el trabajador en Java (`AiRecommendationWorker`) evalúan el riesgo de forma 100% asíncrona sin añadir latencia a la transferencia.

---

## 🏗️ Arquitectura del Sistema

```text
                        ┌────────────────────────────────────────┐
                        │ Consola Web Frontend / k6 / REST Client│
                        └───────────────────┬────────────────────┘
                                            │ HTTP / REST (Puerto 8085 / 8080)
                                            ▼
                    ┌────────────────────────────────────────────────┐
                    │ Nginx Reverse Proxy / SmartBancs Frontend      │
                    └───────────────────────┬────────────────────────┘
                                            │
                                            ▼
        ┌───────────────────────────────────────────────────────────────────┐
        │ Transaction Service (Java 21 + Spring Boot 3.4.1 + Virtual Threads)│
        │                                                                   │
        │ ├── TransferController (Transferencias Atómicas & Idempotentes)   │
        │ ├── AccountController (Consulta de Saldos en Tiempo Real)          │
        │ ├── TransferService (JdbcTemplate + Double-Entry Ledger)          │
        │ ├── OutboxPublisherJob (Publicación Asíncrona a NATS)             │
        │ └── TransactionMetrics (Métricas Micrometer / Actuator)           │
        └───────────────┬─────────────────────────────────┬─────────────────┘
                        │ SQL Transaccional               │ Evento Asíncrono
                        ▼                                 ▼
            ┌───────────────────────┐         ┌─────────────────────────┐
            │ PostgreSQL 16         │         │ NATS JetStream          │
            │  - accounts           │         │  - transaction.completed│
            │  - transactions       │         └────────────┬────────────┘
            │  - ledger_entries     │                      │
            │  - outbox_events      │                      ▼
            │  - recommendations    │         ┌─────────────────────────┐
            └───────────────────────┘         │ AiRecommendationWorker  │
                                              │  (FastAPI + Gemini AI)  │
                                              └─────────────────────────┘

        ┌───────────────────────────────────────────────────────────────────┐
        │ Stack de Observabilidad                                           │
        │ Prometheus (9090) + Grafana (3000) + Loki (3100) + Tempo (3200)   │
        └───────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología | Propósito |
|---|---|---|
| **Backend Core** | Java 21 LTS + Spring Boot 3.4.1 | API REST, Virtual Threads, Spring Security, Actuator. |
| **Persistencia Crítica** | Spring JDBC (`JdbcTemplate`) | Transacciones ACID, bloqueo pesimista y Ledger contable. |
| **Base de Datos** | PostgreSQL 16 | Motor relacional transaccional y versionamiento Flyway. |
| **Mensajería** | NATS JetStream 2.10 | Broker asíncrono persistente para eventos y Outbox. |
| **Servicio de IA** | Python 3.12 + FastAPI | Evaluación de riesgo con **Google Gemini API** y motor sintético. |
| **Pipeline ETL** | Python + Pandas | Limpieza, deduplicación e ingestión de lotes bancarios. |
| **Consola Web** | HTML5 / CSS3 Glassmorphism / Nginx | Interfaz interactiva de demostración y simulador de concurrencia. |
| **Observabilidad** | Prometheus + Grafana + Loki + Tempo | Monitoreo de métricas, dashboards, logs y trazabilidad distribuida. |
| **Pruebas** | JUnit 5, Testcontainers, k6 | Pruebas unitarias, de integración real con BD y carga masiva. |

---

## 📌 1. Instrucciones de Ejecución (Paso a Paso)

### 1.1 Prerrequisitos
Asegúrate de contar con las siguientes herramientas instaladas en tu sistema:
- **Docker Desktop** (versión 24.0+ con Docker Compose v2) ejecutándose en Windows / Linux / macOS.
- **PowerShell** (Windows) o **Bash** (Linux/macOS).
- *(Opcional)* Java 21 JDK y Maven 3.9+ si deseas compilar de forma local sin Docker.

---

### 1.2 Paso 1: Configuración de Variables de Entorno
Copia el archivo de ejemplo `.env.example` para crear tu archivo `.env`:
```powershell
cp .env.example .env
```
*(Opcional)* Si cuentas con una clave para la API de Google Gemini, agrégala en `.env`:
```env
GEMINI_API_KEY=tu_api_key_aquí
```
*Nota: Si no se proporciona una clave, el microservicio activa automáticamente el motor sintético de evaluación de riesgo (`mock-risk-v1`).*

---

### 1.3 Paso 2: Instalación y Despliegue Completo
Ejecuta el siguiente comando en la raíz del proyecto para construir las imágenes y levantar los 12 microservicios integrados:
```powershell
docker compose up --build -d
```

Verifica que todos los contenedores se encuentren en estado **Up (Healthy)**:
```powershell
docker ps
```

---

### 1.4 Puntos de Acceso y URLs de Servicios

Una vez levantados los contenedores, puedes ingresar a las siguientes consolas:

| Servicio / Consola | URL de Acceso | Credenciales / Notas |
|---|---|---|
| 🎨 **Consola Web de Demostración (Frontend)** | [`http://localhost:8085`](http://localhost:8085) | Interfaz gráfica interactiva y simulador. |
| 📊 **Grafana Dashboard** | [`http://localhost:3000`](http://localhost:3000) | Usuario: `admin` / Password: `admin` |
| 🔥 **Prometheus Metrics** | [`http://localhost:9090`](http://localhost:9090) | Explorador de métricas PromQL. |
| ⚡ **NATS JetStream Console** | [`http://localhost:8222`](http://localhost:8222) | Estado del motor de eventos. |
| 📖 **Swagger API Docs** | [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html) | Documentación interactiva OpenAPI. |
| 🤖 **Servicio de IA (FastAPI Docs)** | [`http://localhost:8000/docs`](http://localhost:8000/docs) | OpenAPI del servicio de IA. |

---

## 🧪 2. Guía Completa de Pruebas y Validación

### 2.1 Prueba 1: Demostración Interactiva desde el Navegador
1. Abre **`http://localhost:8085`** en tu navegador.
2. Consulta los saldos de las cuentas de prueba iniciales:
   - `ACC-1001` (ID: `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`): **$1,000.00 USD**
   - `ACC-1002` (ID: `b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22`): **$500.00 USD**
   - `ACC-1003` (ID: `c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33`): **$2,500.00 USD**
3. Realiza una transferencia atómica seleccionando cuenta origen, destino, monto y haciendo clic en **"Procesar Transferencia Atómica"**.
4. Prueba la **Ráfaga Concurrente**: Ajusta el deslizador de VUs (ej. 50 peticiones simultáneas) y haz clic en **"Lanzar Ráfaga Concurrente"** para observar el control de saldo y latencias.

---

### 2.2 Prueba 2: Arnés Automático de Verificación (Fases 1 a 6)
Ejecuta el script PowerShell de auditoría para verificar la suite completa:
```powershell
.\scripts\verify-phase6.ps1
```

---

### 2.3 Prueba 3: Pruebas de Carga y Concurrencia Masiva con k6
Ejecuta el script de carga con k6 dentro de un contenedor Docker aislado:
```powershell
Get-Content load-tests/concurrency_test.js | docker run --rm -i --net=host grafana/k6 run -
```
**Resultados esperados**:
- **Consistencia**: 100% de las respuestas devuelven estado HTTP `201 CREATED` o `422 UNPROCESSABLE ENTITY` (Saldo Insuficiente).
- **Latencia**: Latencia p95 promedio de `< 120 ms`.
- **Cero Saldos Negativos**: Garantía de consistencia ACID en PostgreSQL.

---

### 2.4 Prueba 4: Pruebas de Integración con Testcontainers
Para ejecutar la suite de pruebas reales de integración contra PostgreSQL en contenedor dinámico:
```powershell
cd transaction-service
mvn test -Dtest=TransactionIntegrationTest
```

---

## 🛑 3. Instrucciones para Detener y Limpiar la Solución

### Detener los servicios manteniendo los datos persistentes:
```powershell
docker compose down
```

### Detener los servicios y eliminar volúmenes de datos (Limpieza Total):
```powershell
docker compose down -v
```

---

## 📁 Estructura del Repositorio

```text
SmartBancsApp/
├── transaction-service/      # Core API Java 21 + Spring Boot 3.4.1
│   ├── src/main/java/        # Controladores, Servicios, JDBC Repositories, Metrics, Workers
│   ├── src/main/resources/   # Configuración YAML y Migraciones Flyway (V1 a V5)
│   └── src/test/java/        # Pruebas Unitarias e Integración con Testcontainers
├── ai-service/               # Servicio FastAPI + Gemini AI + Motor de Reglas en Python
├── etl-service/              # Script de ingestión y limpieza de lotes en Python/Pandas
├── frontend/                 # Consola Web de Demostración Fintech (Nginx SPA)
├── observability/            # Configuraciones de Prometheus, Grafana, Loki, Promtail y Tempo
├── load-tests/               # Scripts de prueba de concurrencia y estrés k6
├── incidents/                # Informe Post-Mortem de incidente controlado (#001)
├── scripts/                  # Scripts de verificación e infraestructura de arnés (.ps1)
├── docker-compose.yml        # Orquestación de los 12 microservicios
├── Plan.md                   # Plan maestro de arquitectura y diseño
└── README.md                 # Documentación principal de instalación y operación
```

---

## 🚨 Incidente Controlado e Informe Post-Mortem (#001)

El sistema incluye la simulación y documentación de un incidente de alta contención y picos de latencia en el informe [`incidents/post_mortem_incident_001.md`](file:///c:/Users/johan/OneDrive%20-%20Escuela%20Polit%C3%A9cnica%20Nacional/Documentos/EPN/Personal/SmartBancsApp/incidents/post_mortem_incident_001.md), donde se detallan la causa raíz (RCA), la evidencia empírica extraída de Grafana/Tempo/Loki y las acciones preventivas aplicadas (orden estable de bloqueos JDBC y limitación de tasa).
