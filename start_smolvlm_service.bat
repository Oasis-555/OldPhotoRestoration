@echo off
cd /d D:\Engineering\PhotoProject
"D:\Anaconda\envs\smolvlm\python.exe" "D:\Engineering\PhotoProject\vlm_server.py" > "D:\Engineering\PhotoProject\smolvlm_service.out.log" 2> "D:\Engineering\PhotoProject\smolvlm_service.err.log"
