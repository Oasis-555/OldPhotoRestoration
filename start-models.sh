#!/bin/bash

PROJECT_ROOT=$(cd "$(dirname "$0")" && pwd)

echo "=========================================="
echo "Starting old-photo restoration AI services"
echo "=========================================="

echo "[1/4] Starting Real-ESRGAN image super-resolution service on port 8000..."
cd "$PROJECT_ROOT/Real-ESRGAN" || exit 1
nohup python -m uvicorn realesrgan_http_api:app --host 0.0.0.0 --port 8000 > realesrgan.log 2>&1 &
echo "Real-ESRGAN started. Log: Real-ESRGAN/realesrgan.log"

echo "[2/4] Starting GFPGAN face restoration service on port 5001..."
cd "$PROJECT_ROOT/GFPGAN" || exit 1
nohup python app.py > gfpgan.log 2>&1 &
echo "GFPGAN started. Log: GFPGAN/gfpgan.log"

echo "[3/4] Starting SmolVLM-Cap album classification service on port 5000..."
cd "$PROJECT_ROOT" || exit 1
nohup python vlm_server.py > vlm.log 2>&1 &
echo "SmolVLM-Cap started. Log: vlm.log"

echo "[4/4] Starting scratch/damage inpaint service on port 5002..."
cd "$PROJECT_ROOT" || exit 1
nohup python inpaint_service.py > inpaint.log 2>&1 &
echo "Inpaint service started. Log: inpaint.log"

echo "=========================================="
echo "Service start commands have been issued."
echo "Checking processes..."
sleep 2

ps -ef | grep vlm_server.py | grep -v grep
ps -ef | grep app.py | grep -v grep
ps -ef | grep realesrgan_http_api | grep -v grep
ps -ef | grep inpaint_service.py | grep -v grep

echo "=========================================="
echo "Use ./stop-models.sh to stop the services."
