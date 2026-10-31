# UI2 Design Notes

Status: draft for the UI2 migration. This file records decisions, constraints and known coupling.
It is intentionally kept in the repository so the migration does not depend on session memory.

## 1. Goal

Rebuild the Swing UI on a modern, maintained foundation without touching the board, engine or SGF
logic.

In scope:

- main window shell;
- menu bar, toolbars and popups;
- left analysis column;
- right variation column;
- settings dialog;
- player-strength dialog;
- common dialogs and theme surfaces.

Out of scope for UI2:

- KataGo / Leelaz protocol changes;
- board rules;
- SGF parsing;
- engine packaging and launcher scripts.

## 2. Foundation

Planned stable dependencies:

| Dependency | Version | Purpose |
|---|---:|---|
| FlatLaf | 3.7.2 | Look and feel, menus, buttons, tables, dialogs |
| flatlaf-extras | 3.7.2 | SVG icons and extras |
| flatlaf-swingx | 3.7.2 | SwingX compatibility |
| miglayout-swing | 11.5.2 | Layout |
| SwingX | 1.6.4 (existing) | Existing JXTable/JXTaskPane code |

Do not use FlatLaf `3.8-SNAPSHOT`. Pin release versions in `pom.xml`.

Do not add Radiance's Look and Feel alongside FlatLaf. Use one LAF only.

Optional later:

- JNA for Windows dark title bar / system theme detection;
- FlatLaf SVG icons instead of Ikonli/jsvg.

## 3. Architecture

Target packages:

```text
ui2/
  MainWindow
  BoardView
  TopBar
  AppMenuBar
  BottomBar
  AnalysisSidebar
  VariationSidebar
  StatusFooter
  SettingsDialog
  StrengthDialog
  AppDialog
actions/
  AppActions
  ActionRegistry
  EngineStatus
  EngineStatusAdapter
  FakeEngineStatus
```

Dependency direction:

```text
ui2 -> AppActions / EngineStatus / BoardRenderer
ui2 -X-> Leelaz internals
ui2 -X-> AppleStyleSupport
ui2 -X-> MorandiPalette
ui2 -X-> JFontMenu
```

`BoardRenderer`, `Input`, the Sabaki theme loader and the existing win-rate graph painter are reused
behind adapters. Their behavior and config keys must not change.

## 4. Menu rules

Follow desktop conventions rather than hand-drawn rows:

```text
[check/icon gutter]  label                         shortcut  submenu arrow
       24 px         left aligned                  right     far right
```

- labels left aligned;
- check/radio marks in the left gutter;
- shortcuts right aligned;
- no shortcut for most checkable items; the right column stays empty;
- submenu arrow at the far right;
- hover is a subtle rounded row background, text color does not change;
- disabled items use muted text;
- separators are 1 px inset lines;
- popups are anchored above the invoking button, centered on the button axis, with a 2 px gap.

FlatLaf owns menu rendering. `ToolbarPopupSupport` is a pre-UI2 workaround and will be deleted once
the FlatLaf menus are validated.

## 5. Theme model

Keep board themes and app shell themes separate:

```text
BoardTheme      board image, stones, background
AppShellTheme   surface, text, border, accent, hover
```

`AppShellTheme` currently contains translucent colors for glass chrome. Rules:

- glass/alpha surfaces only for chrome layered over the board;
- popups and dialogs use solid `popupBackground` / `dialogBackground` colors;
- never write an alpha color into `PopupMenu.background` or `Dialog.background`;
- mapping goes through a single `UiTokens` / `AppThemeInstaller`.

Menus, menu bar, popups, settings and dialogs follow the shell theme. Board themes never change the
shell palette.

## 6. Fonts and scaling

- Use one `UIManager.put("defaultFont", ...)` for FlatLaf;
- keep `Microsoft YaHei` for UI and `Consolas` for numeric/win-rate displays;
- do not create per-component `new Font(...)` in new UI code;
- do not enable `flatlaf.uiScale` until the existing `Config.isScaled` / `Utils.zoomIn()` scaling has
  been audited; double scaling is a known risk;
- custom-painted board text may continue to use the existing frame font settings.

## 7. Look and feel integration

`Lizzie.setLookAndFeel()` and `resetLookAndFeel()` currently install the system LAF and then call
`AppleStyleSupport.applyUiDefaults()`. This will override FlatLaf.

Required:

- add a UI2/FlatLaf branch;
- do not call `AppleStyleSupport.applyUiDefaults()` when FlatLaf is active;
- ensure `resetLookAndFeel()` follows the same branch;
- keep the old LAF path behind a feature flag until UI2 is accepted.

## 8. Feature inventory

`docs/ui2/feature-inventory.csv` is the migration checklist.

A resource key is not automatically an action. For example,
`BottomToolbar.detail.continueGame` is a label in the engine PK detail panel, while
`Menu.continueGenmoveGameAsWhite/Black` and `Menu.continueAnalyzeGameAsWhite/Black` are four
separate continuation actions. Deduplicate by action, not by label.

Keep the distinction between an **Action implementation** and an **entry point**:

- menu and toolbar entries may share one `ActionRegistry` ID (for example both `openAutoAnalyze`
  entries);
- the inventory keeps one row per visible entry point;
- ActionRegistry registers the action once.

So “deduplicate by action” means one implementation, not one UI row.

`origin` column: `both` for shared entries, `katayzy-new` for fork-only features, and
`upstream-741c301` for upstream entries added during the cross-check. New-only entries need a
product decision; they are not silently dropped during the merge.

Dynamic labels: the `label` column stores the baseline label. If the label changes with state,
record the rule in `notes` and the visibility condition in `state_matrix`. The second-engine menu
is the first example: visible only in double-engine mode, and its title becomes `[n] engine-name`
after loading.

Rules:

- one row per semantic action, not per visual occurrence;
- dynamic items use `state_matrix` and `checked_condition` instead of duplicate rows;
- `migration_status` moves through `未接 -> 已接 -> 截图通过 -> 交互通过`;
- cross-check against the upstream `D:\temp\lizzieyzy` menu list, then against the current build.

The first CSV is a draft. It must be reviewed for missing entry points before the vertical slice is
accepted.

## 9. Engine status boundary

UI2 consumes `EngineStatus`, never `Leelaz` internals.

```text
EngineStatus
  state: NOT_LOADED | LOADING | ANALYZING | PAUSED | ERROR
  engineName
  message
```

`EngineStatusAdapter` reads existing `Leelaz` / `EngineManager` fields. `FakeEngineStatus` implements
the same interface and can drive all five states for screenshots and tests.

All status notifications to Swing must be marshalled to the EDT.

Do not refactor `Leelaz` in the first slice. Add the adapter first; a listener interface can follow
after UI2 is stable.

## 10. Migration rules

- `main` remains the release baseline;
- `ui2-base` and `ui2` are migration branches;
- old UI is frozen: crash/security fixes only, no new UI features;
- merge `main` into `ui2` for critical fixes, do not let the branches drift;
- final merge `ui2 -> main` uses `--no-ff`;
- remove the old UI only after the feature inventory and screenshot gates pass, plus one release
  cycle;
- `Config` key names and theme directory layout stay backward compatible.

## 11. Visual references

- Windows 11: menus, settings cards, toggles, focus rings;
- macOS: sidebar spacing, grouped settings, typography;
- IntelliJ: tool windows, settings navigation, tables;
- VS Code: status bar and command surfaces;
- Sabaki: board themes.

Original design is limited to app-specific composition: analysis column hierarchy, board/analysis
layout, strength report cards and theme presets. Widgets and menus are not hand-drawn.
