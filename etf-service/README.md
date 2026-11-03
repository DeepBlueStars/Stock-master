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

## 历史日线回填（一次性 / 按需）

存量 `etf_daily_quotes` 是用「实时快照」一天天攒出来的（每只约 150 根，且周末也会写一行、
东财改版后 `open/high/low` 一度被写成 0），止盈止损要算 MA60 和「持仓期最高收盘」，
需要先补真实历史。回填脚本：

```bash
# 1. 先空跑几只看看数据质量（不写库）
python -m app.tasks.backfill_kline --kind etf --limit 5 --dry-run

# 2. 全量回填全部 ETF（约 1720 只，限速 1.2s/只，预计 40 分钟）
python -m app.tasks.backfill_kline --kind etf --ensure-index

# 3. 指定持仓标的
python -m app.tasks.backfill_kline --kind etf --symbols 515180,512480 --full --ensure-index
python -m app.tasks.backfill_kline --kind stock --symbols 600519,000001 --full --ensure-index

# 4. 顺带清理周末快照写进去的假交易日（先不加参数，看统计再决定）
python -m app.tasks.backfill_kline --kind etf --full --ensure-index --prune-orphans
```

要点：

- **幂等**：按 `symbol + trade_date` upsert，可反复执行，不会产生重复行。
- **增量**：不加 `--full` 时从库内最新交易日的次日起补，适合每日盘后跑一次。
- **必须加 `--ensure-index`**：`etf_daily_quotes` / `stock_daily_kline` 目前**都没有 `(symbol, trade_date)` 索引**，
  逐行 upsert 会退化成全表扫描（实测 100ms/行，1661 行要 160 秒；建索引后 1.1 秒）。
- **限速与熔断**：默认 1.2s/只，连续 5 只全源失败即中止，避免在被封禁时继续打接口。
- **数据源顺序**：ETF 走新浪（`fund_etf_hist_sina`，东财被 IP 封禁时仍可用）→ 东财（`fund_etf_hist_em`）；
  个股走新浪（`stock_zh_a_daily`）→ 东财 → 腾讯。
- **成交量单位**统一为「股」：东财接口返回「手」，脚本内 ×100 折算；新浪本身就是股。
- 连接串：默认 `localhost`，回填生产库时用环境变量 `MONGO_URL` 指定。

## 日线自动更新

分两档，都是为了不让「补数据」挡住收盘后的判定：

- **每日 16:20**（`update_kline_incremental`，交易日）：只补**关注池**——`position`、`trade_plans`、
  `user_favorites` 里出现的标的，按 ETF / 个股分开跑增量（`--full` 关闭）。现库 12 只，约 30 秒。
- **每周六 02:00**（`update_kline_weekly_sweep`，全量）：把全市场 1720 只 ETF + 720 只个股各扫一遍。
  2440 只逐个请求要一个多小时，放在周末凌晨才不会和 17:30 的止盈止损判定抢时间。

两者都幂等 upsert，失败重试；关注池为空时直接跳过，不会退化成全市场。

写入权限约定：

- `etf_daily_quotes` / `stock_daily_kline` 的**权威写入方是回填服务**（完整 OHLC，成交量按「股」）。
- 30 分钟一次的实时快照任务只做 **insert-if-absent**：当日已有行就跳过，绝不覆盖回填结果。
  否则快照会把盘中的 `open/high/low`（含 0 值）盖到收盘后的真实日线上。
- 手工补历史仍用上一节的 CLI；日常运维不需要手动执行。


## 验证

```bash
docker ps | grep etf-service-container
docker logs -f etf-service-container
curl -I http://localhost:8887/docs
```
