@echo off
cd /d D:\Engineering\PhotoProject
"D:\Anaconda\envs\lama-inpaint\python.exe" "D:\Engineering\PhotoProject\inpaint_service.py" > "D:\Engineering\PhotoProject\inpaint_lama.out.log" 2> "D:\Engineering\PhotoProject\inpaint_lama.err.log"
