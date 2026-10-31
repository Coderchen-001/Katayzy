# Gate 1 Self-audit

Status: **not passing yet**. This is the self-audit report, not a request to waive the gate.

## 1. Bidirectional coverage

Checked:

- feature inventory: 230 semantic rows;
- label audit: 2632 records (2618 resource-key + 14 hard-coded);
- upstream key comparison: 447 keys (394 both / 25 ref-only / 28 current-only);
- 8 upstream-only features added, 18 current-only features marked `origin: katayzy-new`.

Findings:

- the inventory is no longer a 119-row skeleton, but it is still not proven 100% complete;
- the label audit is a lookup table, not a feature list;
- descriptive dialog names are now mapped in `dialog-mapping.md`;
- the remaining risk is duplicate or missing submenu children, not top-level menus.

Action:

- `check_inventory_duplicates.py` passes (`230` unique IDs / `path+action`);
- keep the Gate 1 coverage check open until a full child-by-child pass is recorded.

## 2. Children completeness

Completed groups:

- view children: move numbers, rank marks, suggestions, win-rate, variation panel, Kata estimate;
- save/clipboard shortcuts;
- board transforms and view modes;
- right-click remaining actions.

Still open:

- a final child-by-child comparison for every container row;
- confirmation that no container row is being counted as a completed child;
- review of `origin: katayzy-new` entries that are pure UI copy versus real features.

## 3. Shortcut conflicts

Five rows carry `shortcut_conflict`:

```text
M-VIEW-021  Alt+T  comment panel / pause engine game
M-ANA-001   Space  analysis / stop AI game
M-ANA-002   T      hawk-eye / return to main branch
M-GAME-012  Alt+T  pause engine game / comment panel
M-EDIT-012  T      return to main branch / hawk-eye
```

`shortcut_resolution` now records the intended context rule for each. This is documentation only;
the `UiState`/`ActionRegistry` implementation is still pending. Gate 2 must verify the encoded
context, not just the CSV text.

## 4. l10n consistency

- `menu-label-audit.csv` resolves `\uXXXX` through `DisplayStrings_zh_CN.properties`;
- 17 unresolved keys remain, mostly config/command keys, not menu entries;
- known label corrections: `新建`, `加载SGF棋谱中的贴目`, `评估此点`, `引擎规则(KataGo)(Shift+D)`.
- The `Crtl` typo was fixed in all seven l10n files and the CSV notes updated.

Still open:

- a recorded 20-row character-by-character sample in the final Gate 1 review;
- one final audit regeneration after all label edits.

## 5. Upstream 741c301 cross-check

Reference: `D:\temp\lizzieyzy` at `741c301`.

- 447 menu-bearing keys compared;
- ref-only: 25, current-only: 28;
- 8 ref-only feature entries added (`about`, `checkUpdate`, `introduction`, engine-game controls,
  flash analysis, legacy board sync);
- current-only entries are marked for product decision, not silently deleted;
- `checkUpdate` was merged into `checkVersion`.

Still open:

- finalize the `katayzy-new` keep/drop list;
- confirm that `origin: upstream-741c301` entries are still supported by the current build.

## P0 confirmation

P0 surfaces (9):

```text
LizzieFrame.java        411 style hits
ConfigDialog2.java      232
Menu.java                83
BottomToolbar.java       60
SidebarHeaderPanel.java  31
SidebarPanel.java        27
SidebarStatusPanel.java   9
TopHeaderPanel.java       3
WindowMenuStrip.java      3
```

`AppleStyleSupport` is not P0: it is a legacy renderer, kept only until FlatLaf covers the screens
that still reference it.

## Surface classification

- retain: 8 renderer classes;
- replace: 97;
- retire: 2 (`AppleStyleSupport`, `ToolbarPopupSupport`);
- invisible: 0 (no reference-chain evidence yet).

## Verdict

Gate 1 is **not ready to pass**. The remaining blockers are:

1. complete the final child-by-child inventory pass;
2. finish the `katayzy-new` product decisions;
3. encode the shortcut conflict resolutions in `UiState`;
4. record the final l10n sample after the audit regeneration.

P0 is confirmed; surface classification and dialog mapping are complete enough to proceed to the
Gate 1 review, but not enough to start `ui2/widgets` yet.
