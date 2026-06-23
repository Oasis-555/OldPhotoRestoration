@echo off
setlocal

set ROOT=D:\Engineering\PhotoProject
set INPAINT_MODEL=auto
set LAMA_MODEL=%ROOT%\models\big-lama.pt

cd /d "%ROOT%"
echo Starting LaMa inpaint service on http://127.0.0.1:5002
echo Keep this window open while recording the demo.
"D:\Anaconda\envs\lama-inpaint\python.exe" "%ROOT%\inpaint_service.py"

pause
