$ErrorActionPreference = "Stop"

$env:INPAINT_MODEL = "auto"
$env:LAMA_MODEL = "D:\Engineering\PhotoProject\models\big-lama.pt"

Set-Location "D:\Engineering\PhotoProject"
& "D:\Anaconda\envs\lama-inpaint\python.exe" "D:\Engineering\PhotoProject\inpaint_service.py" *> "D:\Engineering\PhotoProject\inpaint_lama.log"
