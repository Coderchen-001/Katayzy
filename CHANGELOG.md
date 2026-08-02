# Changelog

Katayzy 版本变更历史。项目从 LizzieYzy Next 重构而来，当前发布版本基于 tag `next-2026-08-01.4`（exe 版本 `2.6.20901`）。

## 2026-08-02 - Katayzy 重构（Unreleased）

自 rebrand 起的主要变更（`5ac7955c` → `HEAD`）：

### 品牌与结构

- 全面更名为 Katayzy，移除一键设置 / 更新器 / 关于等上游遗留入口
- 伴生进程新增 b10c384 回退机制，缺少首选引擎时自动降级可用

### 启动器与首次运行

- 新增启动器环境门禁：运行前自动检查环境、首次运行引导构建引擎
- 新增引擎配置写入：首次构建时自动生成 `user-data\config.txt` 的分析引擎命令
- 启动器 GPU 信息持久化，显卡检测结果可复用
- `build_engines` 调参收敛，首次构建流程更稳定
- 新增 Windows 一键打包脚本 `scripts\pack_windows.ps1`（编译 → jpackage → 组装到整合包）
- 新增 Windows 打包指南（jpackage + 部署到整合包）

### 分析与对局体验

- 移除 "AI 未就绪" 按钮，启动分析更顺畅
- 新增 16 visits 快速分析模式
- 伴生引擎负载转移：后台分析与主分析窗口共存，互不阻塞
- 禁用 Auto Setup 自动写入配置，避免意外覆盖用户设置
- 固定基础设置（60s 思考 / 1500 visits），移除启动性能基准测试
- 强制空盘停止：棋局为空时自动停止引擎分析
- 伴生 b10c384 使用调优后的 `analysis.cfg`，预加载更稳健

## 更早历史

更早的版本历史（LizzieYzy Next 上游维护记录）已随项目重构移除，见上游仓库 `wimi321/lizzieyzy-next`。
