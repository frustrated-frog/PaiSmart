# 知枢 Agent 运行时闭环 PRD

## 1. 文档信息

- 产品：知枢（PaiSmart）企业知识 Agent
- 版本：Agent Runtime Closure 1.0
- 目标分支：`codex/agent-runtime-closure`
- 上游能力：查询规划、Multi-query、BM25/Dense 召回、RRF、重排、父子块、ReAct 工具调用、运行账本、基础记忆与检索评测

## 2. 背景与问题

当前系统已经具备高质量混合检索和可观测的 ReAct 工具调用，但运行时仍存在五个闭环缺口：

1. 查询规划能识别歧义，却没有保存等待澄清任务并在用户补充后恢复。
2. 检索结果可以进入回答，但系统没有独立判断证据是否充分、冲突或缺失。
3. 循环主要通过固定轮数和工具调用数终止，无法识别重复动作与无进展。
4. checkpoint 能记录运行谱系，但重试仍从原问题开始，不具备节点级恢复和工具回放语义。
5. 评测集中在检索指标，无法回答 Agent 是否选对动作、忠实引用、稳定完成和正确恢复。

本期把聊天助手从“有工具的对话循环”升级为“有状态、可收敛、可恢复、可验证的知识 Agent”。

## 3. 产品目标

### 3.1 用户目标

- 问题含糊时只追问最关键的信息，并在补充后继续原任务。
- 证据不足时主动补检索；仍不足时明确说明缺口，不编造答案。
- 运行过程不重复调用相同工具，不在原地循环。
- 浏览器刷新、服务中断或节点失败后能从安全位置恢复。
- 用户能看到任务目标、当前进度、证据状态、停止原因和恢复结果。

### 3.2 工程目标

- Agent 每轮决策具有可序列化状态、确定性守卫和明确终止原因。
- 工具调用具有风险、并发、副作用和回放策略。
- 运行 trace 可以用于回放、评测、故障归因和回归门禁。
- 新能力失败时可降级，不破坏现有聊天、引用和多租户权限链路。

### 3.3 求职展示目标

项目应能通过代码、演示和指标回答以下问题：

- 如何判断 RAG 证据足够，而不是检索一次就生成？
- 如何在少打断用户的前提下实现有状态澄清？
- 如何防止 Agent 重复调用工具或无进展死循环？
- checkpoint、event log、幂等和节点恢复分别解决什么问题？
- 如何评测一次 Agent 决策轨迹，而不只评最终答案？

## 4. 范围

### 4.1 本期范围

- Evidence Verifier 与最多两轮 Corrective Retrieval。
- Clarification Policy、Pending Task、Slot Merge 与恢复。
- Action Fingerprint、Progress Signature、Loop Guard 与 Terminal Reason。
- Task/Progress Ledger 及模型回合前再水合。
- 工具 Replay Policy、幂等键、工具调用账本和安全恢复。
- 轨迹、忠实度、澄清、循环和恢复评测。
- 对应的 Agent 步骤事件和前端状态展示。

### 4.2 非目标

- 不实现默认多 Agent、Swarm 或通用工作流平台。
- 不实现任意写操作工具；先建立协议和只读工具的回放基础。
- 不允许模型自动发布 Skill、修改系统提示词或激活长期记忆。
- 不引入 GraphRAG；只有评测证明普通检索持续失败时再考虑。
- 不追求跨机器分布式图执行器，本期保持 Java 领域化运行时。

## 5. 核心用户旅程

### 5.1 证据充分，直接回答

1. 用户提出明确知识问题。
2. Agent 检索并组装证据。
3. Evidence Verifier 输出 `SUFFICIENT`。
4. Agent 基于证据回答并生成 claim-citation 映射。
5. Trace 记录证据覆盖、来源和终止原因 `ANSWERED`。

### 5.2 证据不足，纠正检索

1. Evidence Verifier 输出 `PARTIAL` 或 `INSUFFICIENT`，同时给出缺失主题。
2. Query Refiner 根据缺口生成差异化查询，排除已执行的 query fingerprint。
3. Agent 再执行 BM25/Dense、RRF、rerank 和证据判断。
4. 最多两轮；证据仍不足时进行部分回答或拒答。
5. UI 展示“发现证据缺口 → 补充检索 → 收敛结果”。

### 5.3 歧义澄清与任务恢复

1. 查询缺失会改变知识域、时间范围、目标实体或执行风险的关键槽位。
2. Agent 只提出一个信息增益最高的问题，保存 Pending Task。
3. Run 进入 `WAITING_CLARIFICATION`，不占用执行线程。
4. 用户短回复先进入 Slot Merger，而不是创建无关新任务。
5. 合并通过 schema 和业务校验后，从保存的恢复节点继续。

### 5.4 重复动作与无进展终止

1. 每次工具调用生成稳定 action fingerprint。
2. 同一动作第二次出现时向模型注入策略变更提示。
3. 第三次仍重复则拒绝执行并进入最终收敛。
4. 连续两轮 evidence、task ledger 和 state version 均无变化时判定无进展。
5. 最终回答说明已完成部分、阻塞点与建议动作。

