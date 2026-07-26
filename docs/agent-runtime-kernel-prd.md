# 知枢 Agent Runtime Kernel PRD

## 1. 文档信息

- 产品：知枢（PaiSmart）企业知识 Agent
- 版本：2.0
- 开发分支：`codex/agent-runtime-kernel`
- 目标：把现有 Agentic RAG、ReAct、工具账本和运行轨迹统一到可恢复、可验证的运行时内核

## 2. 背景

知枢已经实现查询意图分类、Multi-query、BM25/Dense 双路召回、RRF、重排、纠正检索、有状态澄清、循环守卫、工具回放和轨迹评测。当前主要问题不再是“缺少算法组件”，而是控制平面与执行平面没有形成一致协议：查询计划会重复生成，计划没有确定性约束工具路由，多工具提前终止可能留下不完整协议，预算和错误恢复仍依赖分散判断，审批与 checkpoint 也没有形成真正的暂停—恢复状态机。

本期建设 Agent Runtime Kernel，使一次 Agent Run 从理解、规划、执行到评测都由同一份状态和事件事实驱动。

## 3. 产品目标

### 3.1 用户目标

- 普通问答只进行一次查询规划，避免重复等待。
- Agent 能解释当前目标、计划、证据状态、预算和终止原因。
- 多个工具动作中的某一步被终止时，系统仍能安全收敛回答。
- 高风险动作可以暂停等待审批，审批后从安全节点继续。
- 服务中断后不重复执行已经成功或结果未知的副作用。

### 3.2 工程目标

- `QueryPlan` 成为当前 Run 的单一事实来源。
- 模型只产生候选动作，Runtime 负责路由、预算、权限、并发和终止。
- 每一个 assistant tool request 都有对应的 tool result 或结构化取消结果。
- Agent 状态使用版本化快照持久化，恢复时校验 schema 和状态迁移。
- 评测系统从真实运行 trace 自动生成 actual signals，不接受调用方伪造实际结果。

### 3.3 秋招展示目标

项目应能通过代码、演示和指标回答：

1. 为什么 Query Planning 必须是控制平面，而不只是一个 Prompt？
2. 如何保证 Function Calling 的协议完整性？
3. 如何同时控制模型轮数、工具数、Token、时间和错误重试预算？
4. checkpoint、幂等键、tool ledger 和审批分别解决什么故障？
5. 如何从真实 trace 评测 trajectory、tool use、恢复和 `pass^k`？

## 4. 范围

### 4.1 本期范围

- 统一 Run Context、QueryPlan 和工具可见性。
- QueryPlan 在 RAG 链路中复用，消除重复规划。
- Tool Batch 协议闭合和结构化取消。
- Runtime Budget：轮数、工具数、Token、运行时间。
- 类型化 Tool Error 与确定性恢复决策。
- 版本化 checkpoint 和状态迁移校验。
- `WAITING_APPROVAL / APPROVED / REJECTED / RESUMED` 完整审批协议。
- 真实 Agent Eval Runner、Trace Projector 和版本化 Golden Set。
- 前端 Agent Control Center：计划、预算、审批、证据和终止原因。

### 4.2 非目标

- 不引入默认 Multi-Agent、Swarm 或通用工作流平台。
- 不替换现有 BM25、向量、RRF 和父子块实现。
- 不展示隐藏思维链，只展示结构化决策摘要。
- 不允许模型绕过后端权限和审批策略。
- 不在本期引入 GraphRAG；是否引入由检索评测决定。

## 5. 核心用户旅程

### 5.1 单次规划的知识问答

1. Query Planner 输出 `QueryPlan`。
2. Runtime 根据 intent 和 `retrievalRequired` 筛选工具并注入计划摘要。
3. `search_knowledge` 复用同一 `QueryPlan` 执行多路检索。
4. Evidence Verifier 决定回答、补检索或拒答。
5. Run Trace 保留 planning、retrieval、tool 和 terminal event。

### 5.2 多工具提前终止

1. 模型一次返回多个 tool calls。
2. Runtime 顺序或并行调度允许执行的调用。
3. 某个调用触发证据不足、审批等待或预算终止。
4. 尚未执行的调用写入 `CANCELLED_BY_RUNTIME` ToolMessage。
5. 所有 tool call ID 闭合后，模型才能收到最终收敛指令。

### 5.3 审批和恢复

1. 高风险工具进入 `WAITING_APPROVAL`，保存工具参数摘要、风险说明和恢复节点。
2. 用户在前端批准或拒绝。
3. 批准后创建新的 Run attempt，复用原计划和 checkpoint。
4. 拒绝后生成确定性的 ToolResult，并让 Agent 给出替代方案。
5. 审批决定、操作者和时间进入审计轨迹。

