@echo off
setlocal
set "ROOT=%~dp0"
if "%SMOLVLM_PYTHON%"=="" (set "SMOLVLM_PY=%ROOT%.venv_models\Scripts\python.exe") else (set "SMOLVLM_PY=%SMOLVLM_PYTHON%")
cd /d "%ROOT%"
"%SMOLVLM_PY%" "%ROOT%vlm_server.py" > "%ROOT%smolvlm_service.out.log" 2> "%ROOT%smolvlm_service.err.log"
