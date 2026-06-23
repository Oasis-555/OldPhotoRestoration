import json
from tqdm import tqdm

from modelscope import AutoModelForImageTextToText, AutoProcessor
from PIL import Image
import torch
import random as rm

device = "cuda:0"

model_path = 'SmolVLM-Cap-0.6B'
model = AutoModelForImageTextToText.from_pretrained(
    model_path,
    torch_dtype=torch.bfloat16,
    trust_remote_code=True,
).to(device)

processor = AutoProcessor.from_pretrained(
    model_path,
    trust_remote_code=True,
)


image = Image.open("demo/dog.png").convert("RGB")
messages = [
    {
        "role": "user",
        "content": [
            {"type": "image"},
            {"type": "text", "text": "请描述这张图片"},
        ],
    },
]

# 应用聊天模板
text = processor.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)

# 处理输入
inputs = processor(
    text=text,
    images=[image],
    return_tensors="pt"
)

# 生成回复
outputs = model.generate(
    **inputs.to(model.device),
    max_new_tokens=128,
    do_sample=False,
    use_cache=True,
)

# 解码输出
generated_ids = outputs[0][inputs["input_ids"].shape[1]:]
response = processor.decode(generated_ids, skip_special_tokens=True)
print(response)

