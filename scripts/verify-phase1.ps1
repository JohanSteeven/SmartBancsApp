# Script de Verificación y Arnés de Ingeniería para la Fase 1
# SmartBancsApp - Phase 1 Verification Harness

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SmartBancsApp - Phase 1 Service Verification Harness   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$endpoints = @(
    @{ Name = "Transaction Service Health"; Url = "http://localhost:8080/actuator/health"; Expected = 200 },
    @{ Name = "Transaction Service Prometheus"; Url = "http://localhost:8080/actuator/prometheus"; Expected = 200 },
    @{ Name = "Transaction Service Swagger UI"; Url = "http://localhost:8080/swagger-ui/index.html"; Expected = 200 },
    @{ Name = "AI Service Health"; Url = "http://localhost:8000/health"; Expected = 200 },
    @{ Name = "NATS Monitoring (Varz)"; Url = "http://localhost:8222/varz"; Expected = 200 },
    @{ Name = "Prometheus Health"; Url = "http://localhost:9090/-/healthy"; Expected = 200 },
    @{ Name = "Grafana Health"; Url = "http://localhost:3000/api/health"; Expected = 200 },
    @{ Name = "Loki Ready Check"; Url = "http://localhost:3100/ready"; Expected = 200 },
    @{ Name = "Tempo Ready Check"; Url = "http://localhost:3200/ready"; Expected = 200 },
    @{ Name = "Postgres Exporter Metrics"; Url = "http://localhost:9187/metrics"; Expected = 200 },
    @{ Name = "cAdvisor Healthz"; Url = "http://localhost:8088/healthz"; Expected = 200 }
)

$passed = 0
$failed = 0

foreach ($ep in $endpoints) {
    try {
        $response = Invoke-WebRequest -Uri $ep.Url -Method Get -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -eq $ep.Expected) {
            Write-Host "[PASS] $($ep.Name) - Status: $($response.StatusCode) ($($ep.Url))" -ForegroundColor Green
            $passed++
        } else {
            Write-Host "[WARN] $($ep.Name) - Unexpected Status: $($response.StatusCode) (Expected: $($ep.Expected))" -ForegroundColor Yellow
            $failed++
        }
    } catch {
        Write-Host "[FAIL] $($ep.Name) - Connection Failed or Offline: $($ep.Url) ($($_.Exception.Message))" -ForegroundColor Red
        $failed++
    }
}

Write-Host ""
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan
Write-Host " Summary: $passed Passed, $failed Failed / Pending Startup" -ForegroundColor Cyan
Write-Host "----------------------------------------------------------" -ForegroundColor Cyan

if ($failed -eq 0) {
    Write-Host ">>> Phase 1 Infrastructure Harness Verification PASSED! <<<" -ForegroundColor Green
} else {
    Write-Host ">>> Phase 1 Verification incomplete. Ensure 'docker compose up --build -d' is running. <<<" -ForegroundColor Yellow
}
