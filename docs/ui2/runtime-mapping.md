# Runtime Menu Mapping

Purpose: turn the 119-entry omission queue into runtime evidence by matching the clean-build
component dump against the static inventory and the parsed menu tree.

## Clean-build baseline

From the clean `-DskipTests package` artifact (no formatter):

```text
dump lines        773
top-level menus    12
sub-containers     38
JPopupMenu rows    50
separators         47
leaves            340
  JFontMenuItem         162
  JFontCheckBoxMenuItem 173
  InfoMenuItem            1
  JFontLabel              2
  JFontButton             2
```

`350` is the leaf count before excluding the five `InfoMenuItem`/label/button rows; it is not the
same unit as the 213 semantic inventory rows. Do not equate them.

## Buckets

Every runtime row must land in exactly one bucket:

| Bucket | Meaning |
|---|---|
| `matched` | maps to one feature-inventory row by stable key |
| `separator` | visual glue; not a feature, but counted |
| `popup-glue` | `JPopupMenu` row itself; not a feature |
| `toolbar-mirror` | `工具栏/显示` mirror of a command already in the toolbar bucket |
| `engine-dynamic` | engine menu child whose title/count changes with engine state |
| `config-hidden` | present in the tree but hidden by the config profile |
| `unmatched` | no current inventory row; must become an omission record |

Do not silently drop `InfoMenuItem`, `JFontLabel` or `JFontButton`; put them in `unmatched` until
mapped.

## Matching key

Use:

```text
(parent stable path, component class, normalized text)
```

`#N` is still a sibling ordinal and shifts when a separator is inserted. For diffing, compare the
multiset of stable keys, not raw paths. Use text/path only to disambiguate duplicate keys.

## Inventory reconciliation

- `显示` has many toolbar mirrors; map them to `entry_type=toolbar`/mirror rows, not to menu rows.
- The engine menu title is “未加载引擎” in the no-engine baseline; map by top-level position, not
  title.
- `帮助` has one real leaf in this baseline; the no-entry list should remain absent.
- Separators and popup rows are excluded from the feature totals but remain asserted structurally.

The omission queue is closed only when every runtime `unmatched` row has either a new inventory row
or a documented toolbar/hidden/dynamic reason, and every inventory entry is either matched or
explicitly exempted.

## fmt policy

`pom.xml` no longer binds `fmt:format` to `package` (execution phase `none`). A clean build must not
rewrite source. Formatting is an explicit, isolated commit:

```bash
mvn fmt:format
```

Do not run a formatter as part of baseline generation.
