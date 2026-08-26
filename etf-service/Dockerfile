# 使用Python 3.9官方镜像作为基础镜像，基于当前维护的Debian版本
FROM python:3.9-slim-bookworm

# 设置工作目录
WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# 复制requirements.txt到工作目录
COPY requirements.txt .

# 安装Python依赖
RUN pip install --no-cache-dir -r requirements.txt

# 复制项目文件到工作目录
COPY app/ ./app/

# 暴露端口
EXPOSE 8887

# 设置环境变量
ENV PYTHONUNBUFFERED=1
ENV PORT=8887
ENV HOST=0.0.0.0

# 启动命令
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8887"]