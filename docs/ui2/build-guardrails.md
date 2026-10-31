# UI2 Build Guardrails and Golden Baseline

This is the correctness plan for the UI2 migration. It is intentionally separate from the visual
design: it defines how “the UI still works” is proven after every milestone.

## 1. Red line and its precise exemption

Before the golden baseline exists, do not change the production UI:

- no `LizzieFrame` / `Menu` / `TopHeaderPanel` / `SidebarPanel` visual edits;
- no FlatLaf adoption in the normal launch path;
- no widget reskin.

Allowed before the baseline, in separate commits and with no behavior change in the normal path:

- `FakeEngineStatus` and the `EngineStatus` interface;
- a `--ui2-smoke` / `--ui2-dump` entry point;
- test-only component-tree traversal and screenshot capture;
- build scripts under `tools/ui2/`.

Rule: the normal launch path still behaves exactly as before; only opt-in test flags activate the
harness. Harness commits are not UI-migration commits.

## 2. Guardrail layers

### L0 — compile

- normal compile of the app;
- `-Werror`/ErrorProne only for the new `ui2` package, not the legacy tree;
- no new warnings in legacy code.

### L1 — l10n key consistency

Scan every `resourceBundle.getString("...")` and assert the key exists in all **8** bundles:

```text
DisplayStrings.properties
_en_US / _ja_JP / _ko / _th_TH / _zh_CN / _zh_HK / _zh_TW
```

A missing key in any locale is a failure. This catches keys that render literally in the UI.

### L2 — runtime component-tree golden snapshot

Use a fixed harness:

- `FakeEngineStatus` in each of the five states;
- fixed test SGF;
- fixed config profiles;
- fixed window size and theme;
- opt-in `--ui2-smoke` startup, then dump and exit.

Dump recursively:

- menu bar and its 12 top-level menus;
- `TopHeaderPanel.contentPanel` including left/center/right groups (65 widgets);
- sidebar and right-panel常驻 components;
- for each component: normalized path, class, label, mnemonic, accelerator, visible, enabled.

Normalize volatile data: engine names, timestamps, dynamic counters, animation frames. Do not compare
object identity or raw pixel positions.

Initial coverage is the **resident main window**. Modal dialogs and popups are not in the tree and
are covered by L4 screenshots; do not claim the snapshot covers all 107 surfaces.

Baseline matrix:

- default config;
- all-visible config;
- analysis / game / double-engine scenarios;
- zh_CN and en_US (WrapLayout reflows with text length).

### L3 — startup smoke

- launch with the harness;
- wait for a stable window;
- fail on exception/error dialog;
- dump and exit with a non-zero code on failure.

JCEF can be disabled for speed during the migration, but one milestone must run with JCEF enabled
before release.

### L4 — screenshot regression

- screenshot only key surfaces;
- perceptual hash or manual bless;
- expected visual changes are reblessed per milestone;
- bind baselines to DPI and window size; a different machine/scale is an environment change, not a
  regression;
- dialogs and popups are included here because L2 cannot reach them.

### L5 — text-clipping scan

Do not compare `preferredSize` to bounds in this null-layout legacy UI; that generates noise.

Instead:

- font metrics width vs available width;
- line height vs available height;
- report clipped text with component path;
- run across all 8 locales, especially Korean/Thai;
- respect the self-painted whitelist.

## 3. Additional invariants

### Action wiring must not change

`wrap, not rewrite`: a migration commit may move a component or replace its visual class, but the
number of `addActionListener` calls and the bound actions must stay the same. Grep the diff as a cheap
check.

### Config round-trip

Load an old `ui.cfg`/`uiConfig`, save it, diff the keys:

- existing keys preserved;
- no user-visible values silently changed;
- new keys may be added, never removed.

### Menu/IA invariant

The 257-row feature inventory, 12 top-level menus and 65 topPanel widgets remain the reference. Any
unexpected tree difference in L2 is a regression; expected differences need a reviewed bless.

### Rollback drill

After the first milestone, revert to the previous tag once and re-run the gates. A guardrail that has
never been exercised is not a guardrail.

### Licensing

“Copy the mature design” means use libraries, patterns and interaction models, not paste code/assets
from IntelliJ, Lichess or other projects without checking the license. FlatLaf/MigLayout/JSVG etc.
stay under their original licenses; theme assets keep their notices.

## 4. Initial milestone order

1. Add the test harness and `FakeEngineStatus` in isolated commits.
2. Generate the golden baseline from the unchanged production UI.
3. Add tags: `ui2-golden-baseline` and keep `ui2-base-2026-10-31`.
4. Implement only L0/L2/L3 in `ui2-check`.
5. Add L1 (8 bundles), then L4/L5 as coverage grows.
6. First real UI change: FlatLaf shell preset (structural zero-diff); verify the gates can detect
   regressions.
7. Then widgets/MainWindow migration.

**Diagram correction:** the flowchart's “widgets → FlatLaf” order is wrong. FlatLaf must come
before widgets, because LAF defaults, fonts and colors affect every primitive. Build and test the
widgets under the target LAF; otherwise they will be built against the old look and reworked.

### Gate scope

- L0/L2/L3: every **UI migration commit**;
- L4 screenshots: each visual milestone, and every modal/dialog surface;
- L5 clipping scan: each locale/font milestone;
- documentation-only commits do not run the full gate.

## 5. Known limitations

- L2 cannot see modal dialogs; L4 covers them.
- Dynamic labels (engine names, shell presets) need normalization or explicit allowances.
- Web/JCEF and native `readboard.exe` need their own smoke tests.
- Golden files are environment-sensitive; record JDK, OS, DPI and window size.
- A green gate means “no detected structural/visual regression”, not “all interaction paths are
  correct”. Click behavior is protected by the wrap-not-rewrite discipline and manual acceptance.