### 5.4 自动回归评测

1. 评测 Runner 加载带版本的 Golden Set。
2. 每个 case 真正运行 Agent，多次采样共享隔离数据集。
3. Trace Projector 从 Run、Step、Tool Ledger 和引用映射生成 actual signals。
4. 确定性指标与 LLM Judge 分开计算。
5. 与 baseline 比较，质量门禁失败时输出具体退化模块。

## 6. 功能需求

### FR-01 Runtime Context

- 每个 Run 保存 `schemaVersion`、QueryPlan、currentNode、status、budgets、taskLedger、toolCursor、terminalReason 和 stateVersion。
- QueryPlan 在同一个 Run 中只生成一次；恢复时直接读取快照。
- Runtime Context 不能只存在 JVM 内存，关键节点必须持久化。

### FR-02 Tool Routing

- `retrievalRequired=false` 时，不向模型暴露知识检索和摘要工具。
- `SUMMARY` 意图优先暴露聚合摘要工具，禁止重复执行“先 search 后 summary 内部再 search”的等价链路。
- 工具筛选、风险和参数 schema 由 Java 校验，不依赖 Prompt 自律。

### FR-03 Tool Protocol

- 每个 tool call 必须产生 `SUCCESS / FAILED / REUSED / CANCELLED / WAITING_APPROVAL` 之一。
- 批次提前停止时，剩余 tool call 产生结构化取消结果。
- 上下文压缩不得拆散 assistant tool calls 与完整 tool result 集合。

### FR-04 Runtime Budget

- 支持 `maxModelTurns`、`maxToolCalls`、`maxPromptTokens`、`maxCompletionTokens`、`maxRunSeconds`。
- 每次模型调用和工具执行前由 Runtime 硬检查。
- 达到预算后持久化准确的 TerminalReason，并生成可解释的部分结果。

### FR-05 Typed Error

- Tool Error 至少区分 `INVALID_ARGUMENT / PERMISSION_DENIED / NOT_FOUND / RATE_LIMITED / TIMEOUT / UPSTREAM / INTERNAL`。
- 每种错误声明 retryable、用户提示、建议动作和退避时间。
- 重试预算绑定 `errorType + actionFingerprint`，不能无限更换自然语言错误继续执行。

### FR-06 Checkpoint/Approval

- checkpoint 带 schemaVersion、stateVersion 和 currentNode。
- checkpoint 写入失败时不得静默推进到不可恢复节点。
- 审批状态必须有后端接口、用户归属校验、前端交互和恢复入口。
- 幂等约束必须在数据库层处理并发冲突，不能依赖单 JVM `synchronized`。

### FR-07 Evaluation

- Agent Eval API 接收 dataset/version/run configuration，不接收调用方提交的 actual trajectory 或 passed。
- Trace Projector 自动计算 intent、clarification、tool selection、arguments、citations、terminal reason、latency、token 和恢复信号。
- 同一 case 支持 k 次运行，输出 `pass@1` 与 `pass^k`。
- 回归报告能定位 planning、retrieval、tool、state、answer 五类失败。

### FR-08 UI

- 展示结构化任务计划、依赖和当前步骤。
- 展示剩余轮数、工具数、Token 和运行时间预算。
- 展示审批卡片及风险说明。
- 展示 RAG pipeline、证据状态、降级和最终终止原因。
- 不暴露模型隐藏思维链和敏感工具参数。

## 7. 非功能需求

- 单次规划相对旧链路减少一次 Query Planner LLM 调用。
- Tool Batch 协议完整率为 100%。
- `AT_MOST_ONCE` 工具重复副作用率为 0。
- Runtime 本地守卫 P95 小于 10ms。
- 所有新增状态迁移有单元测试；关键恢复链路有数据库集成测试。
- 现有引用预览、多租户权限、聊天历史和流式输出保持兼容。

## 8. 验收标准

- 多 tool calls 中第一个触发终止后，后续调用均收到 `CANCELLED_BY_RUNTIME` ToolMessage。
- 一个正常知识问答只调用一次 Query Planner。
- `retrievalRequired=false` 的 QueryPlan 不会暴露 `search_knowledge`。
- Token 或时间预算达到上限后，不再发起下一次模型调用或工具调用。
- 审批 Run 不会被统计为普通 `COMPLETED`。
- Eval Runner 的 actual signals 全部来自持久化 trace。
- Agent/RAG 定向测试全部通过；完整测试中的历史失败单独记录，不与本期回归混淆。

## 9. 交付阶段

1. Runtime Context、单次规划和工具路由。
2. Tool Batch 协议与 Runtime Budget。
3. Typed Error、版本化 checkpoint 和审批恢复。
4. Eval Runner 与 Trace Projector。
5. Agent Control Center UI 和端到端验证。
