@echo off
setlocal

set "ROOT=%~dp0"
set INPAINT_MODEL=auto
set "LAMA_MODEL=%ROOT%models\big-lama.pt"
if "%LAMA_PYTHON%"=="" (set "LAMA_PY=%ROOT%.venv_models\Scripts\python.exe") else (set "LAMA_PY=%LAMA_PYTHON%")

cd /d "%ROOT%"
echo Starting LaMa inpaint service on http://127.0.0.1:5002
echo Keep this window open while recording the demo.
"%LAMA_PY%" "%ROOT%inpaint_service.py"

pause
