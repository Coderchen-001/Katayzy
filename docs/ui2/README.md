# UI2 Migration Documents

This directory is the acceptance record for the UI2 rewrite.

| File | Purpose |
|---|---|
| `feature-inventory.csv` | Migration checklist: every menu, toolbar, context-menu and status entry |
| `design-notes.md` | Architecture, FlatLaf/MigLayout decisions, theme and scaling rules |
| `acceptance.md` | Gates for the vertical slice, fake-engine screenshots and old-UI retirement |

## Status

`feature-inventory.csv` is a **draft** generated from the current `Menu.java`,
`BottomToolbar.java` and right-click menus. It is not yet complete.

Before the vertical slice is accepted:

1. Review every top-level menu and submenu against the current build.
2. Cross-check against upstream `D:\temp\lizzieyzy`.
3. Add missing keyboard shortcuts and dynamic states.
4. Mark each row `未接 -> 已接 -> 截图通过 -> 交互通过`.

The CSV deliberately uses one row per semantic action. Dynamic menu items are expressed through
`state_matrix` and `checked_condition` instead of duplicate rows.

## Git

```text
main                         release baseline
ui2-base                     pre-UI2 UI archive
ui2                          UI2 migration work
tag ui2-base-2026-10-31      migration baseline
```

`main` stays release-ready; UI2 changes happen on `ui2`. The old UI is frozen except for crash and
security fixes.
