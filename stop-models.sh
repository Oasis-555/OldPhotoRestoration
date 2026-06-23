#!/bin/bash

echo "Stopping AI model services..."

pkill -f "uvicorn realesrgan_http_api:app"
echo "Real-ESRGAN service stopped."

pkill -f "python app.py"
echo "GFPGAN service stopped."

pkill -f "python vlm_server.py"
echo "SmolVLM-Cap service stopped."

pkill -f "python inpaint_service.py"
echo "Inpaint service stopped."

echo "Done."
