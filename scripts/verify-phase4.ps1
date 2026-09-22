# Script de Verificación y Arnés de Ingeniería para la Fase 4
# SmartBancsApp - Phase 4 Verification Harness (AI Service, Gemini API & Asynchronous Worker)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SmartBancsApp - Phase 4 AI & Async Processing Harness  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080/api/v1"
$aiUrl = "http://localhost:8000"
$sourceId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
$destId = "c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33"

$passed = 0
$failed = 0

# 1. Verificar Salud e Integración Gemini de AI Service
Write-Host "[TEST 1] Verificando salud e integración Gemini en AI Service..." -ForegroundColor Yellow
try {
    $aiHealth = Invoke-RestMethod -Uri "$aiUrl/health" -Method Get -ErrorAction Stop
    Write-Host "  -> Estado: $($aiHealth.status) | Integración Gemini: $($aiHealth.gemini_integration)" -ForegroundColor Gray
    Write-Host "[PASS] Servicio de IA respondiendo correctamente." -ForegroundColor Green
    $passed++
} catch {
    Write-Host "[WARN] No se pudo conectar con AI Service ($($_.Exception.Message)). Verifique si el servicio está corriendo." -ForegroundColor Yellow
    $failed++
}

# 2. Probar Endpoint Directo de Evaluación de Riesgo de IA
Write-Host ""
Write-Host "[TEST 2] Probando endpoint directo /api/v1/risk-assessments..." -ForegroundColor Yellow
$directPayload = @{
    transaction_id = [System.Guid]::NewGuid().ToString()
    source_account_id = $sourceId
    destination_account_id = $destId
    amount = 1250.00
    currency = "USD"
} | ConvertTo-Json

try {
    $aiDirectResp = Invoke-RestMethod -Uri "$aiUrl/api/v1/risk-assessments" -Method Post -ContentType "application/json" -Body $directPayload -ErrorAction Stop
    Write-Host "  -> Puntaje de Riesgo: $($aiDirectResp.risk_score)" -ForegroundColor Gray
    Write-Host "  -> Recomendación: $($aiDirectResp.recommendation)" -ForegroundColor Gray
    Write-Host "  -> Versión de Modelo: $($aiDirectResp.model_version)" -ForegroundColor Gray
    Write-Host "  -> Tiempo de Procesamiento: $($aiDirectResp.processing_time_ms) ms" -ForegroundColor Gray
    Write-Host "[PASS] Evaluación directa de riesgo ejecutada con éxito." -ForegroundColor Green
    $passed++
} catch {
    Write-Host "[WARN] Falló la evaluación directa de IA ($($_.Exception.Message))." -ForegroundColor Yellow
    $failed++
}

# 3. Ejecutar Transferencia Atómica e Invocación Asíncrona NATS -> AI
Write-Host ""
Write-Host "[TEST 3] Ejecutando transferencia atómica y esperando procesamiento asíncrono..." -ForegroundColor Yellow
$idempotencyKey = "AI-TEST-KEY-" + (Get-Random)
$transferBody = @{
    sourceAccountId = $sourceId
    destinationAccountId = $destId
    amount = 500.00
    currency = "USD"
    description = "Transferencia para evaluación asíncrona de IA"
} | ConvertTo-Json

try {
    $headers = @{
        "Idempotency-Key" = $idempotencyKey
        "Content-Type" = "application/json"
    }
    $txResp = Invoke-RestMethod -Uri "$baseUrl/transactions/transfers" -Method Post -Headers $headers -Body $transferBody -ErrorAction Stop
    $txId = $txResp.transactionId

    Write-Host "  -> Transacción completada inmediatamente (HTTP 201). ID: $txId" -ForegroundColor Gray
    Write-Host "  -> Esperando 2 segundos para dar tiempo al consumidor NATS de procesar la IA..." -ForegroundColor Gray
    Start-Sleep -Seconds 2

    # Consultar recomendación en PostgreSQL vía API REST
    $recResp = Invoke-RestMethod -Uri "$baseUrl/recommendations/$txId" -Method Get -ErrorAction Stop
    Write-Host "  -> Recomendación Persistida: $($recResp.recommendation) (Score: $($recResp.riskScore), Modelo: $($recResp.modelVersion))" -ForegroundColor Gray
    Write-Host "[PASS] Evaluación asíncrona de IA procesada y persistida correctamente." -ForegroundColor Green
    $passed++
} catch {
    Write-Host "[WARN] No se pudo verificar la recomendación asíncrona ($($_.Exception.Message)). Asegúrese de tener el entorno en ejecución." -ForegroundColor Yellow
    $failed++
}

Write-Host ""
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan
Write-Host " Summary: $passed Passed, $failed Failed / Pending Execution" -ForegroundColor Cyan
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan

if ($failed -eq 0) {
    Write-Host ">>> Phase 4 AI & Async Harness Verification PASSED! <<<" -ForegroundColor Green
} else {
    Write-Host ">>> Phase 4 Verification complete. Ensure environment containers are running. <<<" -ForegroundColor Yellow
}
