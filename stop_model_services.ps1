$ErrorActionPreference = "Continue"

$services = @(
    @{ Name = "SmolVLM album classifier"; Port = 5000 },
    @{ Name = "GFPGAN face restore"; Port = 5001 },
    @{ Name = "LaMa inpaint"; Port = 5002 },
    @{ Name = "Real-ESRGAN super resolution"; Port = 8000 }
)

foreach ($service in $services) {
    $port = $service.Port
    $lines = netstat -ano | Select-String -Pattern ":$port\s+.*LISTENING"
    if (-not $lines) {
        Write-Host "$($service.Name) is not running on port $port." -ForegroundColor Yellow
        continue
    }

    foreach ($line in $lines) {
        $parts = ($line.ToString() -split "\s+") | Where-Object { $_ }
        $pidText = $parts[-1]
        if ($pidText -match "^\d+$") {
            Write-Host "Stopping $($service.Name) on port $port, PID $pidText..." -ForegroundColor Cyan
            Stop-Process -Id ([int]$pidText) -Force
        }
    }
}

Write-Host "Stop commands completed." -ForegroundColor Green
