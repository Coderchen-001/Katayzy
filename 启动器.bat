@echo off
chcp 936 >nul
title Katayzy 启动器

cd /d "%~dp0"

rem ---- 环境门禁：打开即检测 ----
echo ================================================
echo    Katayzy 启动器 (CUDA 12.8 + TensorRT 10.9)
echo ================================================
echo.
echo 正在检测显卡环境...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check_env.ps1"
if errorlevel 1 (
    echo.
    echo 环境检查未通过！请按上方提示处理显卡或驱动后重试。
    echo.
    pause
    exit /b 1
)
echo.
echo 环境检查通过！
rem ---- 捕获显卡信息（常驻显示，后续不再检测） ----
for /f "usebackq tokens=1-3 delims=," %%a in (`nvidia-smi --query-gpu="name,compute_cap,driver_version" --format="csv,noheader,nounits"`) do (
    set GPU_MODEL=%%a
    set GPU_CC=%%b
    set GPU_DRV=%%c
)
set GPU_INFO=%GPU_MODEL% ｜ CC %GPU_CC% ｜ 驱动 %GPU_DRV%

:main
cls
echo ================================================
echo    Katayzy 启动器
echo    (CUDA 12.8 + TensorRT 10.9)
echo ================================================
echo    %GPU_INFO%
echo ================================================
echo.
echo    [1] 启动 Katayzy
echo    [2] 构建/重建引擎配置
echo    [3] 更新权重模型（官网布置中）
echo    [0] 退出
echo.
set "choice="
set /p choice=请选择:
if "%choice%"=="" exit /b 0
if "%choice%"=="1" goto start
if "%choice%"=="2" goto build
if "%choice%"=="3" goto weights
if "%choice%"=="0" exit /b 0
goto main

:start
echo.
echo ================================================
echo    正在准备启动...
echo ================================================
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
echo   b10c384 使用 analysis 模板（秒级）；b10c512/b11c768 首次构建
echo   TensorRT 缓存各约 3~8 分钟，全部约 6~16 分钟。输出实时滚动。
echo   b10c384 的缓存将在首次自动分析时构建（约 1~3 分钟）。
echo ================================================
echo.
set /p confirm=确认构建? (Y/N):
if /i "%confirm%"=="y" (
    echo.
    echo 开始构建引擎配置，请耐心等待...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_engines.ps1"
    if errorlevel 1 (
        echo.
        echo 构建未完成！请检查驱动/显存后，稍后选择菜单 [2] 重试。
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
rem ---- TRT 缓存预检提示 ----
if not exist "%~dp0engines\katago-trt\KataGoData\trtcache\*" (
    echo.
    echo 提示：首次打开棋谱自动分析时，b10c384 需构建 TensorRT 缓存
    echo       （约 1~3 分钟，属正常现象，之后秒加载）。
    echo.
)
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
