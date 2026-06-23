from flask import Flask, request, jsonify
from transformers import AutoModelForImageTextToText, AutoProcessor
from PIL import Image
import torch
import os

app = Flask(__name__)

ALBUM_LABELS = [
    "人物",
    "动物",
    "风景",
    "建筑",
    "交通工具",
    "美食",
    "合影",
    "儿童",
    "证件照",
    "活动",
    "未分类",
]

device = "cuda:0" if torch.cuda.is_available() else "cpu"
model_dtype = torch.bfloat16 if torch.cuda.is_available() else torch.float32
model_path = "SmolVLM-Cap-0.6B"

print("Loading VLM model...")
model = AutoModelForImageTextToText.from_pretrained(
    model_path,
    dtype=model_dtype,
    trust_remote_code=True,
).to(device)

processor = AutoProcessor.from_pretrained(
    model_path,
    trust_remote_code=True,
)
print("VLM model loaded.")


def normalize_album_label(text):
    if not text:
        return "未分类"

    raw = str(text).strip()
    lower = raw.lower().replace("。", " ").replace("，", " ").replace(",", " ")

    if any(k in lower for k in ["动物", "宠物", "猫", "狗", "animal", "pet", "cat", "dog", "bird"]):
        return "动物"
    if any(k in lower for k in ["风景", "自然", "山", "海", "河", "湖", "landscape", "nature", "scenery"]):
        return "风景"
    if any(k in lower for k in ["建筑", "房子", "城市", "building", "architecture", "city"]):
        return "建筑"
    if any(k in lower for k in ["交通", "汽车", "火车", "飞机", "船", "vehicle", "car", "train", "plane", "ship"]):
        return "交通工具"
    if any(k in lower for k in ["美食", "食物", "餐", "food", "meal", "dining"]):
        return "美食"
    if any(k in lower for k in ["儿童", "孩子", "小孩", "baby", "child", "kid"]):
        return "儿童"
    if any(k in lower for k in ["证件", "证件照", "id photo", "passport"]):
        return "证件照"
    if any(k in lower for k in ["合影", "全家福", "多人", "group photo", "group", "family"]):
        return "合影"
    if any(k in lower for k in ["活动", "婚礼", "聚会", "运动", "event", "party", "wedding", "sport"]):
        return "活动"
    if any(k in lower for k in ["人物", "人像", "人脸", "person", "people", "portrait", "face", "man", "woman"]):
        return "人物"

    for label in ALBUM_LABELS:
        if label in raw:
            return label
    return "未分类"


@app.route("/generate", methods=["POST"])
def generate():
    try:
        data = request.json
        if not data or "image_path" not in data:
            return jsonify({"status": "error", "message": "Missing image_path"}), 400

        image_path = data["image_path"]
        if not os.path.exists(image_path):
            return jsonify({"status": "error", "message": f"Image not found: {image_path}"}), 404

        print(f"Classifying: {image_path}")
        image = Image.open(image_path).convert("RGB")
        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image"},
                    {
                        "type": "text",
                        "text": (
                            "Classify the main subject of this image. Choose exactly one label "
                            "from: animal, person, scenery, building, vehicle, food, group photo, "
                            "child, ID photo, event, unknown. If it is a cartoon cat, dog, or pet "
                            "wearing clothes, choose animal. Output only the label."
                        ),
                    },
                ],
            },
        ]

        text = processor.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        inputs = processor(
            text=text,
            images=[image],
            return_tensors="pt",
        )

        outputs = model.generate(
            **inputs.to(model.device),
            max_new_tokens=32,
            do_sample=False,
            use_cache=True,
        )

        generated_ids = outputs[0][inputs["input_ids"].shape[1]:]
        response = processor.decode(generated_ids, skip_special_tokens=True).strip()
        label = normalize_album_label(response)

        return jsonify({"status": "success", "result": label, "raw_result": response})
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


if __name__ == "__main__":
    import logging

    log = logging.getLogger("werkzeug")
    log.setLevel(logging.ERROR)
    app.run(host="0.0.0.0", port=5000, debug=False)
