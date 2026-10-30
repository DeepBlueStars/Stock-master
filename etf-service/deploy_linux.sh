#!/bin/bash

# ETF服务 - Linux Docker部署脚本
#
# 用法：
#   ./deploy_linux.sh                                    # 自动查找同目录下的 etf-service-docker.tar.gz
#   ./deploy_linux.sh /path/to/etf-service-docker.tar.gz
#   ./deploy_linux.sh etf-service-docker.tar.gz /opt/etf-service
#
# 可选环境变量（有值时透传给容器）：
#   MONGO_URL、MONGO_DB_NAME、TUSHARE_TOKEN、TZ、SCHEDULER_TIMEZONE
# 若解压目录下存在 .env 文件，也会通过 --env-file 传给容器
# 未设置 MONGO_URL 时：优先复用已运行的 MongoDB 容器，否则回退到宿主机 host.docker.internal

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TAR_FILE="$1"
DEPLOY_DIR="${2:-$SCRIPT_DIR/etf-service}"

PROJECT_NAME="etf-service"
IMAGE_NAME="etf-service:latest"
CONTAINER_NAME="etf-service-container"
PORT=8887

echo "======================================="
echo "ETF服务 - Linux Docker部署脚本"
echo "======================================="

# 检查脚本是否具有可执行权限
if [ ! -x "$0" ]; then
    echo "[警告] 脚本没有可执行权限，正在添加..."
    chmod +x "$0"
fi

# 检查Docker是否已安装
if ! command -v docker &> /dev/null; then
    echo "[错误] Docker未安装，请先安装Docker"
    exit 1
fi

# 检查Docker服务是否正在运行
if ! docker info &> /dev/null; then
    echo "[错误] Docker服务未运行，请先启动Docker服务"
    exit 1
fi

# 定位部署包（未通过参数指定时，自动在脚本目录下查找）
if [ -z "$TAR_FILE" ]; then
    for candidate in "$SCRIPT_DIR/etf-service-docker.tar.gz" "$SCRIPT_DIR/dist/etf-service-docker.tar.gz"; do
        if [ -f "$candidate" ]; then
            TAR_FILE="$candidate"
            break
        fi
    done
fi

if [ -z "$TAR_FILE" ] || [ ! -f "$TAR_FILE" ]; then
    echo "[错误] 未找到部署包 etf-service-docker.tar.gz"
    echo "请把 build_windows.bat 生成的部署包与本脚本放在同一目录，或用第一个参数指定部署包路径"
    exit 1
fi

echo "[1/5] 解压部署包"
echo "部署包：$TAR_FILE"
echo "解压目录：$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR"
tar -xzf "$TAR_FILE" -C "$DEPLOY_DIR"

if [ $? -ne 0 ]; then
    echo "[错误] 解压部署包失败"
    exit 1
fi

if [ ! -f "$DEPLOY_DIR/Dockerfile" ] || [ ! -d "$DEPLOY_DIR/app" ]; then
    echo "[错误] 部署包内容不完整（缺少 Dockerfile 或 app 目录）"
    exit 1
fi

echo "[2/5] 开始构建Docker镜像..."
echo "构建镜像：$IMAGE_NAME"
echo "构建上下文：$DEPLOY_DIR"
docker build -t "$IMAGE_NAME" "$DEPLOY_DIR"

if [ $? -ne 0 ]; then
    echo "[错误] Docker镜像构建失败"
    exit 1
fi

echo "[2/5] Docker镜像构建成功"

# 检查并停止旧容器
echo "[3/5] 检查并停止旧容器..."
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "停止旧容器 $CONTAINER_NAME"
    docker stop "$CONTAINER_NAME"
    echo "删除旧容器 $CONTAINER_NAME"
    docker rm "$CONTAINER_NAME"
fi

# 清理悬空的旧镜像
echo "[4/5] 清理未使用的旧镜像..."
docker image prune -f > /dev/null 2>&1

echo "[5/5] 启动新的Docker容器..."

ENV_FILE="$DEPLOY_DIR/.env"

env_file_has() {
    [ -f "$ENV_FILE" ] && grep -qE "^[[:space:]]*(export[[:space:]]+)?$1=" "$ENV_FILE"
}

