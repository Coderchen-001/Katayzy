# UI2 Backlog

Use this file for items explicitly deferred. Do not rely on chat memory.

## Board themes (non-blocking)

- [ ] Verify the five Classic themes (`Custom`, `Fast`, `Megapack`, `sabaki`, `yasnaya`) against
  the current `Theme` loader; current assets match the reference, only `theme.txt` differs.
- [ ] Decide whether Classic ships in the main release or as an optional directory pack.
- [ ] Add Classic as optional directories without overwriting the 15 modern themes; use
  `classic-*` IDs on collision.
- [ ] Keep third-party theme notices/licenses when bundling.
- [ ] Make the theme picker directory-scan driven; no hard-coded theme ID list, no hard-coded
  preview PNGs.
- [ ] Build the theme smoke test:
  - scan `theme/*/theme.txt`;
  - load through the existing `Theme` loader;
  - verify all referenced assets exist;
  - render a fixed-size offscreen preview through `BoardRenderer`;
  - write per-theme pass/fail reasons to a report file, not only console;
  - skip a bad theme and continue.
- [ ] Check `BoardRenderer` for live-component coupling before offscreen rendering
  (`getFontMetrics` on a component, live `getWidth()/getHeight()` instead of passed dimensions).
  If present, fix it as part of the `BoardView` decoupling; this is also an acceptance criterion.
- [ ] Later: curate, create new themes, or add preview cards. This is not a UI2 blocker.

## Gate 1 / UI2 prerequisites

- [ ] Classify all 107 `ui-surface-inventory.csv` rows as `保留 / 不可见 / 待替换`;
- [ ] give evidence for every `不可见` classification;
- [ ] map the 16 descriptive dialog references in `surface-coverage.md`;
- [ ] confirm the P0 list (currently 9 classes, including `Menu.java`);
- [ ] run `check_inventory_duplicates.py` and record `OK`;
- [ ] complete the Gate 1 self-audit before starting `ui2/widgets`.

## UI2 visual work

- [ ] `ui2/widgets`: Card, StatusDot, Badge, EmptyState, ProgressRow;
- [ ] `MainWindow` + FlatLaf shell;
- [ ] top/bottom bar and menu bar reskin;
- [ ] left/right columns reskin with preserved layout and actions;
- [ ] settings / strength / sync / kifu import / Web spectator visual passes;
- [ ] generate the self-painted whitelist from `status=保留` rows;
- [ ] run the `ui2/` hard-coded-color grep gate.

## Explicitly frozen

- menu hierarchy, labels, shortcuts, entry points;
- button text and click behavior;
- board-theme loading and config keys;
- feature inventory actions.

Do not “downgrade to advanced”, merge menus, or delete entries during UI2.
