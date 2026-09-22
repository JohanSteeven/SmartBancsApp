# Script de Verificación y Arnés de Ingeniería para la Fase 5
# SmartBancsApp - Phase 5 Observability & Custom Metrics Verification Harness

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SmartBancsApp - Phase 5 Observability Verification    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080"
$grafanaUrl = "http://localhost:3000"
$sourceId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
$destId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22"

$passed = 0
$failed = 0

# 1. Generar tráfico de prueba
Write-Host "[TEST 1] Generando tráfico de prueba para activar métricas Micrometer..." -ForegroundColor Yellow
$idempotencyKey = "METRIC-TEST-KEY-" + (Get-Random)
$transferBody = @{
    sourceAccountId = $sourceId
    destinationAccountId = $destId
    amount = 50.00
    currency = "USD"
    description = "Transferencia para verificación de métricas"
} | ConvertTo-Json

try {
    $headers = @{
        "Idempotency-Key" = $idempotencyKey
        "Content-Type" = "application/json"
    }
    $txResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/transactions/transfers" -Method Post -Headers $headers -Body $transferBody -ErrorAction Stop
    Write-Host "  -> Transacción completada con éxito. ID: $($txResp.transactionId)" -ForegroundColor Gray
    Write-Host "[PASS] Tráfico de prueba generado." -ForegroundColor Green
    $passed++
} catch {
    Write-Host "[WARN] No se pudo generar transferencia de prueba ($($_.Exception.Message)). Verifique si el servicio está corriendo." -ForegroundColor Yellow
    $failed++
}

# 2. Auditar Métricas en /actuator/prometheus
Write-Host ""
Write-Host "[TEST 2] Auditando scraping en /actuator/prometheus..." -ForegroundColor Yellow
try {
    $prometheusMetrics = Invoke-RestMethod -Uri "$baseUrl/actuator/prometheus" -Method Get -ErrorAction Stop
    
    $requiredMetrics = @(
        "financial_transactions_total",
        "financial_transfer_execution_seconds",
        "financial_database_deadlocks_total",
        "financial_outbox_events_total"
    )

    $allFound = $true
    foreach ($m in $requiredMetrics) {
        if ($prometheusMetrics -match $m) {
            Write-Host "  -> Métrica '$m': ENCONTRADA" -ForegroundColor Gray
        } else {
            Write-Host "  -> Métrica '$m': NO ENCONTRADA" -ForegroundColor Red
            $allFound = $false
        }
    }

    if ($allFound) {
        Write-Host "[PASS] Todas las métricas personalizadas de negocio están expuestas correctamente." -ForegroundColor Green
        $passed++
    } else {
        Write-Host "[FAIL] Faltan métricas obligatorias en Prometheus." -ForegroundColor Red
        $failed++
    }
} catch {
    Write-Host "[WARN] No se pudo consultar /actuator/prometheus ($($_.Exception.Message))." -ForegroundColor Yellow
    $failed++
}

# 3. Auditar Provisión de Dashboards en Grafana
Write-Host ""
Write-Host "[TEST 3] Auditando provisión automática de Tablero en Grafana..." -ForegroundColor Yellow
try {
    $grafanaDashboard = Invoke-RestMethod -Uri "$grafanaUrl/api/dashboards/uid/smartbancs-main-dashboard" -Method Get -ErrorAction Stop
    Write-Host "  -> Dashboard Encontrado: '$($grafanaDashboard.dashboard.title)' (UID: $($grafanaDashboard.dashboard.uid))" -ForegroundColor Gray
    Write-Host "[PASS] Dashboard de observabilidad de plataforma provisionado exitosamente." -ForegroundColor Green
    $passed++
} catch {
    Write-Host "[WARN] No se pudo consultar el dashboard en Grafana ($($_.Exception.Message))." -ForegroundColor Yellow
    $failed++
}

Write-Host ""
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan
Write-Host " Summary: $passed Passed, $failed Failed / Pending Execution" -ForegroundColor Cyan
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan

if ($failed -eq 0) {
    Write-Host ">>> Phase 5 Observability Verification Harness PASSED! <<<" -ForegroundColor Green
} else {
    Write-Host ">>> Phase 5 Verification complete. Ensure environment containers are running. <<<" -ForegroundColor Yellow
}
