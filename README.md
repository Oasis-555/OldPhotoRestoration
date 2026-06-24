# Old Photo Restoration System

一个基于 Vue 3、Spring Boot、MyBatis 和本地 AI 模型服务构建的老照片修复与相册管理系统。系统采用前后端分离架构，通过 HTTP API 调用 GFPGAN、Real-ESRGAN、LaMa 和 SmolVLM，支持照片修复、批量处理、结果对比、相册分类和任务通知。

## 主要功能

### 用户与安全

- 用户注册、登录和 JWT 身份认证
- 邮箱唯一性校验
- 忘记密码与邮箱验证码重置
- 个人资料和密码管理
- 邮件通知、站内通知独立开关

### 照片修复

- 单张或多张照片上传，单次最多处理 10 张
- 综合修复：组合人脸修复与超分辨率增强
- 缺损补全：通过手动遮罩调用 LaMa 修复划痕、破洞和缺失区域
- 超分增强：调用 Real-ESRGAN 提升分辨率和局部细节
- 人脸清晰化：调用 GFPGAN 修复退化、模糊的人脸区域
- 每张图片可保存独立的手动遮罩
- 后台异步执行修复任务，前端显示批次进度
- 修复前后滑动对比和并排对比

### 结果与相册

- 修复历史、状态和耗时记录
- 单张下载和多张 ZIP 批量下载
- 保存当前结果或将全部结果批量保存到相册
- 自定义相册分组
- 使用 SmolVLM 按人物、动物、风景、建筑、合影等内容分类
- 相册图片批量移动和批量删除
- 回收站恢复、永久删除和定时清理

### 通知

- WebSocket 实时站内通知
- 修复完成后显示桌面式站内提示
- 可选的 HTML 邮件通知
- 多个任务在短时间内完成时，邮件会聚合发送
- 未配置 SMTP 时自动跳过邮件，不影响修复任务

## 技术栈

| 模块 | 技术 |
|---|---|
| 前端 | Vue 3、Vite、Element Plus、Pinia、Axios |
| 后端 | Spring Boot 2.7、Java 11、MyBatis |
| 数据 | MySQL 8、Redis |
| 通知 | Spring WebSocket、Spring Mail |
| 人脸修复 | GFPGAN |
| 超分辨率 | Real-ESRGAN |
| 缺损补全 | LaMa / simple-lama-inpainting |
| 相册分类 | SmolVLM-Cap-0.6B |

## 项目结构

```text
repository-root/
├─ Old-Photo-Restoration-System-master/
│  ├─ backend/                 # Spring Boot 后端
│  ├─ frontend/                # Vue 3 前端
│  ├─ database/init.sql        # MySQL 初始化脚本
│  └─ start-all.bat            # Windows 前后端启动入口
├─ GFPGAN/                     # GFPGAN 服务
├─ Real-ESRGAN/                # Real-ESRGAN 服务
├─ inpaint_service.py          # LaMa 缺损补全服务
├─ vlm_server.py               # SmolVLM 分类服务
├─ models/                     # LaMa 权重目录，需要自行创建
├─ start_model_services.bat    # Windows 模型服务启动脚本
├─ start_model_services.ps1
└─ stop_model_services.bat
```

## 仓库未包含的内容

Git 仓库不提交大型权重、虚拟环境和运行时生成文件。首次运行前需要自行准备：

```text
.venv_models/                         # 可选的统一 Python 虚拟环境
models/big-lama.pt                    # LaMa 权重
SmolVLM-Cap-0.6B/                     # SmolVLM 本地模型目录
GFPGAN/experiments/pretrained_models/
GFPGAN/gfpgan/weights/
Real-ESRGAN/weights/
```

以下目录会在运行过程中自动产生，也不会提交：

```text
Old-Photo-Restoration-System-master/backend/uploads/
GFPGAN/results/
Real-ESRGAN/api_results/
Real-ESRGAN/api_inputs/
inpaint_results/
*.log
```

## 运行环境

