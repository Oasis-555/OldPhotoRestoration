# One-Click Startup Script for Old Photo Restoration System

Write-Host "Starting Old Photo Restoration System..." -ForegroundColor Cyan

# Define paths
$RootPath = $PSScriptRoot
$BackendPath = Join-Path $RootPath "backend"
$FrontendPath = Join-Path $RootPath "frontend"

# Start Backend in a new window
Write-Host "Launching Backend (Spring Boot)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location -LiteralPath '$BackendPath'; Write-Host 'Starting Backend Service...' -ForegroundColor Green; mvn spring-boot:run" -WindowStyle Normal

# Start Frontend in a new window
Write-Host "Launching Frontend (Vite)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location -LiteralPath '$FrontendPath'; Write-Host 'Starting Frontend Application...' -ForegroundColor Green; npm run dev" -WindowStyle Normal

Write-Host "Startup commands initiated in separate windows." -ForegroundColor Cyan
Write-Host "Backend usually runs on: http://localhost:8080"
Write-Host "Frontend usually runs on: http://localhost:5173"
