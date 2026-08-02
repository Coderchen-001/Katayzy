# Katayzy Windows 打包指南

Katayzy（CUDA 12.8 + TensorRT 10.9 版）的构建与打包说明。
项目要求 Java 17（`pom.xml` source/target 17），exe 由 JDK 自带 `jpackage` 生成。

## 0. 一键打包（推荐）

```powershell
# 默认：编译 → jpackage → 组装到 成品\Katayzy-next-<今天日期>.N\（序号自动递增）
powershell -ExecutionPolicy Bypass -File scripts\pack_windows.ps1

# 手动指定版本
powershell -ExecutionPolicy Bypass -File scripts\pack_windows.ps1 -Version next-2026-08-02.1

# 只重新组装（复用已编译 jar）
powershell -ExecutionPolicy Bypass -File scripts\pack_windows.ps1 -SkipCompile -Version next-2026-08-02.1
```

参数：
- `-Version`：版本串（默认 `next-<今天日期>.N`，目录已存在则自动递增 N）
- `-BaseBundle`：引擎/脚本/附加资源的来源整合包（默认 `成品\Katayzy`）
- `-OutputRoot`：版本化整合包的输出父目录（默认 `D:\全新重构项目-卡塔狗桌面版\成品`）
- `-SkipCompile`：复用 `target\lizzie-yzy2.5.3-shaded.jar`，跳过 mvn package
- `-AppVersion`：exe 文件版本（默认 `2.6.20901`）

脚本内部流程 = 下文 1~4 节的手动步骤。

## 1. 构建 jar

- portable JDK 17：`.tools\jdk-17`
- portable Maven 3.9.16：`.tools\apache-maven-3.9.16`
- 构建入口：`.tools\build.cmd`（自动设置 JAVA_HOME，跳过 fmt 检查，使用阿里云镜像）

## 1. 构建 jar

```bat
cd 项目根
.tools\build.cmd -DskipTests package
```

产出：`target\lizzie-yzy2.5.3-shaded.jar`（shade 后的可运行 jar，主类 `featurecat.lizzie.Lizzie`）。

> 完整测试：`.tools\build.cmd test`（约 200+ 单测，需要联网拉取依赖）。

## 2. 生成 Katayzy.exe（jpackage）

```bat
.tools\jdk-17\bin\jpackage.exe ^
  --type app-image ^
  --name Katayzy ^
  --app-version 2.6.20901 ^
  --input target ^
  --main-jar lizzie-yzy2.5.3-shaded.jar ^
  --main-class featurecat.lizzie.Lizzie ^
  --icon packaging\icons\app-icon.ico ^
  --dest dist\windows
```

产出结构（jpackage app-image 默认布局）：

```
dist\windows\Katayzy\
├── Katayzy.exe        ← 主启动器
├── app\
│   ├── Katayzy.cfg
│   └── lizzie-yzy2.5.3-shaded.jar
└── runtime\           ← 内置 JRE（bin/conf/lib）
```

## 3. 附加资源（JCEF / readboard / clockHelper）

GUI 的嵌入式浏览器（JCEF）与棋盘同步（readboard）不随 jpackage 打包，
需要把附加资源放进 `app\`：

- `app\jcef-bundle\`：参考 `scripts\prepare_bundled_jcef.py` 准备，或直接从
  参考成品 `app\jcef-bundle\` 复制
- `app\readboard\`：参考 `scripts\package_runtime_tools.py`，或从参考成品复制
- `clockHelper\`：`clockHelper\invisibleFrame.jar`（从参考成品复制）

## 4. 部署到整合包

把打包产物铺到整合包根目录（与 `启动器.bat` 平级）：

```
成品\Katayzy\
├── 启动器.bat / check_env.ps1 / build_engines.ps1 / ...（已就绪）
├── Katayzy.exe                    ← 步骤 2 产物（复制到根）
├── app\                           ← 步骤 2 产物 + 步骤 3 附加资源
├── runtime\                       ← 步骤 2 产物（覆盖旧 runtime）
├── engines\katago-trt\            ← 已就绪（katago.exe + dll + 3 权重）
├── clockHelper\                   ← 已就绪
├── user-data\config.txt           ← 已就绪（初始空引擎列表）
└── .lizzie-portable               ← 已就绪
```

部署完成后，双击 `启动器.bat` → `[1] 启动 Katayzy`：
环境门禁 → 首次运行引导构建（b10c384/b10c512/b11c768）→ 启动。


## 5. 注意事项

- 本整合包引擎目录为 `engines\katago-trt\`（TRT 变体），脚本全部使用相对路径，U 盘便携。
- `user-data\config.txt` 的 `ui.analysis-engine-command`（b10c384 伴生进程）与
  `ui.estimate-command`（b10c512，Kata评估）由 `build_engines.ps1` 首次构建时写入。
- `app\PROJECT_INFO.txt` 等随打包重新生成；整合包内旧 jar 仅为占位，务必用步骤 1 的新 jar 覆盖。
