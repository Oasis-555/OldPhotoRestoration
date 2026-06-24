@echo off
setlocal

set "ROOT=%~dp0"
if "%MODEL_PYTHON%"=="" (set "MODEL_PY=%ROOT%.venv_models\Scripts\python.exe") else (set "MODEL_PY=%MODEL_PYTHON%")
if "%LAMA_PYTHON%"=="" (set "LAMA_PY=%MODEL_PY%") else (set "LAMA_PY=%LAMA_PYTHON%")
if "%SMOLVLM_PYTHON%"=="" (set "SMOLVLM_PY=%MODEL_PY%") else (set "SMOLVLM_PY=%SMOLVLM_PYTHON%")

if not exist "%MODEL_PY%" (
  echo Python interpreter not found: %MODEL_PY%
  echo Create .venv_models or set MODEL_PYTHON, LAMA_PYTHON and SMOLVLM_PYTHON.
  pause
  exit /b 1
)

echo Starting model services...

call :start_if_free 5001 "GFPGAN face restore" "%MODEL_PY%" "app.py" "%ROOT%\GFPGAN"
call :start_if_free 5002 "LaMa inpaint" "%LAMA_PY%" "inpaint_service.py" "%ROOT%"
call :start_if_free 8000 "Real-ESRGAN super resolution" "%MODEL_PY%" "-m uvicorn realesrgan_http_api:app --host 0.0.0.0 --port 8000" "%ROOT%\Real-ESRGAN"
call :start_if_free 5000 "SmolVLM album classifier" "%SMOLVLM_PY%" "vlm_server.py" "%ROOT%"

echo.
echo Startup commands completed.
echo Wait 30-60 seconds for model loading, then check:
echo   netstat -ano ^| findstr /R /C:":5000 .*LISTENING" /C:":5001 .*LISTENING" /C:":5002 .*LISTENING" /C:":8000 .*LISTENING"
pause
goto :eof

:start_if_free
set PORT=%~1
set NAME=%~2
set EXE=%~3
set ARGS=%~4
set WORKDIR=%~5

netstat -ano | findstr /R /C:":%PORT% .*LISTENING" >nul
if %ERRORLEVEL% EQU 0 (
  echo %NAME% already running on port %PORT%.
  goto :eof
)

echo Starting %NAME% on port %PORT%...
start "%NAME%" /min /D "%WORKDIR%" "%EXE%" %ARGS%
goto :eof
