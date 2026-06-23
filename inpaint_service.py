import os
import shutil
import subprocess
import tempfile
import uuid

import numpy as np
from flask import Flask, jsonify, request
from PIL import Image, ImageFilter, ImageOps

try:
    import cv2
except ImportError:
    cv2 = None


app = Flask(__name__)

OUTPUT_ROOT = os.getenv("INPAINT_OUTPUT_ROOT", "inpaint_results")
DEFAULT_MODEL = os.getenv("INPAINT_MODEL", "auto").lower()
DEFAULT_LAMA_MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models", "big-lama.pt")
LAMA_REPO = os.getenv("LAMA_REPO", "").strip()
LAMA_MODEL_PATH = os.getenv("LAMA_MODEL_PATH", os.getenv("LAMA_MODEL", DEFAULT_LAMA_MODEL)).strip()
LAMA_PYTHON = os.getenv("LAMA_PYTHON", "python").strip()
LAMA_DEVICE = os.getenv("LAMA_DEVICE", "cpu").strip()
os.makedirs(OUTPUT_ROOT, exist_ok=True)


def _auto_scratch_mask_cv2(img):
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (3, 3), 0)

    line_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (17, 3))
    dark_lines = cv2.morphologyEx(gray, cv2.MORPH_BLACKHAT, line_kernel)
    bright_lines = cv2.morphologyEx(gray, cv2.MORPH_TOPHAT, line_kernel)
    detail = cv2.max(dark_lines, bright_lines)

    _, mask = cv2.threshold(detail, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    edges = cv2.Canny(gray, 60, 160)
    mask = cv2.bitwise_or(mask, edges)

    close_kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, close_kernel, iterations=1)
    mask = cv2.dilate(mask, close_kernel, iterations=1)

    # Avoid over-inpainting textured photos. If the auto mask is too large,
    # keep only stronger line-like damage candidates.
    coverage = float(np.count_nonzero(mask)) / float(mask.size)
    if coverage > 0.08:
        _, mask = cv2.threshold(detail, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        mask = cv2.dilate(mask, close_kernel, iterations=1)

    return mask


def _load_mask_cv2(mask_path, shape):
    mask = cv2.imread(mask_path, cv2.IMREAD_GRAYSCALE)
    if mask is None:
        raise ValueError(f"Could not read mask: {mask_path}")
    if mask.shape[:2] != shape[:2]:
        mask = cv2.resize(mask, (shape[1], shape[0]), interpolation=cv2.INTER_NEAREST)
    _, mask = cv2.threshold(mask, 10, 255, cv2.THRESH_BINARY)
    return mask


def _auto_scratch_mask_pil(image):
    gray = ImageOps.grayscale(image).filter(ImageFilter.GaussianBlur(radius=0.8))
    edges = gray.filter(ImageFilter.FIND_EDGES)
    arr = np.asarray(edges, dtype=np.uint8)
    threshold = max(24, int(arr.mean() + arr.std() * 1.2))
    mask = (arr > threshold).astype(np.uint8) * 255

    coverage = float(np.count_nonzero(mask)) / float(mask.size)
    if coverage > 0.08:
        threshold = max(32, int(arr.mean() + arr.std() * 2.0))
        mask = (arr > threshold).astype(np.uint8) * 255
        coverage = float(np.count_nonzero(mask)) / float(mask.size)
    if coverage > 0.08:
        threshold = max(threshold, int(np.percentile(arr, 99.2)))
        mask = (arr > threshold).astype(np.uint8) * 255

    coverage = float(np.count_nonzero(mask)) / float(mask.size)
    mask_image = Image.fromarray(mask, mode="L")
    if coverage < 0.04:
        mask_image = mask_image.filter(ImageFilter.MaxFilter(3))
    return mask_image


def _load_mask_pil(mask_path, size):
    mask = Image.open(mask_path).convert("L")
    if mask.size != size:
        mask = mask.resize(size, Image.Resampling.NEAREST)
    return mask.point(lambda p: 255 if p > 10 else 0)


def _can_run_lama_cli():
    if not LAMA_REPO or not LAMA_MODEL_PATH:
        return False
    predict_py = os.path.join(LAMA_REPO, "bin", "predict.py")
    return os.path.exists(predict_py) and os.path.exists(LAMA_MODEL_PATH)


def _run_simple_lama(image_path, mask_path, save_path):
    if not os.environ.get("LAMA_MODEL") and os.path.exists(LAMA_MODEL_PATH):
        os.environ["LAMA_MODEL"] = LAMA_MODEL_PATH
    try:
        from simple_lama_inpainting import SimpleLama
    except ImportError as exc:
        raise RuntimeError("simple-lama-inpainting is not installed") from exc

    image = Image.open(image_path).convert("RGB")
    mask = _load_mask_pil(mask_path, image.size)
    result = SimpleLama()(image, mask)
    result.save(save_path)
    return "lama_simple_lama"


def _run_lama_cli(image_path, mask_path, save_path):
    if not _can_run_lama_cli():
        raise RuntimeError("LaMa CLI is not configured. Set LAMA_REPO and LAMA_MODEL_PATH.")

    with tempfile.TemporaryDirectory(prefix="lama_inpaint_") as tmpdir:
        input_dir = os.path.join(tmpdir, "input")
        output_dir = os.path.join(tmpdir, "output")
        os.makedirs(input_dir, exist_ok=True)
        os.makedirs(output_dir, exist_ok=True)

        image_name = "image.png"
        mask_name = "image_mask.png"
        Image.open(image_path).convert("RGB").save(os.path.join(input_dir, image_name))
        _load_mask_pil(mask_path, Image.open(image_path).size).save(os.path.join(input_dir, mask_name))

        env = os.environ.copy()
        env["PYTHONPATH"] = LAMA_REPO + os.pathsep + env.get("PYTHONPATH", "")
        env["TORCH_HOME"] = LAMA_REPO
        cmd = [
            LAMA_PYTHON,
            os.path.join(LAMA_REPO, "bin", "predict.py"),
            f"model.path={LAMA_MODEL_PATH}",
            f"indir={input_dir}",
            f"outdir={output_dir}",
            f"device={LAMA_DEVICE}",
        ]
        subprocess.run(cmd, cwd=LAMA_REPO, env=env, check=True, capture_output=True, text=True)

        candidates = []
        for root, _, files in os.walk(output_dir):
            for file_name in files:
                lower = file_name.lower()
                if lower.endswith((".png", ".jpg", ".jpeg", ".webp")) and "mask" not in lower:
                    candidates.append(os.path.join(root, file_name))
        if not candidates:
            raise RuntimeError("LaMa CLI finished but did not produce an output image")
        shutil.copyfile(candidates[0], save_path)
    return "lama_cli"


def _run_lama_inpaint(image_path, mask_path, save_path):
    errors = []
    try:
        return _run_simple_lama(image_path, mask_path, save_path)
    except Exception as exc:
        errors.append(str(exc))

    try:
        return _run_lama_cli(image_path, mask_path, save_path)
    except Exception as exc:
        errors.append(str(exc))

    raise RuntimeError("LaMa unavailable: " + " | ".join(errors))


def _inpaint_pil(image, mask, iterations=24):
    arr = np.asarray(image.convert("RGB"), dtype=np.float32)
    mask_arr = np.asarray(mask, dtype=np.uint8) > 0

    if not np.any(mask_arr):
        return image.convert("RGB")

    known = ~mask_arr
    h, w = mask_arr.shape

    for _ in range(iterations):
        unresolved = ~known
        if not np.any(unresolved):
            break

        padded_img = np.pad(arr, ((1, 1), (1, 1), (0, 0)), mode="edge")
        padded_known = np.pad(known, ((1, 1), (1, 1)), mode="constant", constant_values=False)
        total = np.zeros_like(arr)
        count = np.zeros((h, w, 1), dtype=np.float32)

        for dy in range(3):
            for dx in range(3):
                if dy == 1 and dx == 1:
                    continue
                neighbor_known = padded_known[dy:dy + h, dx:dx + w]
                total += padded_img[dy:dy + h, dx:dx + w] * neighbor_known[..., None]
                count += neighbor_known[..., None].astype(np.float32)

        fillable = unresolved & (count[..., 0] > 0)
        if not np.any(fillable):
            break
        arr[fillable] = total[fillable] / count[fillable]
        known[fillable] = True

    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), mode="RGB")


