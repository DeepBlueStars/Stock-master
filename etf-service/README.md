# etf-service

ETF 数据服务（Python + FastAPI + MongoDB），提供 ETF 行情/基础数据查询与定时更新，默认端口 **8887**。

## 本地启动

```bash
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8887
```

启动后访问 `http://localhost:8887/docs` 查看 API 文档。

## 打包（Windows）

```powershell
cd d:\WorkSpace\MainProject\StockAI-master\etf-service
.\build_windows.bat
# 产物：
#   dist\etf-service-docker.tar.gz   （Dockerfile + requirements.txt + app/）
#   deploy_linux.sh
```

`build_windows.bat` 第 6 步会把构建镜像所需的文件打包成部署包，并自动排除 `__pycache__`、`*.pyc`。

## 需要传输的文件

| 上传 | 说明 |
|---|---|
| `dist/etf-service-docker.tar.gz` | Docker 构建上下文：`Dockerfile` + `requirements.txt` + `app/` |
| `deploy_linux.sh` | 解压部署包 + 构建镜像 + 启动容器的脚本 |

不需要传输：

| 不上传 | 原因 |
|---|---|
| `venv/` | Windows 虚拟环境（`.exe/.dll`），Linux 不可用，镜像内会重新安装依赖 |
| `dist/app/`、`dist/start.bat` | Windows 打包副本与 Windows 启动脚本 |
| `tests/`、`etf_list.csv` | 服务运行时不引用 |
| `build_windows.bat` | Windows 打包脚本 |

## 部署为 Docker 服务（Linux）

```bash
mkdir -p /opt/etf-service && cd /opt/etf-service
# 将 etf-service-docker.tar.gz 和 deploy_linux.sh 上传到该目录

chmod +x deploy_linux.sh

export MONGO_URL='mongodb://<mongo-ip>:27017/tradingagents'
export MONGO_DB_NAME='tradingagents'
export TUSHARE_TOKEN='<token>'   # 可选
./deploy_linux.sh
```

脚本行为：

1. 自动查找当前目录或 `./dist/` 下的 `etf-service-docker.tar.gz`（也可用第一个参数指定路径）；
2. 解压到 `脚本目录/etf-service`（可用第二个参数指定其他目录）；
3. 校验 `Dockerfile` 与 `app/` 是否存在；
4. `docker build -t etf-service:latest <解压目录>`；
5. 停止/删除旧容器后启动新容器：容器名 `etf-service-container`，端口映射 `8887:8887`，`--restart=always`。

用法与环境变量：

```bash
./deploy_linux.sh                                            # 自动查找部署包
./deploy_linux.sh /path/to/etf-service-docker.tar.gz          # 指定部署包
./deploy_linux.sh etf-service-docker.tar.gz /opt/etf-service  # 指定部署包和解压目录
```

| 环境变量 | 说明 |
|---|---|
| `MONGO_URL` | MongoDB 连接串；默认 `mongodb://localhost:27017/tradingagents`，容器内 localhost 指容器自身，必须覆盖。未设置时容器内会自动使用 `host.docker.internal` |
| `MONGO_DB_NAME` | 数据库名，默认 `tradingagents` |
| `MONGO_SERVER_SELECTION_TIMEOUT_MS` | 选择可用节点超时，默认 `5000` |
| `MONGO_CONNECT_TIMEOUT_MS` | TCP 连接超时，默认 `5000` |
| `SCHEDULER_TIMEZONE` | 定时任务时区，默认 `Asia/Shanghai` |
| `TUSHARE_TOKEN` | Tushare Token，可选 |

有值时脚本会自动 `-e` 透传给容器；解压目录下若存在 `.env`，会通过 `--env-file` 一并加载（例如写 `TZ=Asia/Shanghai`）。

**MongoDB 地址自动探测**：未显式设置 `MONGO_URL` 时，脚本会先查找已运行的 MongoDB 容器并自动加入其 Docker 网络；找不到容器则回退到 `mongodb://host.docker.internal:27017/tradingagents` 并添加 `--add-host=host.docker.internal:host-gateway`。MongoDB 在其他机器时仍应显式 `export MONGO_URL='mongodb://<mongo-ip>:27017/tradingagents'`。

## 每日数据采集与排查

- 交易日 **15:30（Asia/Shanghai）** 执行个股 K 线增量更新；仅从库内最新交易日的次日开始拉取，不再每天重拉 2000 年以来的全部历史。
- 首次全量补采按每 1000 条批量 upsert，避免逐条写入过慢；baostock 日期参数会自动归一化为 `YYYY-MM-DD`（兼容 `20000101` 写法）。
- 采集/写入任务失败会自动重试 3 次；ETF 行情与基本信息写入 0 条时会记录明确错误，便于发现数据库不可达。
- 启动时的补采任务（ETF 行情、基本信息、成分股、行业概念）由调度器在启动约 5 秒后异步执行，不会阻塞 FastAPI 就绪和定时任务注册。
- 数据源顺序：akshare → baostock → tushare → 腾讯行情 → 东财/新浪；腾讯源独立于 akshare，作为兜底。
- 健康检查：`curl -s http://localhost:8887/health`，返回 `mongo.ok` 与 `latest_stock_trade_date`；日志中 `MongoDB 连接目标` 会打印实际连接串（密码已脱敏）。
- 日志出现 `command update requires authentication` / `code 13 Unauthorized`：MongoDB 开启了认证，但 `MONGO_URL` 未带账号密码或 `authSource`。示例：`mongodb://<user>:<password>@<host>:27017/tradingagents?authSource=admin`。`ping` 本身不需要权限，所以健康检查会额外执行 `dbStats` 验证认证。

## 验证

```bash
docker ps | grep etf-service-container
docker logs -f etf-service-container
curl -I http://localhost:8887/docs
```
