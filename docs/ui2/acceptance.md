# UI2 Acceptance

## Gate 0 — Baseline

- [x] `main` remains the release baseline at `be005065`;
- [x] pre-UI2 menu work archived on `ui2-base` at `84346f2d`;
- [x] tag `ui2-base-2026-10-31` created;
- [x] `ui2` branch created from the baseline;
- [x] branches and tag pushed to `origin`.

## Gate 1 — Feature inventory

- [ ] `docs/ui2/feature-inventory.csv` reviewed against `Menu.java`;
- [ ] reviewed against `BottomToolbar.java`;
- [ ] reviewed against right-click menus and keyboard shortcuts;
- [ ] dynamic states represented with `state_matrix` / `checked_condition`;
- [ ] upstream `D:\temp\lizzieyzy` cross-check completed;
- [ ] no missing top-level entry point;
- [ ] every existing dialog and toolbar action has a row.

The inventory is the migration checklist. A vertical slice is not accepted if its actions are not
represented here.

### Gate 1 review rules

- **Audit sampling:** filter `menu-label-audit.csv` to entry-bearing code (`Menu.java`,
  `BottomToolbar.java`, `RightClickMenu*.java`) before sampling. The audit also contains dialog
  copy, error text and config keys; sampling the unfiltered 2600+ rows produces false positives.
- **Hard-coded labels:** review all 14 `kind=hard-coded` rows explicitly; labels without a
  resource key are compared as written.
- **Children completeness:** compare the six submenus (move numbers, move-rank marks, suggestions,
  Kata estimate, win-rate settings, variation panel) by enumerating labels/actions in each code
  block. Do not use line counts as proof of completeness.
- **Shortcut conflicts:** for each `shortcut_conflict` row, verify the two `state_matrix` conditions
  can actually overlap. If they can, add a resolution policy (context, priority, or disabled
  fallback); “different menu context” is not a sufficient rule unless encoded in the state model.
- **l10n consistency:** sample at least 20 resource-key rows and compare the inventory label with
  the decoded `DisplayStrings_zh_CN.properties` value character by character.
- **Upstream cross-check:** compare against the upstream commit recorded in the reference checkout,
  not just the directory name; confirm current-only features are not mistaken for obsolete ones.
- **Action vs entry:** one ActionRegistry implementation may serve multiple entries; the inventory
  keeps one row per visible entry point.
- **Mechanical duplicate scan:** run `python tools/ui2/check_inventory_duplicates.py`; it must
  report `OK` before Gate 1 review. Different paths may share an Action, but the same
  `path+action` may not.

## Gate 2 — Vertical slice

- [ ] `MainWindow` opens with FlatLaf active;
- [ ] embedded `BoardRenderer` renders correctly at 100% and HiDPI;
- [ ] existing board input (click, drag, keyboard) still works;
- [ ] top bar/menu bar uses FlatLaf;
- [ ] menu hierarchy, labels, shortcuts and entry points are unchanged (IA freeze);
- [ ] `ui-surface-inventory.csv` reviewed: every visible frame/dialog/popup/custom-paint surface is
  classified `保留 / 不可见 / 待替换` before any old renderer is removed;
- [ ] `surface-coverage.md` reviewed: every dialog reachable from the feature inventory has a
  surface class or a documented manual mapping;
- [ ] after the visual rewrite, `grep -rn "new Color(\|new Font(" ui2/` returns zero except the
  whitelist generated from `status=保留` in `ui-surface-inventory.csv`; do not pre-write the
  whitelist by hand.
- [ ] bottom bar uses FlatLaf and the normal/primary/danger role model;
- [ ] left analysis column placeholder renders;
- [ ] right variation column placeholder renders;
- [ ] popups open above the invoking button, centered, without covering it;
- [ ] no `AppleStyleSupport.applyUiDefaults()` call on the FlatLaf path;
- [ ] no double scaling.

## Gate 3 — Fake engine states

`FakeEngineStatus` drives all five states without starting KataGo:

- [ ] NOT_LOADED: `未加载引擎`;
- [ ] LOADING: `正在加载引擎…`;
- [ ] ANALYZING: `分析中 · 空格继续`;
- [ ] PAUSED: `已暂停 · 空格继续`;
- [ ] ERROR: error text and recoverable action.

Screenshots must be taken at the same window size and shell theme for old/new comparison.

## Gate 4 — Theme and scaling

- [ ] graphite shell;
- [ ] warm shell;
- [ ] slate shell;
- [ ] light shell (once implemented);
- [ ] board theme switch does not change shell colors;
- [ ] shell switch does not change board theme;
- [ ] popups/dialogs use solid surfaces, not alpha glass;
- [ ] font sizes consistent at 100% and 150% scale;
- [ ] long engine names ellipsize without layout shift.

## Gate 5 — Settings and strength dialog

- [ ] settings uses left category navigation + right cards;
- [ ] settings rows use the shared row component;
- [ ] existing config keys remain compatible;
- [ ] strength report uses cards + chart/table, not a hand-painted page;
- [ ] dialog footer buttons follow primary/secondary/danger roles;
- [ ] Esc closes dialogs and restores focus.

## Gate 6 — Retirement of old UI

- [ ] feature inventory is 100% `交互通过`;
- [ ] no new features added to old UI during migration;
- [ ] old UI kept for one release cycle after Gate 5;
- [ ] changelog records the removal;
- [ ] old UI code deleted in one dedicated commit;
- [ ] final merge to `main` uses `--no-ff`.

## Screenshot matrix

Capture old and new for each state:

| State | Window | Shell | Board theme | Notes |
|---|---|---|---|---|
| unloaded | 1280×800 | graphite | default | fake engine |
| loading | 1280×800 | graphite | default | fake engine |
| analyzing | 1280×800 | graphite | default | fake engine |
| paused | 1280×800 | graphite | default | fake engine |
| error | 1280×800 | graphite | default | fake engine |
| analyzing | 1920×1080 | warm | default | long engine name |
| analyzing | 1920×1080 | slate | custom | sidebar collapsed |
| settings | 1280×800 | graphite | — | all tabs |
| strength | 1280×800 | graphite | — | report loaded |

## Review checklist

- [ ] no text clipping;
- [ ] no overlapping cards;
- [ ] no transparent popup over a white window;
- [ ] no black-on-black or white-on-white text;
- [ ] hover does not change text color;
- [ ] disabled state is visually distinct but readable;
- [ ] keyboard focus is visible;
- [ ] menu labels left aligned, shortcuts right aligned;
- [ ] check marks in the left gutter;
- [ ] submenu arrows visible.
