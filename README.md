# Old Photo Restoration System

老照片修复系统，包含 Vue3 前端、Spring Boot 后端、MyBatis 数据访问层，以及多个本地 AI 模型服务。系统支持照片上传、修复任务创建、修复结果对比、历史记录、相册分类、保存到相册、回收站、批量下载等功能。

## 功能概览

- 用户注册、登录、个人信息管理
- 老照片上传与修复
- 修复模式：
  - 综合修复
  - 缺损补全 / 手动涂抹修复
  - 超分增强
  - 人脸清晰化
- 修复结果滑动对比、并排对比
- 修复历史记录、单张下载、批量下载
- 个人相册、智能分类、分组管理、回收站

## 技术栈

- 前端：Vue3、Vite、Element Plus、Pinia、Axios
- 后端：Spring Boot 2.7、Java 11、MyBatis、MySQL、Redis
- 模型服务：
  - GFPGAN：人脸修复
  - Real-ESRGAN：图像超分增强
  - LaMa / simple-lama-inpainting：缺损补全
  - SmolVLM-Cap-0.6B：相册图片分类

## 目录说明

```text
PhotoProject/
├─ Old-Photo-Restoration-System-master/
│  ├─ backend/              # Spring Boot 后端
│  ├─ frontend/             # Vue3 前端
│  └─ database/init.sql     # 数据库初始化脚本
├─ GFPGAN/                  # GFPGAN 服务代码
├─ Real-ESRGAN/             # Real-ESRGAN 服务代码
├─ inpaint_service.py       # LaMa 缺损补全服务
├─ vlm_server.py            # SmolVLM 分类服务
├─ start_model_services.bat # 一键启动模型服务
├─ stop_model_services.bat  # 一键关闭模型服务
└─ requirements_inpaint.txt # LaMa 补全服务依赖
```

## 仓库未包含的内容

为了避免 GitHub 仓库过大，以下内容没有上传，需要自行准备：

```text
.venv_models/                       # Python 虚拟环境
models/big-lama.pt                  # LaMa 权重
SmolVLM-Cap-0.6B/                   # SmolVLM 本地模型
GFPGAN/experiments/pretrained_models/
GFPGAN/gfpgan/weights/
Real-ESRGAN/weights/
Real-ESRGAN/gfpgan/weights/
Old-Photo-Restoration-System-master/backend/uploads/
GFPGAN/results/
Real-ESRGAN/api_results/
inpaint_results/
```

## 需要自行下载/准备的模型

### 1. GFPGAN

用于“人脸清晰化 / 人脸修复”。

需要准备 GFPGAN 预训练权重，常见文件包括：

```text
GFPGAN/experiments/pretrained_models/GFPGANv1.3.pth
GFPGAN/gfpgan/weights/detection_Resnet50_Final.pth
GFPGAN/gfpgan/weights/parsing_parsenet.pth
```

如果缺少权重，`5001` 人脸修复服务可能无法启动，或启动后无法正常修复。

### 2. Real-ESRGAN

用于“超分增强”。

需要准备权重，例如：

```text
Real-ESRGAN/weights/RealESRGAN_x4plus.pth
Real-ESRGAN/weights/RealESRGAN_x2plus.pth
```

后端默认调用：

```text
http://127.0.0.1:8000/enhance
```

### 3. LaMa

用于“缺损补全 / 手动涂抹修复”。

推荐准备：

```text
models/big-lama.pt
```

并安装依赖：

```powershell
pip install -r requirements_inpaint.txt
```

如果使用 Anaconda，建议单独创建环境，例如：

```powershell
conda create -n lama-inpaint python=3.10
conda activate lama-inpaint
pip install -r requirements_inpaint.txt
```

默认服务地址：

```text
http://127.0.0.1:5002/inpaint
```

### 4. SmolVLM-Cap-0.6B

用于“相册智能分类”，例如人物、动物、风景等分类。

需要自行下载 SmolVLM-Cap-0.6B 模型文件，并放到：

```text
SmolVLM-Cap-0.6B/
```

默认服务地址：

```text
http://127.0.0.1:5000/generate
```

## 环境要求

- Windows 10/11
- JDK 11
- Maven 3.8+
- Node.js 16+
- MySQL 8+
- Redis
- Python 3.10 推荐
- 建议内存 16GB 及以上

模型服务比较占内存。如果电脑内存不足，不建议一次性启动全部模型，可以按演示功能只启动需要的模型。

## 数据库初始化

创建 MySQL 数据库并执行：

```text
Old-Photo-Restoration-System-master/database/init.sql
```

后端数据库配置位于：

```text
Old-Photo-Restoration-System-master/backend/src/main/resources/application.yml
```

默认配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/photo_restoration
    username: root
    password: 123123123
```

请根据本机 MySQL 账号密码修改。

## 启动方式

### 1. 启动模型服务

在项目根目录执行：

```powershell
cd D:\Engineering\PhotoProject
.\start_model_services.bat
```

对应端口：

| 端口 | 服务 |
|---|---|
| 5000 | SmolVLM 相册分类 |
| 5001 | GFPGAN 人脸修复 |
| 5002 | LaMa 缺损补全 |
| 8000 | Real-ESRGAN 超分增强 |

检查端口：

```powershell
netstat -ano | findstr /R /C:":5000 .*LISTENING" /C:":5001 .*LISTENING" /C:":5002 .*LISTENING" /C:":8000 .*LISTENING"
```

关闭模型服务：

```powershell
.\stop_model_services.bat
```

### 2. 启动后端

```powershell
cd D:\Engineering\PhotoProject\Old-Photo-Restoration-System-master\backend
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080/api
```

### 3. 启动前端

```powershell
cd D:\Engineering\PhotoProject\Old-Photo-Restoration-System-master\frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

也可以使用项目自带脚本启动前后端：

```powershell
cd D:\Engineering\PhotoProject\Old-Photo-Restoration-System-master
.\start-all.bat
```

## 常见问题

### 1. 修复时一直转圈

通常是对应模型服务没有启动，或模型服务正在加载。先检查端口：

```powershell
netstat -ano | findstr /R /C:":5001 .*LISTENING" /C:":5002 .*LISTENING" /C:":8000 .*LISTENING"
```

### 2. 缺损补全不可用

检查 LaMa：

```powershell
Invoke-RestMethod http://127.0.0.1:5002/healthz
```

正常应返回：

```json
{"ok": true}
```

### 3. 后端启动提示端口占用

说明 `8080` 已被占用。可以关闭占用进程，或修改 `application.yml` 中的：

```yaml
server:
  port: 8080
```

### 4. Java 提示内存不足

模型服务会占用较多内存。可以先关闭不需要的模型：

```powershell
cd D:\Engineering\PhotoProject
.\stop_model_services.bat
```

然后只启动当前要演示的模型。

## 说明

本仓库主要保存代码、配置和启动脚本，不直接保存大型模型权重和本地运行数据。首次运行前，请按上文说明下载模型权重并放置到对应目录。
