import os
import re
import shutil
import time
import uuid
from pathlib import Path
from typing import Optional

import requests
from fastapi import FastAPI, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, Field
from starlette.responses import JSONResponse
from starlette.staticfiles import StaticFiles

from realesrgan_service import EnhanceParams, RealESRGANService, make_output_basename


def _env_int(name: str) -> Optional[int]:
    v = os.getenv(name)
    if v is None or v == "":
        return None
    return int(v)


OUTPUT_DIR = Path(os.getenv("REAL_ESRGAN_OUTPUT_DIR", "api_results"))
INPUT_DIR = Path(os.getenv("REAL_ESRGAN_INPUT_DIR", "api_inputs"))
DEFAULT_MODEL = os.getenv("REAL_ESRGAN_MODEL", "RealESRGAN_x4plus")
DEFAULT_GPU_ID = _env_int("REAL_ESRGAN_GPU_ID")

MAX_DOWNLOAD_BYTES = int(os.getenv("REAL_ESRGAN_MAX_DOWNLOAD_BYTES", str(50 * 1024 * 1024)))
DOWNLOAD_TIMEOUT_S = float(os.getenv("REAL_ESRGAN_DOWNLOAD_TIMEOUT_S", "30"))

app = FastAPI(title="Real-ESRGAN HTTP API", version="1.0")

service = RealESRGANService(weights_dir=os.getenv("REAL_ESRGAN_WEIGHTS_DIR", "weights"))

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
INPUT_DIR.mkdir(parents=True, exist_ok=True)

app.mount("/results", StaticFiles(directory=str(OUTPUT_DIR)), name="results")


class EnhanceIn(BaseModel):
    image: str = Field(..., description="图片地址：本地绝对路径(/a/b.png) 或 http(s) URL")
    model_name: str = Field(default=DEFAULT_MODEL)
    outscale: float = Field(default=4.0, ge=1.0, le=8.0)
    denoise_strength: float = Field(default=0.5, ge=0.0, le=1.0)
    tile: int = Field(default=0, ge=0)
    tile_pad: int = Field(default=10, ge=0)
    pre_pad: int = Field(default=0, ge=0)
    fp32: bool = False
    face_enhance: bool = False
    ext: str = Field(default="auto", description="auto|jpg|png")


class EnhanceOut(BaseModel):
    ok: bool
    result_path: str
    result_url: str
    elapsed_ms: int
    model_name: str


def _is_http_url(s: str) -> bool:
    return s.startswith("http://") or s.startswith("https://")


def _guess_ext_from_url(url: str) -> str:
    m = re.search(r"\.([a-zA-Z0-9]{1,5})(?:\?|#|$)", url)
    if not m:
        return "jpg"
    ext = m.group(1).lower()
    if ext in {"jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff"}:
        return "jpg" if ext == "jpeg" else ext
    return "jpg"


def _download_to_inputs(url: str) -> Path:
    ext = _guess_ext_from_url(url)
    target = INPUT_DIR / f"dl_{uuid.uuid4().hex}.{ext}"

    with requests.get(url, stream=True, timeout=DOWNLOAD_TIMEOUT_S) as r:
        r.raise_for_status()
        total = 0
        with open(target, "wb") as f:
            for chunk in r.iter_content(chunk_size=1024 * 1024):
                if not chunk:
                    continue
                total += len(chunk)
                if total > MAX_DOWNLOAD_BYTES:
                    raise HTTPException(status_code=413, detail=f"download too large > {MAX_DOWNLOAD_BYTES} bytes")
                f.write(chunk)

    return target


def _resolve_input_path(image: str) -> Path:
    image = image.strip()
    if _is_http_url(image):
        return _download_to_inputs(image)

    p = Path(image)
    if not p.is_file():
        raise HTTPException(status_code=404, detail=f"file not found: {image}")
    return p


@app.get("/healthz")
def healthz():
    return {"ok": True}


@app.post("/enhance", response_model=EnhanceOut)
async def enhance(req: EnhanceIn, request: Request):
    try:
        input_path = await run_in_threadpool(_resolve_input_path, req.image)

        params = EnhanceParams(
            model_name=req.model_name,
            outscale=req.outscale,
            denoise_strength=req.denoise_strength,
            tile=req.tile,
            tile_pad=req.tile_pad,
            pre_pad=req.pre_pad,
            fp32=req.fp32,
            gpu_id=DEFAULT_GPU_ID,
            face_enhance=req.face_enhance,
        )

        out_basename = make_output_basename("esr")
        start = time.time()
        result_path, elapsed_s = await run_in_threadpool(
            service.enhance_to_file,
            input_path,
            OUTPUT_DIR,
            params,
            req.ext,
            "out",
            out_basename,
        )
        elapsed_ms = int((time.time() - start) * 1000)

        # 返回可访问地址
        base = str(request.base_url).rstrip("/")
        result_name = Path(result_path).name
        result_url = f"{base}/results/{result_name}"

        return EnhanceOut(
            ok=True,
            result_path=result_path,
            result_url=result_url,
            elapsed_ms=elapsed_ms,
            model_name=req.model_name.split(".")[0],
        )
    except HTTPException:
        raise
    except requests.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"download failed: {e}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.exception_handler(Exception)
async def _unhandled_exception_handler(request: Request, exc: Exception):
    return JSONResponse(status_code=500, content={"ok": False, "detail": str(exc)})
