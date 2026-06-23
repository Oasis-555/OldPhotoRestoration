import os
import time
import uuid
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple, Union

import cv2
import torch
from basicsr.archs.rrdbnet_arch import RRDBNet
from basicsr.utils.download_util import load_file_from_url

from realesrgan import RealESRGANer
from realesrgan.archs.srvgg_arch import SRVGGNetCompact


@dataclass(frozen=True)
class EnhanceParams:
    model_name: str = "RealESRGAN_x4plus"
    outscale: float = 4.0
    denoise_strength: float = 0.5
    tile: int = 0
    tile_pad: int = 10
    pre_pad: int = 0
    fp32: bool = False
    gpu_id: Optional[int] = None
    face_enhance: bool = False


def _select_model(model_name: str):
    model_name = model_name.split(".")[0]

    if model_name == "RealESRGAN_x4plus":
        model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32, scale=4)
        netscale = 4
        file_url = [
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.0/RealESRGAN_x4plus.pth"
        ]
    elif model_name == "RealESRNet_x4plus":
        model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32, scale=4)
        netscale = 4
        file_url = [
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.1/RealESRNet_x4plus.pth"
        ]
    elif model_name == "RealESRGAN_x4plus_anime_6B":
        model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=6, num_grow_ch=32, scale=4)
        netscale = 4
        file_url = [
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.2.4/RealESRGAN_x4plus_anime_6B.pth"
        ]
    elif model_name == "RealESRGAN_x2plus":
        model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32, scale=2)
        netscale = 2
        file_url = [
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.1/RealESRGAN_x2plus.pth"
        ]
    elif model_name == "realesr-animevideov3":
        model = SRVGGNetCompact(num_in_ch=3, num_out_ch=3, num_feat=64, num_conv=16, upscale=4, act_type="prelu")
        netscale = 4
        file_url = [
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-animevideov3.pth"
        ]
    elif model_name == "realesr-general-x4v3":
        model = SRVGGNetCompact(num_in_ch=3, num_out_ch=3, num_feat=64, num_conv=32, upscale=4, act_type="prelu")
        netscale = 4
        file_url = [
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-wdn-x4v3.pth",
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth",
        ]
    else:
        raise ValueError(f"Unknown model_name: {model_name}")

    return model_name, model, netscale, file_url


def _resolve_model_path(model_name: str, file_urls, weights_dir: Union[str, Path]) -> str:
    weights_dir = Path(weights_dir)
    weights_dir.mkdir(parents=True, exist_ok=True)

    model_path = weights_dir / f"{model_name}.pth"
    if model_path.is_file():
        return str(model_path)

    root_dir = Path(__file__).resolve().parent
    for url in file_urls:
        model_path = Path(
            load_file_from_url(url=url, model_dir=str(root_dir / weights_dir), progress=True, file_name=None)
        )

    return str(model_path)


def _get_dni(model_name: str, model_path: str, denoise_strength: float):
    if model_name != "realesr-general-x4v3" or denoise_strength == 1:
        return model_path, None

    wdn_model_path = model_path.replace("realesr-general-x4v3", "realesr-general-wdn-x4v3")
    return [model_path, wdn_model_path], [denoise_strength, 1 - denoise_strength]


class RealESRGANService:
    def __init__(self, weights_dir: Union[str, Path] = "weights"):
        self._weights_dir = Path(weights_dir)
        self._cache_lock = threading.Lock()
        self._infer_lock = threading.Lock()
        self._cache = {}

    def _build_upsampler(self, params: EnhanceParams):
        model_name, model, netscale, file_urls = _select_model(params.model_name)
        model_path = _resolve_model_path(model_name, file_urls, self._weights_dir)
        model_path, dni_weight = _get_dni(model_name, model_path, params.denoise_strength)

        upsampler = RealESRGANer(
            scale=netscale,
            model_path=model_path,
            dni_weight=dni_weight,
            model=model,
            tile=params.tile,
            tile_pad=params.tile_pad,
            pre_pad=params.pre_pad,
            half=(not params.fp32) and torch.cuda.is_available(),
            gpu_id=params.gpu_id,
        )

        face_enhancer = None
        if params.face_enhance:
            from gfpgan import GFPGANer

            face_enhancer = GFPGANer(
                model_path="https://github.com/TencentARC/GFPGAN/releases/download/v1.3.0/GFPGANv1.3.pth",
                upscale=params.outscale,
                arch="clean",
                channel_multiplier=2,
                bg_upsampler=upsampler,
            )

        return model_name, upsampler, face_enhancer

    def _cache_key(self, params: EnhanceParams) -> Tuple:
        return (
            params.model_name.split(".")[0],
            float(params.denoise_strength),
            int(params.tile),
            int(params.tile_pad),
            int(params.pre_pad),
            bool(params.fp32),
            params.gpu_id,
            bool(params.face_enhance),
        )

    def get_engine(self, params: EnhanceParams):
        key = self._cache_key(params)
        with self._cache_lock:
            engine = self._cache.get(key)
            if engine is not None:
                return engine

            engine = self._build_upsampler(params)
            self._cache[key] = engine
            return engine

    def enhance_to_file(
        self,
        input_path: Union[str, Path],
        output_dir: Union[str, Path],
        params: EnhanceParams,
        output_ext: str = "auto",
        suffix: str = "out",
        output_basename: Optional[str] = None,
    ) -> Tuple[str, float]:
        input_path = Path(input_path)
        if not input_path.is_file():
            raise FileNotFoundError(str(input_path))

        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        img = cv2.imread(str(input_path), cv2.IMREAD_UNCHANGED)
        if img is None:
            raise ValueError(f"cv2.imread failed: {input_path}")

        img_mode = "RGBA" if (len(img.shape) == 3 and img.shape[2] == 4) else None

        model_name, upsampler, face_enhancer = self.get_engine(params)

        start = time.time()
        with self._infer_lock:
            if params.face_enhance:
                if face_enhancer is None:
                    raise RuntimeError("face_enhance enabled but face_enhancer not initialized")
                _, _, output = face_enhancer.enhance(
                    img, has_aligned=False, only_center_face=False, paste_back=True
                )
            else:
                output, _ = upsampler.enhance(img, outscale=params.outscale)
        elapsed_s = time.time() - start

        src_ext = input_path.suffix.lstrip(".") or "png"
        ext = src_ext if output_ext == "auto" else output_ext
        if img_mode == "RGBA":
            ext = "png"

        if output_basename is None:
            output_basename = input_path.stem

        if suffix == "":
            out_name = f"{output_basename}.{ext}"
        else:
            out_name = f"{output_basename}_{suffix}.{ext}"

        out_path = output_dir / out_name
        ok = cv2.imwrite(str(out_path), output)
        if not ok:
            raise RuntimeError(f"cv2.imwrite failed: {out_path}")

        return str(out_path), elapsed_s


def make_output_basename(prefix: str = "img") -> str:
    return f"{prefix}_{uuid.uuid4().hex}"
