# Katayzy

> 追求性能与便捷的通用围棋分析软件

<p align="center">
  <img src="使用效果图.png" alt="Katayzy 使用示例" width="100%" />
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Windows-blue" />
  <img alt="Engine" src="https://img.shields.io/badge/Engine-KataGo-00A3A3" />
  <img alt="Feature" src="https://img.shields.io/badge/Feature-AI%20Analysis-7C3AED" />
</p>

Katayzy 是一款面向围棋爱好者、教师以及希望快速体验 AI 分析的用户打造的通用围棋分析软件。它基于 LizzieYzy Next 重构，内置 KataGo 最新引擎，致力于将"高性能分析、简洁使用体验、低门槛部署"融为一体，让更多人轻松享受智能围棋分析带来的乐趣。

> 注：本项目主要提供便捷、多元的本地分析体验。若你需要使用云算力方案，建议移步上游仓库 [LizzieYzy Next](https://github.com/wimi321/lizzieyzy-next) 的相关整合包，其中提供了智子云算力等算力方案。

## ✨ 核心特性

- **高性能分析**：内置 b10c384、b10c512、b10c768 三个引擎，结合 KataGo 的生成配置流程，自动为当前电脑生成更适配的分析配置。
- **兼容多场景**：同时兼顾通用使用场景与高性能版本需求，降低不同硬件环境下的使用门槛。
- **启动器自动管理**：引擎构建、模型准备、依赖管理与 GUI 使用流程相对分离，启动器脚本可自动完成多数初始化工作。
- **显卡环境检测**：自动识别当前显卡能力，帮助用户更清楚地选择适合自己的版本。
- **便捷的棋谱分析体验**：内置野狐、腾讯棋谱接口，可快速下载棋谱并进行本地分析，自动输出胜率曲线与问题手。
- **持续升级棋力**：正式版适配发布后，将进一步支持自动更新权重模型，让用户持续获得更强的分析能力。

## 🎯 适合谁使用

- **围棋爱好者**：为自己的棋局提供更深入的 AI 分析
- **围棋教师**：适合课堂演示、教学讲解与对局复盘
- **初次接触 AI 围棋的用户**：无需过多折腾配置，也能快速上手

## 🚀 版本选择

| 版本 | 适用场景 | 说明 |
| --- | --- | --- |
| 通用版 | 更广泛的硬件兼容 | 更适合大多数用户快速上手 |
| Nvtrt 版 | NVIDIA 显卡 | 针对 CUDA 12.8 与 TensorRT 10.9.0 适配，性能更强 |

> 若使用 Nvtrt 版，请遵守英伟达相关协议与使用规范。

## 🧠 引擎说明

- **b10c384**：轻量级模型，适合作为伴生进程，负责后台自动分析
- **b10c512**：常用中等规模模型，可作为默认分析引擎
- **b10c768**：更大规模模型，分析能力更强

通过 KataGo 的 genconfig 流程，软件可为当前电脑生成更适配的配置，提升分析效率与体验。

## ▶️ 快速开始

1. 下载并解压发布包
2. 运行启动器脚本
3. 根据提示完成首次引擎构建与模型准备
4. 打开棋谱即可开始分析

> 建议首次使用时完整完成引擎构建流程，以获得更稳定的体验。

## 📦 发布包结构

- **启动器脚本**：启动器.bat、check_env.ps1、check_first_run.ps1
- **引擎与模型管理**：build_engines.ps1、add_engine.ps1、update_weights.ps1
- **程序主体**：app、runtime、clockHelper
- **数据目录**：save、user-data、engines
- **说明文件**：使用说明.txt

## 🔗 项目说明

本项目由 LizzieYzy Next 重构而来，内置 KataGo 最新引擎，并感谢开源社区的共同贡献与支持。

- LizzieYzy Next: https://github.com/wimi321/lizzieyzy-next
- KataGo: https://github.com/lightvector/KataGo
- 项目仓库: https://github.com/Coderchen-001/Katayzy

## 📄 相关文档

- [CHANGELOG.md](CHANGELOG.md)：版本变更历史
- [ROADMAP.md](ROADMAP.md)：项目路线图
- [PACKAGING.md](PACKAGING.md)：Windows 构建与打包指南
- [LICENSE.txt](LICENSE.txt)：GPL-3.0 协议

## 💬 反馈与讨论

如果你在使用过程中遇到问题，欢迎在仓库下提交讨论或反馈。我们希望通过社区的共同参与，让这款软件越来越好用、越来越稳定。

祝你下棋愉快。
