# Upstream Cross-check

Reference: `D:\temp\lizzieyzy` at `741c301` (2023-08-18).

This is a two-way key comparison of menu-bearing files only. Dialog copy and tooltips are
included in the raw list but are not automatically feature entries.

## Summary

- both: 394
- ref-only: 25
- current-only: 28

## Ref-only (upstream has, current key set does not)

| Key | Label | Reference source |
|---|---|---|
| `BottomToolbar.flashAnalyze` | 闪电分析 | Menu.java:4627 |
| `BottomToolbar.syncBoardJava` | 棋盘同步工具(简易版) | BottomToolbar.java:752 |
| `Menu.about` | 关于 | Menu.java:4992 |
| `Menu.changeEngineGameNumbers` | 修改多盘引擎对局数 | Menu.java:3011 |
| `Menu.changeEngineGameNumbersMessage` | 多盘对局数 | Menu.java:3022 |
| `Menu.changeEngineGameNumbersTitle` | 引擎对局 | Menu.java:3023 |
| `Menu.checkUpdate` | 检查更新 | Menu.java:4942 |
| `Menu.chkWRN.toolTopText` | 分析广度拓展,仅分析时有效,点击"参数"了解更多信息 | Menu.java:5938 |
| `Menu.clearSelectToolTipText` | 清除强制区域(Alt+点击) | Menu.java:7303 |
| `Menu.flashAnalyzeAllGame` | 闪电分析(全局)(Ctrl+B) | Menu.java:3237 |
| `Menu.intervention` | 干预引擎对局 | Menu.java:3042 |
| `Menu.introduction` | 简介 | Menu.java:4963 |
| `Menu.introductionJpn` | LizzieYzyの使い方 | Menu.java:4974 |
| `Menu.moveNumberInBranchTips` | 只影响新落子的分支 | Menu.java:719 |
| `Menu.playAlternatelyToolTipText` | 交替落子 | Menu.java:6850 |
| `Menu.playBlackToolTipText` | 添加黑子(右键添加白子) | Menu.java:6797 |
| `Menu.playPassToolTipText` | 停一手 | Menu.java:6858 |
| `Menu.playWhiteToolTipText` | 添加白子(右键添加黑子) | Menu.java:6821 |
| `Menu.rankMenu.base` | 依据: | Menu.java:8783 |
| `Menu.rankMenu.base.tooltips` | 判断阈值与"超级鹰眼-统计信息"一致 | Menu.java:8788 |
| `Menu.rankMenu.score` | 目数 | Menu.java:8786 |
| `Menu.rankMenu.win` | 胜率 | Menu.java:8784 |
| `Menu.readBoardJava` | 棋盘同步工具(简易版) | Menu.java:4001 |
| `Menu.selectAllowToolTipText` | 设置强制计算区域(Alt+鼠标圈选) | Menu.java:6900 |
| `Menu.selectAvoidToolTipText` | 设置强制不计算区域 | Menu.java:6933 |

## Current-only (current fork has, upstream key set does not)

Mark these `origin: katayzy-new` during inventory review.

| Key | Label | Current source |
|---|---|---|
| `BottomToolbar.autoAnalyzeTip` | 自动分析、整盘精析与批量分析 | BottomToolbar.java:639 |
| `BottomToolbar.moreActions` | 更多 | BottomToolbar.java:650 |
| `Menu.appearanceTheme` | 外观主题 | Menu.java:525 |
| `Menu.appleStyleUi` | Apple 风格 UI | Menu.java:689 |
| `Menu.boardStyle` | 棋盘样式 | Menu.java:527 |
| `Menu.boardStyleChineseClassic` | 中式古棋盘（角标/中文坐标） | Menu.java:531 |
| `Menu.boardStyleJapanese` | 日式九星 | Menu.java:529 |
| `Menu.classicColorTheme` | 经典配色（原版 lizzieyzy） | Menu.java:691 |
| `Menu.clearCustomBoardBackground` | 恢复默认棋盘背景 | Menu.java:742 |
| `Menu.foxKifuButton` | 野狐棋谱 | Menu.java:7646 |
| `Menu.layoutMode` | 布局模式 | Menu.java:1775 |
| `Menu.playerStrengthEstimate` | 棋力水平评估 | Menu.java:3278 |
| `Menu.playerStrengthEstimateButton` | 棋力评估 | Menu.java:7678 |
| `Menu.rankMenu.evaluationMode` | 判定依据 | Menu.java:8573 |
| `Menu.restartRequiredForIcons` | 部分图标颜色需重启应用后生效 | Menu.java:706 |
| `Menu.setCustomBoardBackground` | 设置自定义棋盘背景... | Menu.java:736 |
| `Menu.syncDiagnostics` | 同步诊断 | BottomToolbar.java:1071 |
| `Menu.tencentKifu` | 腾讯棋谱 | Menu.java:4021 |
| `Menu.tencentKifuButton` | 腾讯棋谱 | Menu.java:7662 |
| `Menu.webBoard` | Web 旁观 | Menu.java:4046 |
| `Menu.webBoardCopyUrl` | 复制访问地址 | Menu.java:4081 |
| `Menu.webBoardForceExitTrial` | 强制结束试下 | BottomToolbar.java:1113 |
| `Menu.webBoardStart` | 启动 Web 旁观 | Menu.java:4050 |
| `Menu.webBoardStop` | 停止 Web 旁观 | Menu.java:4068 |
| `Menu.yikeLiveWeb` | 打开弈客网页版 | Menu.java:3985 |
| `RightClickMenu.clearAllTracked` | 清除所有评估结果 | RightClickMenu.java:254 |
| `RightClickMenu.trackPoint` | 评估此点 | RightClickMenu.java:250 |
| `RightClickMenu.untrackPoint` | 取消评估此点 | RightClickMenu.java:252 |