- JDK 11
- Maven 3.8 或更高版本
- Node.js 16 或更高版本
- MySQL 8
- Redis 6 或更高版本
- Python 3.9/3.10
- 建议内存 16 GB 以上
- NVIDIA GPU 可明显提升模型速度，但不是所有服务的强制要求

Windows 可以使用仓库中的批处理和 PowerShell 脚本。Linux、macOS 可按照下文命令分别启动各服务。

## 模型与 Python 依赖

建议在仓库根目录创建虚拟环境：

```powershell
python -m venv .venv_models
.\.venv_models\Scripts\Activate.ps1
python -m pip install --upgrade pip
```

Linux/macOS 激活方式：

```bash
source .venv_models/bin/activate
```

### GFPGAN

用于人脸清晰化。安装依赖：

```bash
pip install -r GFPGAN/requirements.txt
```

至少准备以下权重：

```text
GFPGAN/experiments/pretrained_models/GFPGANv1.3.pth
GFPGAN/gfpgan/weights/detection_Resnet50_Final.pth
GFPGAN/gfpgan/weights/parsing_parsenet.pth
```

权重可从 GFPGAN 官方项目的 Releases 和模型说明中下载。

### Real-ESRGAN

用于超分辨率增强。安装依赖：

```bash
pip install -r Real-ESRGAN/requirements.txt
pip install fastapi uvicorn requests
```

默认使用：

```text
Real-ESRGAN/weights/RealESRGAN_x4plus.pth
```

也可以增加 `RealESRGAN_x2plus.pth` 等官方权重，并通过环境变量 `REAL_ESRGAN_MODEL` 切换。

### LaMa

用于缺损补全和手动遮罩修复：

```bash
pip install -r requirements_inpaint.txt
```

将权重放置为：

```text
models/big-lama.pt
```

也可以通过环境变量指定其他位置：

```powershell
$env:LAMA_MODEL_PATH = "模型文件的实际路径"
```

### SmolVLM

用于相册智能分类。需要安装与本机 PyTorch 环境兼容的依赖：

```bash
pip install torch torchvision transformers accelerate pillow flask sentencepiece
```

下载完整模型后，将模型目录放在仓库根目录：

```text
SmolVLM-Cap-0.6B/
```

目录内应包含模型配置、处理器配置、分词器和权重文件。不要只下载单个权重文件。

## 数据库初始化

1. 启动 MySQL。
2. 执行：

```text
Old-Photo-Restoration-System-master/database/init.sql
```

3. 配置数据库连接。推荐使用环境变量，而不是修改并提交密码：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/photo_restoration?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "你的数据库密码"
```

Linux/macOS 使用 `export DB_USERNAME=...`。

后端会兼容已有数据库，并自动补充通知设置字段。

## Redis

Redis 用于密码重置验证码和部分缓存功能。默认连接：

```text
localhost:6379
```

可通过以下环境变量修改：

```text
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
```

## 邮件通知配置

邮件功能默认关闭，因为仓库不会保存邮箱账号和授权码。需要启用时配置：

```powershell
$env:MAIL_HOST = "smtp.qq.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "发件邮箱"
$env:MAIL_PASSWORD = "SMTP 授权码"
```

注意：

- `MAIL_PASSWORD` 通常是邮箱服务商生成的 SMTP 授权码，不是网页登录密码。
- QQ、163、Gmail 等邮箱需要先在邮箱设置中开启 SMTP。
- 用户还需要在个人中心开启“邮件通知”。
- 不配置邮箱时，站内通知、照片修复和其他功能仍然可以正常运行。

生产环境还应设置独立的 JWT 密钥：

```powershell
$env:JWT_SECRET = "长度足够的随机字符串"
```

## 启动模型服务

### Windows 一键启动

默认脚本使用：

```text
.venv_models/Scripts/python.exe
```

如果不同模型位于不同 Python 环境，可先设置：

```powershell
$env:MODEL_PYTHON = "通用模型环境中的 python.exe"
$env:LAMA_PYTHON = "LaMa 环境中的 python.exe"
$env:SMOLVLM_PYTHON = "SmolVLM 环境中的 python.exe"
.\start_model_services.ps1
```

也可以双击：

```text
start_model_services.bat
```

脚本根据自身所在目录定位项目，不依赖固定盘符或用户名。

### 手动启动

```bash
# GFPGAN，端口 5001
cd GFPGAN
python app.py

