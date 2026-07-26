# 知枢 Agentic RAG 面试讲解与演示脚本

## 1. 一分钟项目介绍

知枢是我把传统企业 RAG 改造成可解释知识 Agent 的项目。核心不是简单接入大模型，而是完整实现查询规划、Multi-query、BM25/向量双路召回、RRF、可插拔重排、父子上下文、可恢复 Agent Runtime、治理型记忆和离线评测。每次运行都有 MySQL 事件账本与 Redis 热状态，前端可以看到检索各阶段的候选数、延迟、降级和 checkpoint 恢复谱系。

## 2. 最值得展开的五个技术点

### 2.1 为什么需要 BM25 + Dense

- BM25 对专有名词、编号、错误码和精确关键词更稳定。
- Dense 对同义表达、自然语言改写和语义相似更强。
- 两者分布不同，直接混加原始分数不可靠，所以使用只依赖 rank 的 RRF。
- 所有通道内部都执行相同的组织标签过滤，不能“召回后再过滤”。

### 2.2 RRF 为什么适合这里

RRF 分数为：

```text
score(d) = Σ 1 / (k + rank_i(d))
```

它不要求 BM25 与 cosine score 同尺度，能稳定融合异构排名。系统以 `fileMd5 + chunkId` 去重，默认 `k=60`，融合前保留 channel、queryType、rank 和 rawScore，便于消融分析。

### 2.3 Agent 为什么必须有界

生产 Agent 不能无限循环。本项目显式限制 ReAct round、工具调用数、检索轮数、上下文 Token、工具观察长度、并发和超时。工具执行使用独立线程池，并提供用户级并发隔离、熔断器、风险等级和参数 key 审计。这样才能回答“如何防止失控、雪崩和 Prompt 膨胀”。

### 2.4 为什么 MySQL 和 Redis 都需要

- Redis：低延迟活跃状态、流式内容、断线续传，设置 TTL。
- MySQL：Run/Step/Checkpoint 的长期事实来源，用于审计、指标和恢复。

服务重启后原进程中的 Future 无法恢复，因此悬空任务会被明确标记为 `INTERRUPTED`。重试创建新的 generation，并用 `retryOfGenerationId + attemptNumber` 形成谱系，避免重复计费和事件混写。

### 2.5 记忆为什么要治理

用户点踩不等于事实。裸点踩只进入 `PROPOSED`；有明确纠错内容且通过状态转换后才能成为 `ACTIVE`。每条记忆保存 owner、scope、来源、置信度、过期时间和状态，避免把模型幻觉自动写成长期事实，也避免跨租户污染。

### 2.6 Agent Runtime 为什么比“会调用工具”更有含金量

- QueryPlan 在 Run 内只生成一次，并决定工具可见性；模型不能通过 Prompt 绕过后端路由。
- 一次 Function Calling 返回多个 tool calls 时，首个终止结果之后的调用也会收到结构化取消 ToolMessage，保证协议 100% 闭合。
- Runtime 同时限制模型轮次、工具数、Prompt/Completion Token 和墙钟时间，达到上限后给出确定性部分回答。
- 高风险或结果未知的副作用进入 `WAITING_APPROVAL`，审批决定落 Tool Ledger，恢复时创建新 attempt 并复用同一动作指纹。
- Runtime Snapshot 使用 schemaVersion 与单调 stateVersion；终态不能回到 RUNNING，恢复只能创建新的运行谱系。
- 评测 API 不接收客户端提交的 actual/passed，而是从持久化 Run、Step、Tool Ledger 投影真实轨迹，再计算 trajectory、tool argument、恢复率、重复副作用率和 pass^k。

这套设计的核心回答是：LLM 只负责提出语义候选，Runtime 才是权限、状态、预算、协议和审计的最终裁决者。

## 3. 现场演示路线

### 正常检索演示

1. 提问包含精确术语和语义描述的知识问题。
2. 展开 `AGENT WORKFLOW`。
3. 展示查询意图与 ORIGINAL/LEXICAL/SEMANTIC/DECOMPOSED 变体。
4. 展示 BM25/Dense 候选数、RRF 前后结果数、重排耗时和 traceId。
5. 打开引用，核对文件、页码、命中 chunk 和父上下文。

