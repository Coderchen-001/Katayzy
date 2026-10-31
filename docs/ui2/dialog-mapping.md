# Descriptive Dialog Mapping

`surface-coverage.md` lists 16 feature-dialog names that do not match a surface class directly.
This file is the manual mapping. A row may intentionally map to a non-Swing surface (browser,
native tool) or to an inner class hosted by `LizzieFrame`.

| Feature ID | Dialog reference | Surface / owner | Status | Notes |
|---|---|---|---|---|
| M-FILE-001 | 新建对局对话框 | `NewGameDialog.java` | 已映射 | |
| M-GAME-001 | 新对局对话框 | `NewGameDialog.java` | 已映射 | |
| M-GAME-003 | 引擎对局对话框 | `NewEngineGameDialog.java` | 已映射 | |
| M-GAME-013 | 新对局对话框 | `NewGameDialog.java` | 已映射 | |
| M-GAME-015 | 引擎对局 | `NewEngineGameDialog.java` | 已映射 | 多盘参数在同一对话框内 |
| M-ANA-009 | 玩家强度评估对话框 | `LizzieFrame.java` | 已映射 | dashboard 由 `LizzieFrame` 内部构建；数据来自 `analysis/PlayerStrengthEstimator` |
| M-SYNC-005 | BoardSync | `analysis/ReadBoard.java` | 已映射（非 Swing） | 调用原生 `readboard.exe`，没有独立 Swing 对话框 |
| M-SYNC-003 | 输入框/浏览器 | external input + browser | 非 Swing | 野狐棋谱输入流程 |
| M-HELP-001 | 浏览器/PDF | external browser/PDF | 非 Swing | 使用说明 |
| M-HELP-004 | 识别工具 | `analysis/ReadBoard.java` | 已映射（非 Swing） | 与棋盘同步工具同一原生宿主 |
| T-BOT-005 | 同步菜单 | `BottomToolbar.java` (`yike`) | 已映射 | 弹出菜单，不是 dialog |
| T-BOT-008 | 分享菜单 | `BottomToolbar.java` (`sharePopup`) | 已映射 | 弹出菜单，不是 dialog |
| D-SET-001 | SettingsDialog | `ConfigDialog2.java` | 已映射 | 目标名 SettingsDialog 是 UI2 目标名，不是现有类 |
| D-STR-001 | StrengthDialog | `LizzieFrame.java` | 已映射 | 同上，目标名不是现有类 |
| M-ENG-005 | 引擎参数对话框 | `EngineParameter.java` | 已映射 | |
| M-ENG-006 | 引擎规则对话框 | `SetKataRules.java` | 已映射 | |

## Follow-up

- `M-SYNC-005` and `M-HELP-004` are confirmed native `ReadBoard` flows; they have no Swing surface
  and are not part of the 107 surface list.
- `LizzieFrame` is a P0 host; its inner strength dashboard will be extracted during the strength
  report visual pass.
- `ConfigDialog2` is a P0 host; its target `SettingsDialog` is a UI2 name, not a current class.