# 自动确定 MongoDB 地址，避免容器内 localhost 指向容器自身
NETWORK_ARGS=()
if [ -z "$MONGO_URL" ]; then
    if env_file_has MONGO_URL; then
        echo "[信息] 使用 .env 中的 MONGO_URL"
    else
        MONGO_CONTAINER=$(docker ps --format '{{.Names}}' | grep -Ei 'mongo' | head -n1 || true)
        if [ -n "$MONGO_CONTAINER" ]; then
            MONGO_NETWORK=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' "$MONGO_CONTAINER" 2>/dev/null | awk '{print $1}')
            MONGO_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$MONGO_CONTAINER" 2>/dev/null | awk '{print $1}')
            if [ -n "$MONGO_NETWORK" ] && [ "$MONGO_NETWORK" != "bridge" ]; then
                MONGO_URL="mongodb://$MONGO_CONTAINER:27017/tradingagents"
                NETWORK_ARGS+=(--network "$MONGO_NETWORK")
                echo "[信息] 检测到 MongoDB 容器 $MONGO_CONTAINER，自动加入网络 $MONGO_NETWORK"
            elif [ -n "$MONGO_IP" ]; then
                MONGO_URL="mongodb://$MONGO_IP:27017/tradingagents"
                echo "[信息] 检测到 MongoDB 容器 $MONGO_CONTAINER（默认 bridge），使用容器 IP $MONGO_IP"
            else
                MONGO_URL="mongodb://host.docker.internal:27017/tradingagents"
                NETWORK_ARGS+=(--add-host "host.docker.internal:host-gateway")
                echo "[警告] 无法读取 MongoDB 容器地址，回退到 $MONGO_URL"
            fi
        else
            MONGO_URL="mongodb://host.docker.internal:27017/tradingagents"
            NETWORK_ARGS+=(--add-host "host.docker.internal:host-gateway")
            echo "[警告] 未设置 MONGO_URL，默认连接宿主机 MongoDB: $MONGO_URL"
            echo "        若 MongoDB 在其他机器，请先 export MONGO_URL='mongodb://<mongo-ip>:27017/tradingagents' 再重跑"
        fi
    fi
fi

# 收集需要透传给容器的环境变量
ENV_ARGS=()
if [ -n "$MONGO_URL" ]; then
    ENV_ARGS+=(-e "MONGO_URL=$MONGO_URL")
fi
if [ -n "$MONGO_DB_NAME" ]; then
    ENV_ARGS+=(-e "MONGO_DB_NAME=$MONGO_DB_NAME")
fi
if [ -n "$TUSHARE_TOKEN" ]; then
    ENV_ARGS+=(-e "TUSHARE_TOKEN=$TUSHARE_TOKEN")
fi
if env_file_has TZ; then
    :
else
    ENV_ARGS+=(-e "TZ=${TZ:-Asia/Shanghai}")
fi
if [ -f "$ENV_FILE" ]; then
    ENV_ARGS+=(--env-file "$ENV_FILE")
fi

docker run -d \
    --name "$CONTAINER_NAME" \
    --restart=always \
    -p "$PORT:8887" \
    -e PORT=8887 \
    -e HOST=0.0.0.0 \
    "${NETWORK_ARGS[@]}" \
    "${ENV_ARGS[@]}" \
    "$IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo "[错误] Docker容器启动失败"
    exit 1
fi

echo "[验证] 等待服务启动并检查数据库连通性..."
sleep 6
if command -v curl > /dev/null 2>&1; then
    HEALTH=$(curl -fsS "http://localhost:$PORT/health" 2>/dev/null || true)
    if [ -n "$HEALTH" ]; then
        echo "健康检查：$HEALTH"
    else
        echo "[警告] /health 未返回，请执行 docker logs $CONTAINER_NAME 查看 MongoDB 连接日志"
    fi
fi

echo "======================================="
echo "部署结果："
echo "======================================="
echo "服务名称：$PROJECT_NAME"
echo "镜像名称：$IMAGE_NAME"
echo "容器名称：$CONTAINER_NAME"
echo "服务端口：$PORT"
echo "访问地址：http://localhost:$PORT"
echo "API文档：http://localhost:$PORT/docs"
echo "健康检查：http://localhost:$PORT/health"
echo "======================================="
echo ""
echo "部署成功！"
echo ""
echo "查看容器状态：docker ps | grep $CONTAINER_NAME"
echo "查看容器日志：docker logs $CONTAINER_NAME"
echo "跟踪容器日志：docker logs -f $CONTAINER_NAME"
echo "进入容器：docker exec -it $CONTAINER_NAME bash"
echo "停止服务：docker stop $CONTAINER_NAME"
echo "启动服务：docker start $CONTAINER_NAME"
echo "重启服务：docker restart $CONTAINER_NAME"
echo "删除服务：docker rm -f $CONTAINER_NAME"
echo "删除镜像：docker rmi -f $IMAGE_NAME"
echo ""
echo "======================================="
echo "提示：您可以通过以下命令重新部署服务："
echo "./deploy_linux.sh"
echo "======================================="
