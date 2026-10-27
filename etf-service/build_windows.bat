@echo off

for /f "tokens=2 delims=:." %%a in ('chcp') do set "OLD_CODEPAGE=%%a"
chcp 65001 >nul

rem 切换到脚本所在目录，保证 app、Dockerfile 等相对路径始终正确
cd /d "%~dp0"

rem 本脚本以 UTF-8 编码保存，已切换到 65001 代码页，避免中文乱码及变量名被截断
setlocal enabledelayedexpansion

echo ======================================
echo ETF服务 - Windows打包脚本
echo ======================================

REM 设置变量
set PROJECT_DIR=%cd%
set DIST_DIR=%PROJECT_DIR%\dist
set VENV_DIR=%PROJECT_DIR%\venv

REM 1. 创建dist目录
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
echo [1/6] 已创建dist目录

REM 2. 安装依赖
if exist "%VENV_DIR%" (
    echo [2/6] 虚拟环境已存在，使用现有虚拟环境
    call "%VENV_DIR%\Scripts\activate"
) else (
    echo [2/6] 创建新的虚拟环境
    python -m venv "%VENV_DIR%"
    call "%VENV_DIR%\Scripts\activate"
    pip install --upgrade pip
)

echo [3/6] 安装项目依赖
pip install -r "%PROJECT_DIR%\requirements.txt"

REM 3. 复制项目文件到dist目录
echo [4/6] 复制项目文件
xcopy /E /Y "%PROJECT_DIR%\app" "%DIST_DIR%\app\" >nul
copy /Y "%PROJECT_DIR%\requirements.txt" "%DIST_DIR%\" >nul
copy /Y "%PROJECT_DIR%\Dockerfile" "%DIST_DIR%\" >nul

REM 4. 创建启动脚本
echo [5/6] 创建启动脚本
REM 变量需在括号块外赋值，否则块内 %PYTHON_PATH% 会在解析时展开为空
set "PYTHON_PATH=python"
(
echo @echo off
echo chcp 65001 ^>nul
echo REM ETF服务启动脚本
echo %PYTHON_PATH% -m uvicorn app.main:app --host 0.0.0.0 --port 8887
) > "%DIST_DIR%\start.bat"

REM 6. 打包 Linux Docker 部署包（只包含构建镜像所需的文件）
echo [6/6] 打包 Docker 部署包
set "TAR_FILE=%DIST_DIR%\etf-service-docker.tar.gz"
tar --exclude=__pycache__ --exclude=*.pyc -czf "%TAR_FILE%" Dockerfile requirements.txt app
if errorlevel 1 (
    echo [错误] 打包 Docker 部署包失败，请确认系统已安装 tar 命令（Windows 10 1803+ 自带）
    set "TAR_FILE="
)

echo ======================================
echo 打包结果：
echo 输出目录：%DIST_DIR%
echo 启动命令：%DIST_DIR%\start.bat
if defined TAR_FILE (
    echo Docker部署包：%TAR_FILE%
    echo 部署脚本：%PROJECT_DIR%\deploy_linux.sh
    echo 请将以上两个文件上传到 Linux 服务器后执行：
    echo   chmod +x deploy_linux.sh
    echo   ./deploy_linux.sh
) else (
    echo Docker部署包：打包失败
)
echo ======================================

REM 退出虚拟环境
if defined VIRTUAL_ENV (
    deactivate
)

echo 打包脚本执行完成！

rem 恢复原始代码页
if defined OLD_CODEPAGE chcp %OLD_CODEPAGE% >nul
pause