@app.route("/healthz", methods=["GET"])
def healthz():
    return jsonify({"ok": True})


@app.route("/inpaint", methods=["POST"])
def inpaint():
    data = request.get_json(silent=True) or {}
    image_path = data.get("image_path") or data.get("image")
    if not image_path:
        return jsonify({"status": "error", "message": "Missing image_path"}), 400
    if not os.path.exists(image_path):
        return jsonify({"status": "error", "message": f"File not found: {image_path}"}), 404

    try:
        radius = float(data.get("radius", 3.0))
        radius = max(1.0, min(9.0, radius))
        basename, ext = os.path.splitext(os.path.basename(image_path))
        ext = ext if ext else ".png"
        save_name = f"{basename}_inpaint_{uuid.uuid4().hex[:8]}{ext}"
        save_path = os.path.join(OUTPUT_ROOT, save_name)
        mask_path = data.get("mask_path")
        model = str(data.get("model") or DEFAULT_MODEL or "auto").lower()
        if model not in {"auto", "lama", "opencv", "pil"}:
            model = "auto"

        if model in {"auto", "lama"} and mask_path:
            try:
                method = _run_lama_inpaint(image_path, mask_path, save_path)
                mask_arr = np.asarray(_load_mask_pil(mask_path, Image.open(image_path).size), dtype=np.uint8)
                coverage = float(np.count_nonzero(mask_arr)) / float(mask_arr.size)
                return jsonify({
                    "status": "success",
                    "input_path": image_path,
                    "result_path": os.path.abspath(save_path),
                    "result_relative_path": save_path,
                    "mask_coverage": coverage,
                    "method": method,
                    "model": "lama",
                })
            except Exception as e:
                if model == "lama":
                    raise
                app.logger.warning("LaMa unavailable, falling back to OpenCV/PIL inpaint: %s", e)

        if cv2 is not None:
            img = cv2.imread(image_path, cv2.IMREAD_COLOR)
            if img is None:
                return jsonify({"status": "error", "message": "Could not read image"}), 400

            mask = _load_mask_cv2(mask_path, img.shape) if mask_path else _auto_scratch_mask_cv2(img)
            restored = cv2.inpaint(img, mask, radius, cv2.INPAINT_TELEA)
            ok = cv2.imwrite(save_path, restored)
            if not ok:
                raise RuntimeError(f"Could not write output: {save_path}")
            coverage = float(np.count_nonzero(mask)) / float(mask.size)
            method = "opencv_telea_auto_mask" if not mask_path else "opencv_telea_user_mask"
        else:
            image = Image.open(image_path).convert("RGB")
            mask = _load_mask_pil(mask_path, image.size) if mask_path else _auto_scratch_mask_pil(image)
            restored = _inpaint_pil(image, mask, iterations=int(radius * 8))
            restored.save(save_path)
            mask_arr = np.asarray(mask, dtype=np.uint8)
            coverage = float(np.count_nonzero(mask_arr)) / float(mask_arr.size)
            method = "pillow_neighbor_auto_mask" if not mask_path else "pillow_neighbor_user_mask"

        return jsonify({
            "status": "success",
            "input_path": image_path,
            "result_path": os.path.abspath(save_path),
            "result_relative_path": save_path,
            "mask_coverage": coverage,
            "method": method,
            "model": "opencv" if cv2 is not None else "pil",
        })
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


if __name__ == "__main__":
    import logging

    log = logging.getLogger("werkzeug")
    log.setLevel(logging.ERROR)
    app.run(host="0.0.0.0", port=5002, debug=False)
