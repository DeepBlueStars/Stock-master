# StockAI 工作区

本仓库是一套自用的股票分析与交易辅助系统，包含后端服务、前端控制台、ETF 数据服务和 trade-sentry 四个子项目。本文档汇总各项目 **打包 → 传输 → Docker 部署** 的标准流程；开发规范见 [AGENTS.md](./AGENTS.md)，各项目细节见对应子项目 README。

## 目录

- [StockAI 工作区](#stockai-工作区)
  - [目录](#目录)
  - [项目概览](#项目概览)
  - [部署拓扑与端口](#部署拓扑与端口)
  - [通用前提](#通用前提)
  - [1. StockAI 后端（Java / Maven）](#1-stockai-后端java--maven)
    - [1.1 打包（Windows）](#11-打包windows)
    - [1.2 需要传输的文件](#12-需要传输的文件)
    - [1.3 服务器部署](#13-服务器部署)
  - [2. StockAI-Console 前端（Vue / Vite）](#2-stockai-console-前端vue--vite)
    - [2.1 打包（Windows）](#21-打包windows)
    - [2.2 需要传输的文件](#22-需要传输的文件)
    - [2.5 服务器部署](#25-服务器部署)
    - [2.4 验证](#24-验证)
  - [3. etf-service（Python / FastAPI）](#3-etf-servicepython--fastapi)
    - [3.1 打包（Windows）](#31-打包windows)
    - [3.2 需要传输的文件](#32-需要传输的文件)
    - [3.3 服务器部署](#33-服务器部署)
    - [3.4 验证](#34-验证)
  - [4. 完整部署顺序](#4-完整部署顺序)
  - [5. 常见问题](#5-常见问题)
  - [6. 子项目 README](#6-子项目-readme)

## 项目概览

| 项目 | 技术栈 | 容器端口 | 打包方式 | 需要上传的文件 |
|---|---|---|---|---|
| **StockAI** | Java 21 + Spring Boot 3.5 | 8888 | Maven | `StockAI/target/stockai-service-1.0.0.jar` |
| **StockAI-Console** | Vue 3 + Vite | 8889（nginx） | `StockAI-Console/deploy.bat` | `StockAI-Console/dist.tar.gz` |
| **etf-service** | Python + FastAPI | 8887 | `etf-service/build_windows.bat` | `dist/etf-service-docker.tar.gz` + `deploy_linux.sh` |
| **trade-sentry** | Vue 3 + FastAPI | 3000 / 8000 | 暂无打包脚本 | 暂未纳入本文档 |

> trade-sentry 目前没有 Dockerfile / 部署脚本，暂不提供 Docker 部署流程；需要时再补充。

## 部署拓扑与端口

```
浏览器
  └─ http://<服务器IP>:8889        StockAI-Console（nginx 容器）
        └─ /srv/*  反向代理 → StockAI 后端（Java 容器 :8888）
              └─ MongoDB :27017（tradingagents）

etf-service（Python 容器 :8887）──→ MongoDB :27017
```

| 服务 | 容器名 | 镜像 | 端口 |
|---|---|---|---|
| StockAI 后端 | `stockai-service` | `stockai-service:latest` | 8888 |
| StockAI-Console | `stockai-console` | `nginx:alpine` | 8889 |
| etf-service | `etf-service-container` | `etf-service:latest` | 8887 |

## 通用前提

- Linux 服务器已安装 Docker，且能拉取基础镜像（`eclipse-temurin`、`nginx`、`python`）。
- 需要可访问的 MongoDB（数据库名默认 `tradingagents`）。
- 防火墙/安全组放行 **8887、8888、8889**。
- 容器内 `localhost` 指容器自身，数据库地址不要写 `localhost`：
  - 数据库在宿主机：`--add-host=host.docker.internal:host-gateway` + `mongodb://host.docker.internal:27017/tradingagents`
  - 数据库在其他机器：直接写该机器 IP。
- 定时任务依赖时区，建议所有容器加 `-e TZ=Asia/Shanghai`（etf-service 可在解压目录 `.env` 中写 `TZ=Asia/Shanghai`，部署脚本会通过 `--env-file` 加载）。

---

## 1. StockAI 后端（Java / Maven）

### 1.1 打包（Windows）

```powershell
cd d:\WorkSpace\MainProject\StockAI-master\StockAI
mvn clean package -DskipTests
# 产物：target\stockai-service-1.0.0.jar
```

环境要求：JDK 21、Maven（本机验证版本 3.6.3）。

### 1.2 需要传输的文件

| 上传 | 不上传 |
|---|---|
| `target/stockai-service-1.0.0.jar` | `src/`、`target/classes/`、`target/*.original`、`.mvn/`、`pom.xml`（目标机不需要重新编译） |

`application.yml` 已打进 jar，配置通过环境变量覆盖（见 1.3）。首次部署还需在服务器创建 `Dockerfile`。

### 1.3 服务器部署

常用环境变量（Spring Boot 宽松绑定，按需覆盖 jar 内 `application.yml`）：

| 变量 | 对应配置 | 说明 |
|---|---|---|
| `SERVER_PORT` | `server.port` | 默认 8888 |
| `SPRING_DATA_MONGODB_URI` | `spring.data.mongodb.uri` | 默认 `mongodb://localhost:27017/tradingagents`，容器内必须覆盖 |
| `SPRING_AI_OPENAI_API_KEY` | `spring.ai.openai.api-key` | 生产环境必须用自己的 Key |
| `SPRING_AI_OPENAI_BASE_URL` | `spring.ai.openai.base-url` | OpenAI 兼容地址 |
| `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` | `spring.ai.openai.chat.options.model` | 模型名 |
| `MONITOR_CRON` / `SCREENING_CRON` / `MARKET_REGIME_CRON` | 定时任务 | 可选 |
| `FEISHU_ENABLED` / `FEISHU_WEBHOOK_URL` / `FEISHU_SECRET` | 飞书推送 | 可选，默认关闭 |

> 仓库中的 `application.yml` 带有默认 Key，仅建议本地使用；生产部署请用环境变量覆盖，并避免把真实密钥提交到仓库。

---

## 2. StockAI-Console 前端（Vue / Vite）

### 2.1 打包（Windows）

```powershell
cd d:\WorkSpace\MainProject\StockAI-master\StockAI-Console
npm install          # 首次或依赖变更时执行
.\deploy.bat
# 产物：dist.tar.gz（内容为 dist 目录下的 index.html、assets/ 等）
```

`deploy.bat` 会依次执行：清理 `dist` → `npm run build` → `tar -czvf dist.tar.gz -C dist .`。需要 Node.js 20.19+（或 22.12+），以及 Windows 自带的 `tar` 命令。

> **运行时配置**：前端启动时会先读取 `/env.js`。使用根目录的 `deploy-stockai.sh` 部署时，脚本会按以下优先级生成该文件：① 与 `dist.tar.gz` 同级的服务器 `.env`；② tar 包内的 `.env`；③ 都没有时回退到构建时的 `VITE_API_BASE_URL`。因此换服务器时通常只需修改服务器上的 `.env` 并重新执行部署脚本，无需重新打包。

### 2.2 需要传输的文件

| 上传 | 不上传 |
|---|---|
| `StockAI-Console/dist.tar.gz` | `node_modules/`、`dist/`、`src/`、`package*.json`（目标机不需要重新编译） |
| 可选：与 `dist.tar.gz` 同级的 `.env`（服务器端配置，优先级最高） | |

说明：

- `proxy_pass` 末尾的 `/` 会去掉 `/srv` 前缀；如果前端直接用绝对地址访问后端（`VITE_API_BASE_URL=http://<backend-ip>:8888`），可以删除 `/srv/` 这段配置（后端已开启 `allowedOrigins("*")`）。
- 静态资源是 `/assets/...` 绝对路径，必须部署在站点根路径下。


### 2.5 服务器部署

前后端都上传完成后，执行部署脚本
```bash
bash deploy-stockai.sh
```

### 2.4 验证

```bash
docker ps | grep stockai-service
docker logs -f stockai-service
curl -s -X POST http://localhost:8888/config/get \
  -H 'Content-Type: application/json' -d '{"userId":"test"}'

docker ps | grep stockai-console
docker logs -f stockai-console
curl -I http://localhost:8889/
```

浏览器访问 `http://<服务器IP>:8889/` 即可。

---

## 3. etf-service（Python / FastAPI）

### 3.1 打包（Windows）

```powershell
cd d:\WorkSpace\MainProject\StockAI-master\etf-service
.\build_windows.bat
# 产物：
#   dist\etf-service-docker.tar.gz   （Dockerfile + requirements.txt + app/）
#   deploy_linux.sh
```

`build_windows.bat` 第 6 步会把 `Dockerfile`、`requirements.txt`、`app/` 打包成部署包，并自动排除 `__pycache__`、`*.pyc`。

### 3.2 需要传输的文件

| 上传 | 不上传 |
|---|---|
| `etf-service/dist/etf-service-docker.tar.gz` | `venv/`（Windows 虚拟环境，Linux 不可用）、`dist/app/`、`dist/start.bat`、`tests/`、`etf_list.csv` |
| `etf-service/deploy_linux.sh` | `build_windows.bat`（Windows 打包脚本） |

### 3.3 服务器部署

```bash
mkdir -p /opt/etf-service && cd /opt/etf-service
# 将 etf-service-docker.tar.gz 和 deploy_linux.sh 上传到该目录

chmod +x deploy_linux.sh
bash deploy_linux.sh
```

`deploy_linux.sh` 的行为：

1. 自动查找当前目录或 `./dist/` 下的 `etf-service-docker.tar.gz`（也可用第一个参数指定路径）；
2. 解压到 `脚本目录/etf-service`（可用第二个参数指定，如 `/opt/etf-service/build`）；
3. `docker build -t etf-service:latest <解压目录>`；
4. 停止/删除旧容器后 `docker run` 新容器（容器名 `etf-service-container`，端口 `8887`）；
5. `MONGO_URL`、`MONGO_DB_NAME`、`TUSHARE_TOKEN` 有值时自动 `-e` 透传；**未设置 `MONGO_URL` 时会自动探测已运行的 MongoDB 容器**（加入其 Docker 网络），没有容器则回退 `mongodb://host.docker.internal:27017/tradingagents` 并添加 `--add-host`；解压目录存在 `.env` 时通过 `--env-file` 加载（可放 `TZ=Asia/Shanghai` 等）。
6. 启动后脚本会请求 `http://localhost:8887/health`，直接显示 MongoDB 连通性和 `stock_daily_kline` 最新交易日。

等价的手动命令：

```bash
mkdir -p /opt/etf-service/build && cd /opt/etf-service
tar -xzf etf-service-docker.tar.gz -C build
docker build -t etf-service:latest build
docker run -d --name etf-service-container --restart=always -p 8887:8887 \
  -e MONGO_URL='mongodb://<mongo-ip>:27017/tradingagents' \
  -e MONGO_DB_NAME='tradingagents' \
  etf-service:latest
```

### 3.4 验证

```bash
docker ps | grep etf-service-container
docker logs -f etf-service-container
curl -I http://localhost:8887/docs
```

---

## 4. 完整部署顺序

1. MongoDB（已有可跳过）
2. StockAI 后端 → 8888
3. etf-service → 8887
4. StockAI-Console → 8889（`/srv` 反向代理到 8888）

## 5. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 容器启动后连不上数据库 | 容器内 `localhost` 是容器自身；改用宿主机 `host.docker.internal`（Linux 需 `--add-host=host.docker.internal:host-gateway`）或数据库真实 IP |
| etf-service 接口报数据库错误 / 日志出现 `localhost:27017 Connection refused` | 容器内 `localhost` 是容器自身。优先在部署时 `export MONGO_URL='mongodb://<mongo-ip>:27017/tradingagents'`；新版 `deploy_linux.sh` 会优先复用已运行的 MongoDB 容器，否则回退宿主机 `host.docker.internal`。部署完成后访问 `http://localhost:8887/health` 确认 `mongo.ok=true` |
| etf-service 日志出现 `command update requires authentication` / `code 13 Unauthorized` | MongoDB 开启了认证但连接串缺少账号密码或 `authSource`；设置 `MONGO_URL='mongodb://<user>:<password>@<host>:27017/tradingagents?authSource=admin'` 后重跑部署脚本，并用 `/health` 的 `mongo.ok=true` 确认 |
| 前端刷新子路由 404 | nginx 缺少 `try_files $uri $uri/ /index.html`（前端是 history 路由模式） |
| 前端请求后端失败 | 优先检查部署目录（与 `dist.tar.gz` 同级）`.env` 中的 `VITE_API_BASE_URL`，再执行 `deploy-stockai.sh` 重新生成 `/env.js`；手动部署时检查 nginx `/srv` 反代配置 |
| 定时任务执行时间不对 | 容器默认 UTC，加 `-e TZ=Asia/Shanghai`（etf-service 可写入解压目录 `.env`） |
| `./deploy_linux.sh: /bin/bash^M: bad interpreter` | 脚本被转成了 CRLF；在服务器执行 `sed -i 's/\r$//' deploy_linux.sh`（或 `dos2unix deploy_linux.sh`）后重试 |
| `tar` 找不到 | Windows 10 1803+ 自带 `tar`；旧系统需安装或改用 7-Zip 手动打包 |

## 6. 子项目 README

- [StockAI/README.md](./StockAI/README.md) — Java 后端
- [StockAI-Console/README.md](./StockAI-Console/README.md) — Vue 前端
- [etf-service/README.md](./etf-service/README.md) — ETF 数据服务
- [trade-sentry/README.md](./trade-sentry/README.md) — 交易打板系统（暂无 Docker 部署流程）
