# Script de Verificación y Arnés de Ingeniería para la Fase 2
# SmartBancsApp - Phase 2 Verification Harness (Transactional Core & Idempotency)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SmartBancsApp - Phase 2 Core Transactional Verification " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080/api/v1"
$sourceId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11" # ACC-1001 (1000.00 USD)
$destId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22"   # ACC-1002 (500.00 USD)

$passed = 0
$failed = 0

# 1. Consultar Cuentas Iniciales
Write-Host "[TEST 1] Consultando cuentas de prueba iniciales..." -ForegroundColor Yellow
try {
    $sourceAcc = Invoke-RestMethod -Uri "$baseUrl/accounts/$sourceId" -Method Get -ErrorAction Stop
    $destAcc = Invoke-RestMethod -Uri "$baseUrl/accounts/$destId" -Method Get -ErrorAction Stop

    Write-Host "  -> Cuenta Origen ($($sourceAcc.accountNumber)): Balance = $($sourceAcc.balance) $($sourceAcc.currency)" -ForegroundColor Gray
    Write-Host "  -> Cuenta Destino ($($destAcc.accountNumber)): Balance = $($destAcc.balance) $($destAcc.currency)" -ForegroundColor Gray
    Write-Host "[PASS] Consulta de cuentas exitosa." -ForegroundColor Green
    $passed++
} catch {
    Write-Host "[FAIL] No se pudieron consultar las cuentas iniciales. ($($_.Exception.Message))" -ForegroundColor Red
    $failed++
}

# 2. Ejecutar Transferencia Atómica Válida
$idempotencyKey = "HARNESS-TEST-KEY-" + (Get-Random)
Write-Host ""
Write-Host "[TEST 2] Ejecutando transferencia atómica de 150.00 USD (Clave: $idempotencyKey)..." -ForegroundColor Yellow

$transferBody = @{
    sourceAccountId = $sourceId
    destinationAccountId = $destId
    amount = 150.00
    currency = "USD"
    description = "Transferencia de prueba del Arnés de Fase 2"
} | ConvertTo-Json

try {
    $headers = @{
        "Idempotency-Key" = $idempotencyKey
        "Content-Type" = "application/json"
    }
    $txResponse = Invoke-RestMethod -Uri "$baseUrl/transactions/transfers" -Method Post -Headers $headers -Body $transferBody -ErrorAction Stop
    
    if ($txResponse.status -eq "COMPLETED" -and $txResponse.isIdempotentResponse -eq $false) {
        Write-Host "  -> Transacción completada con ID: $($txResponse.transactionId)" -ForegroundColor Gray
        Write-Host "[PASS] Transferencia atómica ejecutada correctamente." -ForegroundColor Green
        $passed++
    } else {
        Write-Host "[FAIL] Respuesta inesperada en transferencia: $($txResponse | ConvertTo-Json)" -ForegroundColor Red
        $failed++
    }
} catch {
    Write-Host "[FAIL] Error al ejecutar transferencia: $($_.Exception.Message)" -ForegroundColor Red
    $failed++
}

# 3. Probar Idempotencia (Misma clave)
Write-Host ""
Write-Host "[TEST 3] Reenviando la misma solicitud con clave $idempotencyKey para probar Idempotencia..." -ForegroundColor Yellow
try {
    $txIdempotent = Invoke-RestMethod -Uri "$baseUrl/transactions/transfers" -Method Post -Headers $headers -Body $transferBody -ErrorAction Stop
    
    if ($txIdempotent.isIdempotentResponse -eq $true -and $txIdempotent.transactionId -eq $txResponse.transactionId) {
        Write-Host "  -> Retornada respuesta idempotente idéntica." -ForegroundColor Gray
        Write-Host "[PASS] Prueba de idempotencia exitosa." -ForegroundColor Green
        $passed++
    } else {
        Write-Host "[FAIL] La idempotencia no respondió como se esperaba." -ForegroundColor Red
        $failed++
    }
} catch {
    Write-Host "[FAIL] Error al probar idempotencia: $($_.Exception.Message)" -ForegroundColor Red
    $failed++
}

# 4. Probar Saldo Insuficiente (Respuesta 422)
Write-Host ""
Write-Host "[TEST 4] Intentando transferencia de monto superior al saldo disponible (99,999.00 USD)..." -ForegroundColor Yellow
$insufficientBody = @{
    sourceAccountId = $sourceId
    destinationAccountId = $destId
    amount = 99999.00
    currency = "USD"
} | ConvertTo-Json

try {
    $headersFail = @{
        "Idempotency-Key" = "HARNESS-TEST-KEY-FAIL-" + (Get-Random)
        "Content-Type" = "application/json"
    }
    $resFail = Invoke-WebRequest -Uri "$baseUrl/transactions/transfers" -Method Post -Headers $headersFail -Body $insufficientBody -UseBasicParsing -ErrorAction Stop
    Write-Host "[FAIL] Se esperaba error 422 pero la solicitud respondió $($resFail.StatusCode)" -ForegroundColor Red
    $failed++
} catch {
    if ($_.Exception.Response.StatusCode -eq [System.Net.HttpStatusCode]::UnprocessableEntity) {
        Write-Host "  -> Rechazado correctamente con HTTP 422 Unprocessable Entity." -ForegroundColor Gray
        Write-Host "[PASS] Regla de saldo insuficiente verificada." -ForegroundColor Green
        $passed++
    } else {
        Write-Host "[FAIL] Se esperaba HTTP 422, se obtuvo: $($_.Exception.Message)" -ForegroundColor Red
        $failed++
    }
}

# 5. Verificación Final de Saldos
Write-Host ""
Write-Host "[TEST 5] Verificando saldos finales actualizados en base de datos..." -ForegroundColor Yellow
try {
    $sourceFinal = Invoke-RestMethod -Uri "$baseUrl/accounts/$sourceId" -Method Get -ErrorAction Stop
    $destFinal = Invoke-RestMethod -Uri "$baseUrl/accounts/$destId" -Method Get -ErrorAction Stop

    Write-Host "  -> Saldo Final Origen: $($sourceFinal.balance) $($sourceFinal.currency) (Esperado: 850.00)" -ForegroundColor Gray
    Write-Host "  -> Saldo Final Destino: $($destFinal.balance) $($destFinal.currency) (Esperado: 650.00)" -ForegroundColor Gray

    if ($sourceFinal.balance -eq 850.00 -and $destFinal.balance -eq 650.00) {
        Write-Host "[PASS] Consistencia contable verificada con precisión." -ForegroundColor Green
        $passed++
    } else {
        Write-Host "[WARN] Los saldos difieren de lo esperado según las transferencias realizadas." -ForegroundColor Yellow
        $failed++
    }
} catch {
    Write-Host "[FAIL] Error al consultar saldos finales: $($_.Exception.Message)" -ForegroundColor Red
    $failed++
}

Write-Host ""
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan
Write-Host " Summary: $passed Passed, $failed Failed / Pending Startup" -ForegroundColor Cyan
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan

if ($failed -eq 0) {
    Write-Host ">>> Phase 2 Transactional Core Harness Verification PASSED! <<<" -ForegroundColor Green
} else {
    Write-Host ">>> Phase 2 Verification incomplete. Ensure Docker service is running. <<<" -ForegroundColor Yellow
}
