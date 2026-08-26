@echo off
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
echo [1/5] 已创建dist目录

REM 2. 安装依赖
if exist "%VENV_DIR%" (
    echo [2/5] 虚拟环境已存在，使用现有虚拟环境
    call "%VENV_DIR%\Scripts\activate"
) else (
    echo [2/5] 创建新的虚拟环境
    python -m venv "%VENV_DIR%"
    call "%VENV_DIR%\Scripts\activate"
    pip install --upgrade pip
)

echo [2/5] 安装项目依赖
pip install -r "%PROJECT_DIR%\requirements.txt"

REM 3. 复制项目文件到dist目录
echo [3/5] 复制项目文件
xcopy /E /Y "%PROJECT_DIR%\app" "%DIST_DIR%\app\" >nul
copy /Y "%PROJECT_DIR%\requirements.txt" "%DIST_DIR%\" >nul
copy /Y "%PROJECT_DIR%\Dockerfile" "%DIST_DIR%\" >nul

REM 4. 创建启动脚本
echo [4/5] 创建启动脚本
(
echo @echo off

echo REM ETF服务启动脚本
set PYTHON_PATH=python
echo %PYTHON_PATH% -m uvicorn app.main:app --host 0.0.0.0 --port 8887
) > "%DIST_DIR%\start.bat"

REM 5. 完成打包
echo [5/5] 打包完成

echo ======================================
echo 打包结果：
echo 输出目录：%DIST_DIR%
echo 启动命令：%DIST_DIR%\start.bat
echo ======================================

REM 退出虚拟环境
if defined VIRTUAL_ENV (
    deactivate
)

echo 打包脚本执行完成！
pause