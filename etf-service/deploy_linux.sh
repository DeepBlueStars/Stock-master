#!/bin/bash

# ETF服务 - Linux Docker部署脚本

echo "======================================="
echo "ETF服务 - Linux Docker部署脚本"
echo "======================================="

# 检查脚本是否具有可执行权限
if [ ! -x "$0" ]; then
    echo "[警告] 脚本没有可执行权限，正在添加..."
    chmod +x "$0"
fi

# 检查当前目录是否包含Dockerfile
if [ ! -f "Dockerfile" ]; then
    echo "[错误] 当前目录不存在Dockerfile，请在项目根目录执行此脚本"
    exit 1
fi

# 检查当前目录是否包含app目录
if [ ! -d "app" ]; then
    echo "[错误] 当前目录不存在app目录，请在项目根目录执行此脚本"
    exit 1
fi

# 配置变量
PROJECT_NAME="etf-service"
IMAGE_NAME="etf-service:latest"
CONTAINER_NAME="etf-service-container"
PORT=8887

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

echo "[1/5] 开始构建Docker镜像..."
echo "当前工作目录: $(pwd)"
echo "构建镜像: $IMAGE_NAME"
# 构建Docker镜像
docker build -t $IMAGE_NAME .

if [ $? -ne 0 ]; then
    echo "[错误] Docker镜像构建失败"
    exit 1
fi

echo "[2/5] Docker镜像构建成功"

# 检查并停止旧容器
echo "[3/5] 检查并停止旧容器..."
if docker ps -a | grep -q $CONTAINER_NAME; then
    echo "停止旧容器 $CONTAINER_NAME"
    docker stop $CONTAINER_NAME
    echo "删除旧容器 $CONTAINER_NAME"
    docker rm $CONTAINER_NAME
fi

# 检查是否有未使用的旧镜像
echo "[4/5] 清理未使用的旧镜像..."
docker image prune -f --filter "reference=$PROJECT_NAME:*" --filter "before=$IMAGE_NAME"

echo "[5/5] 启动新的Docker容器..."
# 运行新容器
docker run -d \
    --name $CONTAINER_NAME \
    --restart=always \
    -p $PORT:8887 \
    -e PORT=8887 \
    -e HOST=0.0.0.0 \
    $IMAGE_NAME

if [ $? -ne 0 ]; then
    echo "[错误] Docker容器启动失败"
    exit 1
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
echo "./$0"
echo "======================================="