@echo off
chcp 936 >nul
title Katayzy 启动器

cd /d "%~dp0"

:main
cls
echo ================================================
echo    Katayzy 启动器
echo    (CUDA 12.8 + TensorRT 10.9)
echo ================================================
echo.
echo    [1] 启动 Katayzy（自动检测环境与首次构建）
echo    [2] 检测环境
echo    [3] 构建/重建引擎配置
echo    [4] 更新权重模型（官网布置中）
echo    [0] 退出
echo.
set "choice="
set /p choice=请选择:
if "%choice%"=="" exit /b 0
if "%choice%"=="1" goto start
if "%choice%"=="2" goto env
if "%choice%"=="3" goto build
if "%choice%"=="4" goto weights
if "%choice%"=="0" exit /b 0
goto main

:env
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check_env.ps1"
echo.
pause
goto main

:start
echo.
echo ================================================
echo    正在检查显卡环境...
echo ================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check_env.ps1"
if errorlevel 1 (
    echo.
    echo 环境检查未通过！请按上方提示处理显卡或驱动后重试。
    echo.
    pause
    goto main
)
echo.
echo 环境检查通过！
echo.

rem ---- 首次运行检测 ----
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check_first_run.ps1"
if errorlevel 1 goto launch

echo ================================================
echo   检测到首次运行：尚未配置任何引擎。
echo.
echo   是否自动构建全部 3 个模型？
echo     - b10c384  （伴生进程基础模型，打开棋谱自动分析）
echo     - b10c512  （默认引擎 / 棋力评估）
echo     - b11c768  （大模型，棋力最强）
echo.
echo   首次构建需生成 TensorRT 引擎缓存，每个模型约 3~8 分钟，
echo   全部约 10~25 分钟。期间输出实时滚动，请勿关闭窗口。
echo ================================================
echo.
set /p confirm=确认构建? (Y/N):
if /i "%confirm%"=="y" (
    echo.
    echo 开始构建引擎配置，请耐心等待...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_engines.ps1"
    if errorlevel 1 (
        echo.
        echo 构建未完成！请检查驱动/显存后，稍后选择菜单 [3] 重试。
        echo.
        pause
        goto main
    )
    echo.
    echo 引擎配置构建完成。
) else (
    echo.
    echo 已跳过构建。注意：不构建则打开棋谱的自动分析不可用。
)
echo.

:launch
if exist "%~dp0Katayzy.exe" (
    echo 正在启动 Katayzy...
    start "" "%~dp0Katayzy.exe"
    echo 已启动。可关闭本窗口。
    echo.
    pause
) else (
    echo 未找到 Katayzy.exe（当前目录：%~dp0）
    echo 请先将打包好的 Katayzy.exe 放到本目录。
    echo.
    pause
)
goto main

:build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_engines.ps1"
echo.
pause
goto main

:weights
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0update_weights.ps1"
echo.
pause
goto main
