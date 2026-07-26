# Tracking Analysis Contract

“追踪分析此点”复用当前前台本地 KataGo 的唯一 GTP stream。Production 不再创建第二个
tracking KataGo 进程，也不保留 legacy runtime、console、preload、warning、keep-tracking、
feature flag 或失败 fallback。

本合同补充 `docs/SNAPSHOT_NODE_KIND.md`；tracking 只显示 transient overlay，不改变其中的
`MOVE`、`PASS`、`SNAPSHOT` 或 ReadBoard history 语义。

## Production 入口

- `LizzieFrame` 在一个 frame 生命周期内只实例化一个 `TrackingAnalysisController`。
- 本地右键入口把 add/remove/clear 意图提交给该 controller。
- 当前 `ReadBoard` 存在时，同一右键入口必须先通过
  `ReadBoardTrackingEligibilityAdapter` 复验 stable accepted frame；不稳定 frame 不发送
  tracking request，也不重试。
- 两个入口只复用 `Lizzie.leelaz`。引擎必须 started、loaded、local direct、KataGo，且不在
  engine-game mode；Remote/WebSocket/SSH/double-engine 不在本合同范围。
- Add 失败不显示 tracking-specific popup、`X` 或 retry 文案。

## Controller ownership

`TrackingAnalysisController` 唯一拥有：

- selected points、newest-first pending points 和每点 current attempt；
- immutable context、request generation 和 progress timeout；
- current stream-only lease handle；
- immutable `DisplaySnapshot`。

UI 只能提交 add/remove/clear。Controller 不启动、切换、重启或恢复引擎，不写
Board/history，不写普通 `bestMoves`，也不触发普通分析功能。

每个点使用一个独立 stream-only lease。Initial numbered stop 完整结束后只发送：

```text
kata-analyze <interval> allow B <coord> 1 allow W <coord> 1
```

只有目标坐标 visits 严格增加才续期 8 秒 progress timeout。达到该点 visits 后发送 final
numbered stop；只有 final fence 成功关闭后结果才标记 completed，然后调度 newest pending
point。Clean natural completion 可按首个 receipt 恢复原 ponder；任何 handoff、ordinary
release、context failure 或 transport failure 都不得恢复。

## Context 与 ReadBoard

Tracking context 至少绑定：

- history identity 与 current display/history node identity；
- board size、完整 stones fingerprint、to-play、rules、komi；
- engine identity/incarnation；
- interval 与每点 visits；
- 可选 ReadBoard helper identity、revision、accepted node 与 board revision。

第一个点建立 context；后续 add 必须匹配。局面、history/display node、board size、stones、
to-play、rules、komi、engine/incarnation、参数或 ReadBoard identity/revision/node 任一变化都
立即清 selected/pending/result 并 release 当前 lease。

ReadBoard stable 条件与失效顺序由 `ReadBoardTrackingEligibilityAdapter` 和 `ReadBoard`
eligibility snapshot 拥有。Tracking 不拦截 helper protocol，不推导或补造 `MOVE/PASS`。

## Display 与 renderer

- `DisplaySnapshot` 是 immutable value；renderer 不读取 controller 内部 mutable state。
- Renderer 只在 snapshot 的 history identity 与 current history 相同，且 display node identity
  同时等于 current display/history node时绘制。
- selected point 立即显示虚线环；remove pending 立即隐藏且不影响 current；remove current 或
  clear 立即隐藏全部相应 overlay，后台只完成安全归还。
- Completed result 可在同一 context 内与新点并存。Tracking result 只画 overlay，不进入
  SGF、普通候选、胜率图或 history node。
- Strict safe-GTP release 可冻结最后一个仍 valid result snapshot；其他 ordinary、typed
  handoff、lifecycle、context 或 transport release 清空 selected/display。

## Stream arbitration

`Leelaz` 唯一拥有 stream arbitration、initial/final fence、ordinary queue、typed handoff 和
transport terminal/rebind settlement。锁序保持：

```text
engineArbitrationLock -> commandQueue
```

- Ordinary command 先进入原 queue，再 claim tracking release；final fence 后由原 writer 按
  FIFO 写出。
- Safe raw GTP whitelist 仅包含无 caller ID、exact arity 的 `name`、`version`、
  `protocol_version`、`list_commands`、`known_command <name>` 和 `showboard`。
- Safe query 发布 `SAFE_READ_ONLY_QUERY` 并冻结最后 valid overlay；其他 admitted ordinary
  发布 `ORDINARY_OPERATION` 并清空，二者都不自动 reacquire。
- Typed foreground/retained-mode first winner 在 claim 时立即清 display，final fence 后激活
  existing target owner。第二 typed/lifecycle contender沿用 existing busy。
- Destructive lifecycle 继续使用 existing reservation并立即执行原 action；不得保存 callback、
  continuation 或 user intent。
- Final fence、terminal 或 transport 输出不确定时 fail-closed，ordinary queue 不能穿过可能
  仍开放的 analysis stream。

## 配置兼容

唯一保留的 tracking 专用设置是每点 visits：

```text
tracking-analysis-max-visits
```

加载旧配置时，若新 key 不存在，则迁移 `tracking-engine-max-visits` 的正值；随后删除旧
`tracking-engine-max-visits`、`tracking-engine-preload` 和
`tracking-engine-skip-warning`。旧 preload/engine command 不能启动第二进程。

## 测试与发布边界

- Production-entry tests 必须同时覆盖 local 与 stable ReadBoard route，并证明二者共享同一
  controller/current engine。
- Controller、lease、ordinary、handoff、lifecycle、ReadBoard GMA、renderer stale-node gate
  和配置迁移必须有 deterministic headless coverage。
- Windows integration harness 只存在于 test source，通过 public controller/lease/handoff
  seam 使用 monotonic clock 输出原始 CSV/JSON；它必须捕获并恢复 default uncaught handler
  与 EDT EventQueue，Executor task 必须显式取得 `Future` 或 join。
- Windows GUI、真实 KataGo/OpenCL 与实际进程数仍需 Ticket 07 验收；本 source candidate 在
  该 gate 前不得 merge 或 release。

## Out of scope

- Remote Compute/WebSocket、Java SSH、外部 ssh/plink、double-engine tracking。
- 第二 tracking process、dual-runtime route、feature flag 或 fallback。
- Cross-point persistent lease、跨 context 恢复、结果持久化、PV overlay、SGF tracking 字段。
- Ordinary response ledger、第二 queue、generic transaction/callback registry、rollback 或
  自动重试。
