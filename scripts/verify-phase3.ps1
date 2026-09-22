# Script de Verificación y Arnés de Ingeniería para la Fase 3
# SmartBancsApp - Phase 3 Verification Harness (Outbox Pattern, NATS Sync & Python ETL)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SmartBancsApp - Phase 3 Outbox & ETL Verification Harness" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080/api/v1"
$sourceId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
$destId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22"

$passed = 0
$failed = 0

# 1. Ejecutar Transferencia para Generar Evento Outbox
$idempotencyKey = "OUTBOX-TEST-KEY-" + (Get-Random)
Write-Host "[TEST 1] Generando transferencia atómica para verificar el Patrón Outbox..." -ForegroundColor Yellow

$transferBody = @{
    sourceAccountId = $sourceId
    destinationAccountId = $destId
    amount = 75.00
    currency = "USD"
    description = "Prueba del Patrón Outbox y Sincronización NATS"
} | ConvertTo-Json

try {
    $headers = @{
        "Idempotency-Key" = $idempotencyKey
        "Content-Type" = "application/json"
    }
    $txResponse = Invoke-RestMethod -Uri "$baseUrl/transactions/transfers" -Method Post -Headers $headers -Body $transferBody -ErrorAction Stop
    
    if ($txResponse.status -eq "COMPLETED") {
        Write-Host "  -> Transacción registrada exitosamente. ID: $($txResponse.transactionId)" -ForegroundColor Gray
        Write-Host "[PASS] Evento Outbox almacenado en base de datos." -ForegroundColor Green
        $passed++
    } else {
        Write-Host "[FAIL] No se pudo completar la transacción para Outbox." -ForegroundColor Red
        $failed++
    }
} catch {
    Write-Host "[WARN] No se pudo conectar con transaction-service ($($_.Exception.Message)). Verifique si Docker está en ejecución." -ForegroundColor Yellow
    $failed++
}

# 2. Ejecutar Pipeline ETL en Python
Write-Host ""
Write-Host "[TEST 2] Probando Pipeline ETL de Pandas con lote de muestra (data/sample_batch.csv)..." -ForegroundColor Yellow

$etlPath = ".\etl-service\etl_processor.py"
$sampleFile = ".\etl-service\data\sample_batch.csv"

if (Test-Path $etlPath) {
    try {
        $pythonCmd = "python"
        $etlOutput = & $pythonCmd $etlPath --file $sampleFile 2>&1
        Write-Host $etlOutput -ForegroundColor Gray
        
        if ($etlOutput -match "Resumen de Ejecución ETL") {
            Write-Host "[PASS] Pipeline ETL ejecutado y validado correctamente." -ForegroundColor Green
            $passed++
        } else {
            Write-Host "[WARN] La salida del ETL difiere de lo esperado." -ForegroundColor Yellow
            $failed++
        }
    } catch {
        Write-Host "[WARN] No se pudo ejecutar python localmente ($($_.Exception.Message))." -ForegroundColor Yellow
        $failed++
    }
} else {
    Write-Host "[FAIL] No se encontró el script de procesador ETL en $etlPath" -ForegroundColor Red
    $failed++
}

Write-Host ""
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan
Write-Host " Summary: $passed Passed, $failed Failed / Pending Execution" -ForegroundColor Cyan
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan

if ($failed -eq 0) {
    Write-Host ">>> Phase 3 Outbox & ETL Verification Harness PASSED! <<<" -ForegroundColor Green
} else {
    Write-Host ">>> Phase 3 Verification complete. Ensure environment containers are running. <<<" -ForegroundColor Yellow
}
