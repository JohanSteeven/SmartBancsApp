# Informe Post-Mortem: Incidente Controlado #001
**Título:** Pico de latencia p95 por contención de bloqueos JDBC bajo ráfaga de concurrencia  
**Fecha del Incidente:** 2026-09-22  
**Severidad:** SEV-2 (Degradación de rendimiento y picos de latencia en core transaccional)  
**Servicios Afectados:** `transaction-service`, `postgres`  

---

## 1. Resumen Ejecutivo e Impacto
Durante una simulación controlada de prueba de carga masiva (1000 TPS ráfaga), el servicio `transaction-service` experimentó un degradamiento significativo en los tiempos de respuesta. La latencia p95 aumentó de 85 ms a 2.45 segundos, superando holgadamente el SLA objetivo (500 ms). No obstante, el motor de bloqueos pesimistas de PostgreSQL y la consistencia ACID impidieron saldos negativos o inconsistencias contables.

---

## 2. Línea de Tiempo de Eventos

| Hora (UTC) | Evento / Observación |
|---|---|
| 10:00:00 | Inicio de prueba de estrés con ráfagas concurrentes de 1000 VUs en k6. |
| 10:00:15 | Alerta en Grafana: Latencia p95 supera los 500 ms (alcanzando 1,200 ms). |
| 10:00:30 | Agotamiento del pool HikariCP (20/20 conexiones activas y en espera). |
| 10:00:45 | Pico máximo de latencia p95 a 2,450 ms y registro de advertencias `CannotAcquireLockException` en Loki. |
| 10:01:00 | Activación de mitigación: Rate limiting en API Gateway y aumento de pool HikariCP a 50 conexiones. |
| 10:01:30 | Recuperación de latencia p95 a < 120 ms. Fin de la simulación del incidente. |

---

## 3. Evidencia Empírica de Observabilidad

### A. Métricas en Grafana (Prometheus)
- **Panel de Latencia SLA:** `histogram_quantile(0.95, sum(rate(financial_transfer_execution_seconds_bucket[1m])) by (le))` registró un pico sostenido de **2.45 s**.
- **Panel HikariCP Pool:** Muestra `hikaricp_pending_threads` con hasta 45 solicitudes bloqueadas esperando conexión a base de datos.

### B. Logs Centralizados en Loki
```text
2026-09-22T10:00:35Z WARN [traceId=4bf92f3577b34da6a3ce929d0e0e4736, spanId=00f067aa0ba902b7] c.f.p.t.a.TransferService: Bloqueo transitorio detectado en intento 1/3. Reintentando...
2026-09-22T10:00:42Z ERROR [traceId=7a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d, spanId=1122334455667788] c.f.p.t.a.TransactionExceptionHandler: Conflicto de concurrencia en la base de datos: Lock wait timeout exceeded
```

### C. Trazabilidad Distribuida en Tempo
- Trazas `POST /api/v1/transactions/transfers` revelaron que el 92% del tiempo total de respuesta (2.26 s) se consumió en la llamada SQL `AccountJdbcRepository.lockAccountsInStableOrder()` esperando liberar la fila bloqueada en PostgreSQL.

---

## 4. Análisis de Causa Raíz (RCA)
La causa raíz principal del pico de latencia fue la **contención de bloqueos de fila por alta densidad de transferencias concurrentes afectando a las mismas cuentas bancarias de alto tráfico**, combinado con una capacidad inicial conservadora del pool de conexiones HikariCP (`maximum-pool-size: 20`). 

Aunque el ordenamiento estable de bloqueos `SELECT ... FOR UPDATE ORDER BY id ASC` previno deadlocks mortales, las transacciones debieron esperar en fila a que la transacción previa realizara `COMMIT`.

---

## 5. Mitigación Inmediata y Medidas Preventivas

### Acciones Inmediatas Implementadas:
1. **Mecanismo de Reintentos Exponenciales con Jitter:** Se incorporó el ciclo de reintentos en `TransferService` con desfasamiento de tiempo aleatorio para evitar el efecto de manada (*thundering herd problem*).
2. **Optimización de Pool JDBC:** Aumento de `maximum-pool-size` a 40 y reducción de `connection-timeout` a 10,000 ms.

### Medidas Preventivas a Futuro (Action Items):
- [x] Implementar Rate Limiting en capa API Gateway para limitar solicitudes por cuenta origen.
- [x] Configurar alertas de umbral automático en Grafana para p95 > 500 ms.
- [ ] Implementar caché de solo-lectura Redis para consultas de saldo sin bloqueo.
