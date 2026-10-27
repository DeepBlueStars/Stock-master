#!/bin/bash
# 自动部署 StockAI 服务 - 无需交互
# author: hpp@vico-lab.com

set -e  # 遇到错误时退出脚本

# 将 .env 中的 VITE_* 变量转换为前端可读取的运行时配置 env.js
generate_runtime_env() {
    local env_file="$1"
    local target_file="$2"
    local json=""
    local line key value

    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        line="$(printf '%s' "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

        if [ -z "$line" ] || [ "${line:0:1}" = "#" ]; then
            continue
        fi
        if [ "${line#export }" != "$line" ]; then
            line="${line#export }"
            line="$(printf '%s' "$line" | sed -e 's/^[[:space:]]*//')"
        fi

        key="${line%%=*}"
        if [ "$key" = "$line" ]; then
            continue
        fi
        value="${line#*=}"
        value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
        key="$(printf '%s' "$key" | tr -d '[:space:]')"

        case "$key" in
            VITE_*) ;;
            *) continue ;;
        esac

        case "$value" in
            \"*\") value="${value#\"}"; value="${value%\"}" ;;
            \'*\') value="${value#\'}"; value="${value%\'}" ;;
        esac

        value="$(printf '%s' "$value" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"

        if [ -n "$json" ]; then
            json="$json,"
        fi
        json="$json\"$key\":\"$value\""
    done < "$env_file"

    printf 'window.__STOCKAI_ENV__ = {%s};\n' "$json" > "$target_file"
}

echo "========================================"
echo "开始自动部署 StockAI 服务"
echo "========================================"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ 错误：未安装 Docker"
    exit 1
fi

echo "✅ Docker 已安装: $(docker --version)"

# 清理现有容器和镜像
echo "正在清理现有服务..."
docker compose -f dc-stockai.yml down 2>/dev/null || true

{
    docker container stop stockai-web stockai-service 2>/dev/null || true
    docker container rm stockai-web stockai-service 2>/dev/null || true
    docker image rm stockai-service stockai-web 2>/dev/null || true
} >> /dev/null 2>&1

echo "✅ 清理完成"

# 创建必要的目录
echo "创建目录结构..."
mkdir -p /home/xian/stockai/nginx/cnf
mkdir -p /home/xian/stockai/www/web
mkdir -p /home/xian/stockai/media
mkdir -p /home/xian/stockai/log

echo "✅ 目录创建完成"

# 复制 Nginx 配置
if [ -f ./stockai.conf ]; then
    cp ./stockai.conf /home/xian/stockai/nginx/cnf/
    echo "✅ Nginx 配置文件已复制"
else
    echo "⚠️  警告：未找到 stockai.conf 文件"
fi

# 解压前端文件（如果存在）
if [ -f ./dist.tar.gz ]; then
    echo "正在解压前端文件..."
    tar -xzf ./dist.tar.gz -C /home/xian/stockai/www/web/ 2>/dev/null && echo "✅ 前端文件解压完成" || echo "⚠️  前端文件解压失败或文件已存在"
else
    echo "⚠️  提示：未找到 dist.tar.gz 文件，跳过前端部署"
fi

# 生成前端运行时配置：优先使用服务器外部 .env（与 dist.tar.gz 同级），
# 没有外部 .env 时才使用 tar 包内的 .env；两者都没有则回退到构建时配置
WEB_DIR=/home/xian/stockai/www/web
EXTERNAL_ENV_FILE=./.env
TAR_ENV_FILE="$WEB_DIR/.env"
RUNTIME_ENV_FILE="$WEB_DIR/env.js"

if [ -f "$EXTERNAL_ENV_FILE" ]; then
    generate_runtime_env "$EXTERNAL_ENV_FILE" "$RUNTIME_ENV_FILE"
    echo "✅ 已使用外部 .env 生成前端运行时配置"
elif [ -f "$TAR_ENV_FILE" ]; then
    generate_runtime_env "$TAR_ENV_FILE" "$RUNTIME_ENV_FILE"
    echo "✅ 已使用 tar 包内 .env 生成前端运行时配置"
else
    printf 'window.__STOCKAI_ENV__ = {};\n' > "$RUNTIME_ENV_FILE"
    echo "⚠️  未找到外部或 tar 包内 .env，前端将使用构建时配置"
fi

# .env 不应作为静态资源对外暴露，生成 env.js 后删除
if [ -f "$TAR_ENV_FILE" ]; then
    rm -f "$TAR_ENV_FILE"
fi

# 检查后端文件
if [ ! -f ./stockai-service.jar ]; then
    echo "❌ 错误：未找到 stockai-service.jar 文件"
    exit 1
fi

# 启动服务
echo "正在启动服务..."
docker compose -f dc-stockai.yml up -d

# 等待服务启动
echo "等待服务启动..."
sleep 5

# 检查服务状态
echo "检查服务状态..."
if docker ps | grep -q "stockai-web" && docker ps | grep -q "stockai-service"; then
    echo "========================================"
    echo "✅ StockAI 部署成功！"
    echo ""
    echo "访问地址："
    echo "前端页面：http://192.168.31.88:8889/"
    echo "后端API：http://192.168.31.88:8889/srv/"
    echo "媒体文件：http://192.168.31.88:8889/media/"
    echo ""
    echo "容器状态："
    docker ps | grep stockai
    echo "========================================"
else
    echo "❌ 服务启动失败，请检查日志："
    docker compose -f dc-stockai.yml logs
    exit 1
fi