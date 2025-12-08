# PowerShell script to test PostgreSQL connection
# This simulates what Spring Boot application will do

Write-Host "Testing PostgreSQL connection..." -ForegroundColor Cyan
Write-Host "Host: localhost"
Write-Host "Port: 5432"
Write-Host "Database: openwallet"
Write-Host "Username: openwallet"
Write-Host "Password: openwallet"
Write-Host ""

# Test 1: Check if port is accessible
Write-Host "Test 1: Checking if port 5432 is accessible..." -ForegroundColor Yellow
$tcpClient = New-Object System.Net.Sockets.TcpClient
try {
    $tcpClient.Connect("localhost", 5432)
    Write-Host "✅ Port 5432 is accessible" -ForegroundColor Green
    $tcpClient.Close()
} catch {
    Write-Host "❌ Port 5432 is NOT accessible: $_" -ForegroundColor Red
}
Write-Host ""

# Test 2: Using Docker exec
Write-Host "Test 2: Using Docker exec (from inside container)..." -ForegroundColor Yellow
$result = docker-compose exec -T postgres psql -U openwallet -d openwallet -c "SELECT 'Connection successful!' as status, current_user, current_database();" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Docker exec connection test PASSED" -ForegroundColor Green
    Write-Host $result
} else {
    Write-Host "❌ Docker exec connection test FAILED" -ForegroundColor Red
    Write-Host $result
}
Write-Host ""

# Test 3: Show Docker Compose configuration
Write-Host "Test 3: Docker Compose PostgreSQL configuration:" -ForegroundColor Yellow
docker-compose config | Select-String -Pattern "POSTGRES_|ports:" -Context 0,2
Write-Host ""

Write-Host "Done!" -ForegroundColor Cyan

