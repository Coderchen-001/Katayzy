# Inactive / Ghost Entries

These are **not** part of the current visible UI. Do not add them to the visible feature inventory
unless the product decision is to restore them.

## Constructed but not mounted

| Entry | Evidence | Note |
|---|---|---|
| `shareKifu` (分享) | `Menu.java:3881`; `this.add(shareKifu)` commented at `3885` | Five children are built (current SGF, batch SGF, edit, local history, search) but the menu is not added to the menu bar. Record as inactive. |

## No current menu entry

`path-mismatch-triage.md` B-class and the upstream-only list cover entries such as:

- 使用说明 / 关于 / 简介 / 历史记录 / 恢复自动保存的棋谱;
- 检查更新: `CheckVersion` exists but is invoked from `Config.java` at startup, not a menu;
- 同步诊断: `Menu.syncDiagnostics` resource exists, but no `Menu.java` entry;
- 铺平棋盘 / 闪电分析(全局) / 修改多盘引擎对局数 / 干预引擎对局 / 设置>快捷键.

Keep these in the reference/backlog pool, not in the current-UI inventory. `IA 冻结` means “do not
change visible menus”, not “invent entries that do not exist”.