### 5.5 节点级恢复

1. 每个可恢复节点完成后保存 checkpoint、task ledger 和 tool ledger。
2. 服务中断后运行标记 `INTERRUPTED`，保留最近安全节点。
3. 用户恢复时读取 checkpoint，不重复已经成功的 `REPLAY_SAFE` 工具。
4. `AT_MOST_ONCE` 工具只返回已保存结果；无法确认结果时要求人工确认。
5. 新尝试保留 `retryOfGenerationId`，同时记录 `resumedFromCheckpointId`。

## 6. 功能需求

### FR-01 Evidence Verifier

- 输出状态、置信度、已覆盖主题、缺失主题、冲突证据和建议动作。
- 优先使用确定性覆盖信号，必要时使用轻量模型判断。
- Verifier 失败时降级为 `PARTIAL`，不得把未知当充分。
- 最多允许两轮纠正检索，并对查询去重。

### FR-02 Clarification

- 规则决定是否必须澄清，模型只负责抽取槽位和生成友好问题。
- Pending Task 至少保存原问题、意图、槽位、缺失槽位、问题、选项、恢复节点、过期时间。
- 支持 `WAITING_CLARIFICATION / RESUMED / EXPIRED / CANCELLED`。
- 用户可忽略澄清并按明确默认值继续；高风险缺失字段不允许默认。

### FR-03 Loop Guard

- action fingerprint 基于工具名和规范化参数，不依赖 tool call ID。
- 重复动作采用 warning/hard 两级阈值。
- progress signature 至少包含证据 ID、完成 Todo、关键状态版本和错误分类。
- 所有终止必须持久化 `TerminalReason`，禁止只有自然语言错误。

### FR-04 Task Ledger

- 复杂任务保存 goal、constraints、acceptance criteria、todos、blockers、next action、plan version。
- Todo 状态限定为 `PENDING / IN_PROGRESS / COMPLETED / BLOCKED / SKIPPED`。
- 每个时刻最多一个 Todo 为 `IN_PROGRESS`。
- 模型回合前注入精简后的活跃 Ledger；上下文压缩或恢复后重新注入。

### FR-05 Tool Protocol

- Tool Spec 声明 risk level、effect、concurrency 和 replay policy。
- 支持 `REPLAY_SAFE / AT_MOST_ONCE / REQUIRES_APPROVAL`。
- tool request/result 作为原子协议对持久化。
- 恢复时修复 dangling tool request，禁止向模型发送不完整协议。

### FR-06 Evaluation

- 保留现有 Recall@K、MRR、nDCG 与延迟评测。
- 增加 clarification trigger、slot merge、tool selection、argument、trajectory、citation faithfulness 和 claim coverage 指标。
- 增加 duplicate action rate、no-progress termination rate、resume success rate 和 duplicate side-effect rate。
- 同一任务支持重复运行，记录成功率和 `pass^k`。
- 历史 bad case 可固化为回归集。

### FR-07 Observability/UI

- Agent Step 展示证据状态、补检索轮次、澄清等待、重复动作警告、恢复节点和终止原因。
- 用户可查看但不能看到隐藏思维链；只展示结构化决策摘要。
- 引用预览和历史引用映射必须保持兼容。

## 7. 非功能需求

### 正确性

- 低置信度证据不得静默生成确定答案。
- 用户补充信息不得跨会话、跨用户或跨组织合并。
- 恢复不得重复执行不可安全回放的副作用。

### 性能

- 确定性 Evidence Verifier P95 小于 50ms。
- 澄清等待不占用 Agent 工具线程。
- Loop Guard 和 Task Ledger 处理均为本地计算，不能新增远程依赖。

### 可用性

- Evidence、Memory、Eval 增强模块失败时主聊天可降级。
- 所有等待和终止状态对前端可解释。
- 服务重启后的悬空运行必须进入可恢复或明确失败状态。

### 安全

- 文档、记忆和工具结果被标记为低信任数据，不能覆盖系统指令。
- 工具参数和 checkpoint 内容在日志与前端输出前脱敏。
- 所有恢复请求校验 run 所属用户。

## 8. 验收指标

- 澄清触发与槽位合并存在独立测试集和混淆矩阵。
- 重复工具动作第三次执行率为 0。
- 无进展任务能够输出结构化终止原因，不依赖超时兜底。
- checkpoint 恢复测试中，成功步骤不会重复落账。
- `AT_MOST_ONCE` 工具重复副作用率为 0。
- 回答评测能输出 citation precision/recall 和 claim coverage。
- 至少提供一次关闭/开启 Evidence Refine、Loop Guard 和 Task Ledger 的消融结果。

## 9. 发布阶段

1. 文档与数据契约。
2. Evidence Verifier 与 Corrective Retrieval。
3. Clarification 与 Pending Task。
4. Loop Guard 与 Terminal Reason。
5. Task Ledger、Tool Replay 与节点恢复。
6. Agent Eval Lab 与 UI 完整展示。

