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

## Resolved: 手数(M) vs 落子评价标记

The prior “rank is a leaf” conclusion was wrong. `addRankMarkMenu(null, moveRankMenu)` builds a real
subtree in the helper method (`Menu.java:8472`), which the static tree parser cannot see.

Corrected rows:

```text
M-VIEW-061  落子评价标记 > 最后1手       rankLastMove
M-VIEW-064  落子评价标记 > 评价方式 > 自动  rankModeAuto
M-VIEW-065  落子评价标记 > 评价方式 > 目数  rankModeScore
M-VIEW-066  落子评价标记 > 评价方式 > 胜率  rankModeWinrate
M-VIEW-067  落子评价标记 > 评价方式 > 综合  rankModeCombined
M-VIEW-068  落子评价标记 > 在原位置显示     showMoveRankInOrigin
M-VIEW-091  落子评价标记 > 无              rankNoneMove
M-VIEW-092  落子评价标记 > 评价方式         container
```

These 8 rows carry the narrow exemption `helper-method subtree (addRankMarkMenu L8472)`; the
right-click popup reuses the same helper.

Keep `M-VIEW-060/062/063` under `手数(M)`; they are different actions.

## Remaining risk

119 omission records remain in `audit-closure.md`, deferred to construction-time verification.

## Verdict

Children coverage is complete for the main groups, and the hand-number/rank structural error is
resolved. The omission queue is a construction-time check, not a children blocker.
