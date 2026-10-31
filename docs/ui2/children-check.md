# Children Check

Source: `real-menu-tree.txt` + `feature-inventory.csv` (255 rows).
Status: **not fully closed**. This records the current child coverage and the remaining structural
issues.

## Verified container groups

| Container | Inventory children | Status |
|---|---|---|
| 文件 > 更多保存 | M-FILE-019/020/021 | verified |
| 显示 > 外观主题 | M-VIEW-007..015 | verified |
| 显示 > 面板 | M-VIEW-021/028 + 077/078 | verified (path-only visual grouping) |
| 显示 > 选点信息 | M-VIEW-069/070/071 | verified |
| 显示 > 显示胜率/目数曲线 | M-VIEW-072..076 | verified |
| 显示 > KataGo相关设置 > Kata评估显示 | M-VIEW-079..089 | verified |
| 设置 > 语言 | M-SET-005..012 | verified |
| 棋局 > 人机续弈 | M-GAME-014A..D | verified |
| 编辑 > 跳转到… | M-NAV-001..004 | verified |

## Known blockers

### 手数(M) vs 落子评价标记

The current CSV still carries entries from the old mistaken split:

```text
M-VIEW-061  最近一手        likely duplicate of M-VIEW-050
M-VIEW-064  评价方式 > 自动  virtual container
M-VIEW-065  评价方式 > 目差  virtual container
M-VIEW-066  评价方式 > 胜率  virtual container
M-VIEW-067  评价方式 > 综合  virtual container
M-VIEW-068  在原位置显示      source has no such entry
```

`落子评价标记(Alt+M)` is a leaf switch. Its old “children” belong to `手数(M)` or
`显示胜率/目数曲线`; the rows above must be removed or reclassified before Gate 1 closes.

### Omission queue

119 omission records remain in `audit-closure.md`. They are deferred to construction-time
verification (visible components are checked against the real screen), but they are still a
documented Gate 1 risk.

## Verdict

Children coverage is verified for the main groups, but the hand-number/rank structural cleanup is
not done. Gate 1 cannot be declared closed yet.
