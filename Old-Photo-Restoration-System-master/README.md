# 老照片修复系统 (Old Photo Restoration System)

基于深度学习的智能老照片修复系统，集成了图像去噪、去划痕、色彩修复、人脸增强等核心算法功能，并提供完善的用户管理、独立相册、历史记录等业务功能。

## ✨ 核心特性

- **🚀 智能修复核心**：一键修复老照片的常见损伤（划痕、噪点、褪色、模糊），支持局部人脸精修。
- **📊 动态对比视图**：提供直观的**滑动条（Slider）**与**并排（Side-by-Side）**对比模式，让修复效果一目了然。
- **📁 专属个人相册**：支持多用户隔离的图片存储。用户可以自由创建分组、批量移动和删除图片。
- **🤖 一键智能分类**：根据图片内容自动将未分类的照片进行打标并归档到相应相册分组中。
- **🕰️ 历史记录追踪**：自动记录所有上传的修复任务及其状态（处理中、已完成、失败），并可随时重载或下载。
- **⚡ 独立守护进程**：提供 `start-all.ps1` 与 `start-all.bat` 一键启动脚本，免配置同时启动前后端服务。

## 🛠️ 技术栈

### 前端 (Frontend)
- **核心框架**: Vue 3 (Composition API)
- **构建工具**: Vite
- **UI 组件库**: Element Plus
- **网络请求**: Axios
- **样式处理器**: SCSS

### 后端 (Backend)
- **核心框架**: Java 11+ / Spring Boot 2.7
- **持久层机制**: Spring Data JPA / Hibernate
- **数据库**: MySQL 8.0
- **缓存**: Redis (通过 Spring Cache 机制加速列表查询)
- **安全与鉴权**: JWT (JSON Web Token)
- **文件管理**: 本地物理隔离存储 (NIO Path 绝对路径解析)

## 📂 项目结构

```text
Old Photo Restoration System/
├── backend/                 # Spring Boot 后端项目
│   ├── src/main/java/       # 业务逻辑代码 (Controllers, Services, Entities)
│   ├── src/main/resources/  # 配置文件 (application.yml)
│   └── pom.xml              # Maven 依赖管理
├── frontend/                # Vue 3 前端项目
│   ├── src/pages/           # 页面组件 (Album, Restoration, History, Login...)
│   ├── src/stores/          # Pinia 状态管理
│   ├── package.json         # NPM 依赖管理
│   └── vite.config.js       # Vite 构建配置
├── database/                # 数据库初始化脚本
│   └── init.sql
├── start-all.ps1            # PowerShell 一键启动脚本
├── start-all.bat            # CMD 一键启动脚本
└── README.md                # 项目说明文档
```

## 🚀 快速开始

### 方式一：一键启动（推荐 Windows 用户）

确保您的电脑已安装 Java 17、Maven、Node.js、MySQL 以及 **Redis**。

> **关于 Redis 的启动（如果您使用 Docker）**：
> 请先在您的终端执行以下命令启动一个本地 Redis 实例：
> ```bash
> docker run -d --name photo-redis -p 6379:6379 redis
> ```

1. 修改 `backend/src/main/resources/application.yml` 中的数据库账号密码、Redis 配置以及邮件配置（如需找回密码功能）。
2. 确保本地 6379 端口的 Redis 服务正常运行中。
3. 双击运行根目录下的 `start-all.bat` 或在 PowerShell 中执行 `./start-all.ps1`。
4. 脚本将为您自动安装依赖、编译并同时启动前端和后端服务。
5. 访问 [http://localhost:5173](http://localhost:5173) 即可使用系统。

### 方式二：手动分步启动

**1. 初始化数据库**
```bash
mysql -u root -p < database/init.sql
```

**2. 启动后端**
```bash
cd backend
mvn clean install
mvn spring-boot:run
```
后端服务默认运行在 `http://localhost:8080`。

**3. 启动前端**
```bash
cd frontend
npm install
npm run dev
```
前端服务默认运行在 `http://localhost:5173`。

## 👥 开发者

席超博、林宇豪、桑田