# LaMa，端口 5002
cd ..
python inpaint_service.py

# Real-ESRGAN，端口 8000
cd Real-ESRGAN
python -m uvicorn realesrgan_http_api:app --host 0.0.0.0 --port 8000

# SmolVLM，端口 5000，可选
cd ..
python vlm_server.py
```

| 端口 | 服务 | 用途 |
|---|---|---|
| 5000 | SmolVLM | 相册智能分类 |
| 5001 | GFPGAN | 人脸清晰化 |
| 5002 | LaMa | 划痕和缺损补全 |
| 8000 | Real-ESRGAN | 超分辨率增强 |

关闭 Windows 模型服务：

```powershell
.\stop_model_services.ps1
```

## 启动后端

在仓库根目录执行：

```powershell
cd .\Old-Photo-Restoration-System-master\backend
mvn spring-boot:run
```

内存较紧张时，可以打包并限制 Java 堆内存：

```powershell
mvn package -DskipTests
java -Xms128m -Xmx512m -jar .\target\photo-restoration-backend-1.0.0.jar
```

后端默认地址：

```text
http://localhost:8080/api
```

## 启动前端

新开终端，在仓库根目录执行：

```powershell
cd .\Old-Photo-Restoration-System-master\frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

Windows 也可以进入 `Old-Photo-Restoration-System-master` 后运行 `start-all.bat`，同时启动前后端。

## 服务调用关系

```text
浏览器
  │
  ├─ Vue 3 前端 :5173
  │     ├─ REST API
  │     └─ WebSocket 通知
  │
  └─ Spring Boot 后端 :8080
        ├─ MySQL :3306
        ├─ Redis :6379
        ├─ SmolVLM :5000
        ├─ GFPGAN :5001
        ├─ LaMa :5002
        └─ Real-ESRGAN :8000
```

## 常见问题

### 修复任务长时间没有结果

检查对应模型端口是否正在监听。综合修复至少需要 GFPGAN 和 Real-ESRGAN；缺损补全需要 LaMa。

```powershell
netstat -ano | findstr /R /C:":5000 .*LISTENING" /C:":5001 .*LISTENING" /C:":5002 .*LISTENING" /C:":8000 .*LISTENING"
```

### 修复结果和原图相同

通常表示模型服务没有真正加载权重，或者接口使用了兜底处理。检查模型窗口和根目录下的服务日志，并确认权重位置正确。

### 保存到相册或批量下载失败

确认模型返回的结果文件仍然存在，并确保后端对上传目录、模型输出目录具有读写权限。若 Real-ESRGAN 输出目录不在默认位置，可设置：

```powershell
$env:ESRGAN_RESULTS_DIR = "Real-ESRGAN 输出目录的实际路径"
```

### 邮件没有发送

依次确认：

1. `MAIL_USERNAME` 和 `MAIL_PASSWORD` 已设置。
2. 邮箱已开启 SMTP，使用的是授权码。
3. 用户注册邮箱真实有效。
4. 个人中心的邮件通知开关已开启。
5. 后端日志中没有 SMTP 连接或认证错误。

### Java 启动提示内存不足

AI 服务通常比前后端占用更多内存。可以只启动本次需要的模型，并使用限制堆内存的 JAR 启动方式：

```powershell
java -Xms128m -Xmx512m -jar .\target\photo-restoration-backend-1.0.0.jar
```

### 端口被占用

默认端口为 `5000`、`5001`、`5002`、`5173`、`8000` 和 `8080`。关闭已有进程，或者同步修改服务端口与后端 `application.yml` 中的模型地址。

## 安全说明

- 不要将数据库密码、SMTP 授权码、JWT 私钥提交到 Git。
- 建议通过环境变量或未纳入版本控制的本地配置文件提供敏感信息。
- `uploads`、模型结果和日志可能包含用户图片或本机路径，不应上传到公开仓库。
