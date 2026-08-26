@echo off
setlocal enabledelayedexpansion

echo Starting deployment process...

REM 删除dist目录下的所有文件，如果目录不存在则跳过
if exist dist\* (
    echo Deleting files in dist directory...
    del /q dist\* 2>nul
    echo Deleted files in dist directory.
) else (
    echo dist directory is empty or does not exist, skipping deletion.
)

REM 执行npm构建命令，即使失败也继续执行后续命令
echo Running npm build...
call npm run build || echo npm build failed, continuing...
echo npm build completed.

REM 删除旧的压缩包，如果不存在则跳过
if exist dist.tar.gz (
    echo Deleting old dist.tar.gz...
    del dist.tar.gz 2>nul
    echo Deleted old dist.tar.gz.
) else (
    echo No old dist.tar.gz found, skipping deletion.
)

REM 创建新的压缩包
echo Creating new dist.tar.gz...
if exist dist (
    echo dist directory exists, creating archive...
    echo Running tar command...
    tar -czvf dist.tar.gz -C dist .
    echo Archive created successfully.
) else (
    echo Error: dist directory not found!
)

echo Deployment process completed!

dir dist.tar.gz 2>nul || echo Warning: dist.tar.gz was not created!

exit /b 0