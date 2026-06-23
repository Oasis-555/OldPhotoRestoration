import os
import cv2
import torch
import uuid
from flask import Flask, request, jsonify
from basicsr.utils import imwrite
from gfpgan import GFPGANer

# 初始化 Flask 应用
app = Flask(__name__)

# ========== 固定配置 (沿用原脚本默认值) ==========
OUTPUT_ROOT = 'results'
MODEL_VERSION = '1.3'
UPSCALE = 2
BG_UPSAMPLER = 'realesrgan'
BG_TILE = 400
WEIGHT = 0.5

# 确保输出目录存在
os.makedirs(os.path.join(OUTPUT_ROOT, 'restored_imgs'), exist_ok=True)

# ========== 全局模型变量 ==========
restorer = None

def load_model():
    """预加载 GFPGAN 模型和背景上采样器"""
    global restorer

    print("正在加载 GFPGAN 模型...")

    # ------------------------ 设置背景上采样器 (RealESRGAN) ------------------------
    bg_upsampler = None
    if BG_UPSAMPLER == 'realesrgan' and torch.cuda.is_available():
        try:
            from basicsr.archs.rrdbnet_arch import RRDBNet
            from realesrgan import RealESRGANer
            
            model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32, scale=2)
            bg_upsampler = RealESRGANer(
                scale=2,
                model_path='https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.1/RealESRGAN_x2plus.pth',
                model=model,
                tile=BG_TILE,
                tile_pad=10,
                pre_pad=0,
                half=True)
            print("RealESRGAN 背景上采样器加载成功。")
        except Exception as e:
            print(f"RealESRGAN 加载失败，将仅使用 GFPGAN: {e}")
            bg_upsampler = None
    elif not torch.cuda.is_available():
        print("未检测到 CUDA，跳过 RealESRGAN，仅使用 GFPGAN (CPU 模式会较慢)。")

    # ------------------------ 设置 GFPGAN 修复器 ------------------------
    if MODEL_VERSION == '1':
        arch = 'original'
        channel_multiplier = 1
        model_name = 'GFPGANv1'
        url = 'https://github.com/TencentARC/GFPGAN/releases/download/v0.1.0/GFPGANv1.pth'
    elif MODEL_VERSION == '1.2':
        arch = 'clean'
        channel_multiplier = 2
        model_name = 'GFPGANCleanv1-NoCE-C2'
        url = 'https://github.com/TencentARC/GFPGAN/releases/download/v0.2.0/GFPGANCleanv1-NoCE-C2.pth'
    elif MODEL_VERSION == '1.3':
        arch = 'clean'
        channel_multiplier = 2
        model_name = 'GFPGANv1.3'
        url = 'https://github.com/TencentARC/GFPGAN/releases/download/v1.3.0/GFPGANv1.3.pth'
    elif MODEL_VERSION == '1.4':
        arch = 'clean'
        channel_multiplier = 2
        model_name = 'GFPGANv1.4'
        url = 'https://github.com/TencentARC/GFPGAN/releases/download/v1.3.0/GFPGANv1.4.pth'
    else:
        raise ValueError(f'Wrong model version {MODEL_VERSION}.')

    # 确定模型路径
    model_path = os.path.join('experiments/pretrained_models', model_name + '.pth')
    if not os.path.isfile(model_path):
        model_path = os.path.join('gfpgan/weights', model_name + '.pth')
    if not os.path.isfile(model_path):
        model_path = url  # 自动下载

    restorer = GFPGANer(
        model_path=model_path,
        upscale=UPSCALE,
        arch=arch,
        channel_multiplier=channel_multiplier,
        bg_upsampler=bg_upsampler)
    
    print("GFPGAN 模型加载完成，服务已就绪！")

# ========== API 接口 ==========
@app.route('/restore', methods=['POST'])
def restore():
    """
    接收 JSON: {"image_path": "路径/到/图片.jpg"}
    返回 JSON: {"status": "success", "result_path": "路径/到/修复后图片.png"}
    """
    if restorer is None:
        return jsonify({"status": "error", "message": "Model not loaded"}), 500

    data = request.get_json()
    if not data or "image_path" not in data:
        return jsonify({"status": "error", "message": "Missing 'image_path'"}), 400

    input_path = data["image_path"]
    
    if not os.path.exists(input_path):
        return jsonify({"status": "error", "message": f"File not found: {input_path}"}), 400

    try:
        # 1. 读取图片
        img_name = os.path.basename(input_path)
        basename, ext = os.path.splitext(img_name)
        
        # 为了防止文件名冲突，给输出文件加个唯一后缀 (或者直接用原文件名)
        # 这里直接沿用原文件名，保存在 results/restored_imgs/ 下
        output_ext = ext[1:] if ext else 'png'
        save_name = f'{basename}.{output_ext}'
        save_path = os.path.join(OUTPUT_ROOT, 'restored_imgs', save_name)

        # 2. 读取图片
        input_img = cv2.imread(input_path, cv2.IMREAD_COLOR)
        if input_img is None:
            return jsonify({"status": "error", "message": "Could not read image"}), 400

        weight = float(data.get("weight", WEIGHT))
        weight = max(0.0, min(1.0, weight))

        # 3. 执行修复 (paste_back=True 表示把修复的人脸贴回原图)
        cropped_faces, restored_faces, restored_img = restorer.enhance(
            input_img,
            has_aligned=False,
            only_center_face=False,
            paste_back=True,
            weight=weight)

        # 4. 保存结果
        if restored_img is not None:
            imwrite(restored_img, save_path)
            
            # 返回绝对路径，方便调用方直接访问
            absolute_save_path = os.path.abspath(save_path)
            
            return jsonify({
                "status": "success",
                "input_path": input_path,
                "result_path": absolute_save_path,
                "result_relative_path": save_path,
                "faces": len(restored_faces),
                "weight": weight,
                "upscale": UPSCALE
            })
        else:
            return jsonify({"status": "error", "message": "No face detected or restoration failed"}), 400

    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == '__main__':
    # 屏蔽 Flask 警告
    import logging
    log = logging.getLogger('werkzeug')
    log.setLevel(logging.ERROR)

    load_model()
    app.run(host='0.0.0.0', port=5001, debug=False) # 注意端口改成 5001 了，避免和之前的 VLM 服务冲突
