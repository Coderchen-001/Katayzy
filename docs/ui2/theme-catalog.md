# Theme Catalog Audit

Status: proposal for the board-theme supplement. No theme is copied or removed yet.

## Source

- reference: `D:\temp\lizzieyzy\theme` at `741c301`
- current release theme pack: 15 themes (`agate`, `antique-hard`, `antique-soft`, `basic`,
  `default`, `jade-hard`, `jade-soft`, `sakura`, `shell-slate-hard`, `shell-slate-soft`, `sumire`,
  `yuki`, `yunzi-hard`, `yunzi-soft`, `yunzi-soft-2`);
- current source tree also contains five older theme directories: `Custom`, `Fast`, `Megapack`,
  `sabaki`, `yasnaya`.

## Asset comparison

The five reference directories use the same image assets as the five older source directories.
Only `theme.txt` differs in places (default/key normalization), so they are not a new image pack.

Partial overlaps with the shipped modern themes:

| Reference theme | Asset overlap with shipped themes |
|---|---|
| Custom | none |
| Fast | `default` (1 asset) |
| Megapack | `default` (2 assets) |
| sabaki | none |
| yasnaya | none |

Partial overlap is not enough to call a theme duplicate: a theme is board + stones + background +
palette. Add a theme only if the complete visual set is unique.

## Recommendation

1. Keep the current 15-theme modern pack as the primary catalog.
2. Add the old five as an optional **Classic** group only if their `theme.txt` is compatible with the
   current `Theme` loader.
3. Deduplicate by the full asset-set hash plus a visual screenshot, not by directory name.
4. Do not overwrite a shipped theme when names collide; use distinct IDs such as
   `classic-custom`, `classic-fast`, `classic-megapack`, `classic-sabaki`, `classic-yasnaya`.
5. Check the license/readme of every third-party theme asset before bundling.
6. The theme picker can group `Modern` and `Classic`; this is a theme-list change, not a menu
   hierarchy change. `Theme` config keys and the Sabaki loader stay compatible.

## Open questions

- Are the five old themes still wanted, or are they only historical?
- Should the Classic group ship in the main release or as an optional theme pack?
- Do any reference themes have license terms that prevent redistribution?
