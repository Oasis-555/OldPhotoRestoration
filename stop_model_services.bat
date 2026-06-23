@echo off
setlocal

echo Stopping model services by port...

call :stop_port 5000 "SmolVLM album classifier"
call :stop_port 5001 "GFPGAN face restore"
call :stop_port 5002 "LaMa inpaint"
call :stop_port 8000 "Real-ESRGAN super resolution"

echo.
echo Stop commands completed.
goto :eof

:stop_port
set PORT=%~1
set NAME=%~2
set FOUND=0

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%PORT% .*LISTENING"') do (
  set FOUND=1
  echo Stopping %NAME% on port %PORT%, PID %%P...
  taskkill /PID %%P /F >nul 2>nul
  if errorlevel 1 (
    echo Failed to stop PID %%P. Please run this script as administrator or stop it manually.
  ) else (
    echo Stopped PID %%P.
  )
)

if "%FOUND%"=="0" echo %NAME% is not running on port %PORT%.
goto :eof
