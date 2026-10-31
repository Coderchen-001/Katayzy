# Katayzy-new Disposition

Source: `upstream-cross-check.csv` `current-only` rows (28 keys).
This is a disposition record, not a proposal to reopen every feature.

| Key | Label | Disposition | Reason |
|---|---|---|---|
| `BottomToolbar.autoAnalyzeTip` | 自动分析、整盘精析与批量分析 | UI copy | Tooltip/copy, not a feature |
| `BottomToolbar.moreActions` | 更多 | UI copy | Button label |
| `Menu.appearanceTheme` | 外观主题 | Keep | Theme hub |
| `Menu.appleStyleUi` | Apple 风格 UI | Entry keep / renderer retired | IA freeze; old LAF not ported |
| `Menu.boardStyle` | 棋盘样式 | Keep | Board theme submenu |
| `Menu.boardStyleChineseClassic` | 中式古棋盘（角标/中文坐标） | Keep | Board style |
| `Menu.boardStyleJapanese` | 日式九星 | Keep | Board style |
| `Menu.classicColorTheme` | 经典配色（原版 lizzieyzy） | Entry keep / renderer retired | IA freeze; old LAF not ported |
| `Menu.clearCustomBoardBackground` | 恢复默认棋盘背景 | Keep | Custom board feature |
| `Menu.foxKifuButton` | 野狐棋谱 | Keep | Product integration |
| `Menu.layoutMode` | 布局模式 | Keep | IA freeze; do not downgrade |
| `Menu.playerStrengthEstimate` | 棋力水平评估 | Keep | Product differentiator |
| `Menu.playerStrengthEstimateButton` | 棋力评估 | Keep | Product differentiator |
| `Menu.rankMenu.evaluationMode` | 判定依据 | Keep | Rank evaluation |
| `Menu.restartRequiredForIcons` | 部分图标颜色需重启应用后生效 | UI copy | Notice text |
| `Menu.setCustomBoardBackground` | 设置自定义棋盘背景... | Keep | Custom board feature |
| `Menu.syncDiagnostics` | 同步诊断 | Keep | Product integration |
| `Menu.tencentKifu` | 腾讯棋谱 | Keep | Product integration |
| `Menu.tencentKifuButton` | 腾讯棋谱 | Keep | Product integration |
| `Menu.webBoard` | Web 旁观 | Keep | Product feature |
| `Menu.webBoardCopyUrl` | 复制访问地址 | Keep | Web spectator |
| `Menu.webBoardForceExitTrial` | 强制结束试下 | Keep | Web spectator |
| `Menu.webBoardStart` | 启动 Web 旁观 | Keep | Web spectator |
| `Menu.webBoardStop` | 停止 Web 旁观 | Keep | Web spectator |
| `Menu.yikeLiveWeb` | 打开弈客网页版 | Keep | Product integration |
| `RightClickMenu.clearAllTracked` | 清除所有评估结果 | Keep | Analysis feature |
| `RightClickMenu.trackPoint` | 评估此点 | Keep | Analysis feature |
| `RightClickMenu.untrackPoint` | 取消评估此点 | Keep | Analysis feature |

## Decisions already made

- Apple style / classic color: **old renderer retired**, menu entries preserved during UI2.
- “Downgrade to advanced” and menu consolidation: **withdrawn**; IA is frozen.
- Retained product set: board themes, shell presets, appearance, strength, sync diagnostics,
  Fox/Tencent, Web spectator, evaluation points.

## Remaining product question

Only the two legacy UI entries (`appleStyleUi`, `classicColorTheme`) need a later decision:
keep as compatibility aliases, disable with an explanation, or remove during the future
menu-layout cleanup. They are not blockers for UI2.