### 故障降级演示

1. 暂停向量服务，证明 BM25 仍可返回结果，Trace 显示降级原因。
2. 暂停 reranker，证明切换本地轻量重排。
3. 配置无效主模型凭据和有效备用模型，证明 Provider Router 故障转移。
4. 所有模型均失败时，证明错误可操作、运行记录保留且不扣预留 Token。

### 恢复演示

1. 让一次任务失败或主动停止。
2. 刷新页面，证明失败步骤从 MySQL 恢复。
3. 点击“重新运行”，展示 `恢复 Agent 运行` 步骤。
4. 对比两个 generationId、attemptNumber 和 retry 谱系。
5. 查看 `RUN HEALTH` 中的恢复次数和成功率。

### 审批与运行控制演示

1. 让一个结果未知的 `AT_MOST_ONCE` 工具进入审批状态。
2. 展示审批卡片中的 replay policy、Tool Ledger ID 和风险说明。
3. 分别演示批准与拒绝：批准只执行原动作指纹，拒绝回放安全 ToolResult 并生成替代方案。
4. 展开 `RUNTIME CONTROL`，查看 intent、可见工具、轮次/工具/Token 预算、Evidence 与 TerminalReason。
5. 对比 source 与 retry generation，说明为什么恢复使用新 attempt 而不是篡改原运行。

## 4. 常见追问

### 为什么不直接用 LangChain/LangGraph

本项目重点是理解并实现关键边界：事件状态、预算、工具隔离、恢复、计费和评测。核心运行时自主实现，使行为可控且便于解释；未来若迁移到 LangGraph，持久化 schema、工具协议和 Trace 契约仍可复用。

### Cross-encoder 比 Embedding 好在哪里

Embedding 是双塔结构，可离线编码文档，适合大规模召回；Cross-encoder 对 query-document 联合编码，相关性更准但成本高，因此只用于 Top-N 重排。系统把它设计为可插拔增强组件，并设置超时和 fallback。

### 父子分块解决什么问题

小块更适合精准匹配，大块更适合给 LLM 完整语境。父子分块让子块负责召回，父块或邻近上下文负责生成，避免单一 chunk size 在召回率和上下文完整性之间二选一。

### 如何证明优化有效

不能只展示主观回答。项目提供固定 JSONL 黄金集和 Recall@K、MRR、nDCG、P95 门禁；通过 BM25-only、Dense-only、RRF、RRF+Multi-query、RRF+Rerank 的消融实验对比增益，同时观察零召回率和延迟成本。

### 如何防止越权

权限条件进入 BM25 和 Dense 每一个 ES 查询，而不是在融合后过滤；记忆和 Run API 同样按 user/scope 校验。评测集中应包含同名跨组织文档，权限泄漏率必须为 0。

## 5. 简历表述参考

> 设计并实现企业级 Agentic RAG：完成查询意图分类与 Multi-query，基于 Elasticsearch 独立执行 BM25/Dense 召回，使用 RRF 融合与 Cross-encoder 重排；构建有界 ReAct Runtime、工具熔断隔离、上下文压缩及多模型故障转移；以 MySQL Run/Step/Checkpoint + Redis 热状态实现跨进程恢复，并建立 Recall@K/MRR/nDCG 评测门禁和可视化 Trace。

不要填写尚未实测的百分比。完成黄金集实验后，用真实的 Recall、nDCG lift、P95 和故障恢复数据替换定性描述。

## 6. 消融实验记录模板

| Profile | Recall@10 | MRR@10 | nDCG@10 | Zero Recall | P95 | 备注 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| BM25 only |  |  |  |  |  | 精确词基线 |
| Dense only |  |  |  |  |  | 语义基线 |
| BM25 + Dense + RRF |  |  |  |  |  | 融合增益 |
| RRF + Multi-query |  |  |  |  |  | 改写增益与成本 |
| RRF + Multi-query + Rerank |  |  |  |  |  | 最终 profile |

同时记录数据集版本、代码 commit、Embedding 模型、reranker、Top-K、RRF k、硬件与执行时间，保证实验可复现。
