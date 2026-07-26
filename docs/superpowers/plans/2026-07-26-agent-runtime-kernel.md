# Agent Runtime Kernel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将知枢现有查询规划、RAG、ReAct、工具账本、checkpoint 与评测统一为可恢复、可验证的 Agent Runtime Kernel。

**Architecture:** 保留现有 Spring Boot 领域服务和 Vue 消息页，引入小型纯函数式 Runtime 组件负责工具选择、批次协议、预算和错误分类；`QueryPlan` 作为 Run 级单一事实贯穿 ChatHandler、ToolRegistry 和 AgenticRetrievalService。后续状态恢复与评测均从 MySQL 持久化事实投影，不依赖模型重新猜测状态。

**Tech Stack:** Java 17、Spring Boot 3.4、JUnit 5、Mockito、MySQL/JPA、Vue 3、TypeScript、Naive UI。

**Implementation Status (2026-07-26):** Tasks 1–6 已实现并分批提交；Task 7 的定向测试、前端构建、浏览器登录门禁验证、文档回写和分支推送已执行。完整后端测试的遗留 UploadService/H2 配置失败记录在交付说明中。

---

### Task 1: 单次 QueryPlan 与确定性工具路由

**Files:**
- Create: `src/main/java/com/yizhaoqi/smartpai/service/AgentToolSelector.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/AgentToolSelectorTest.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/rag/AgenticRetrievalService.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/AgentToolRegistry.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/ChatHandler.java`
- Modify: `src/test/java/com/yizhaoqi/smartpai/rag/AgenticRetrievalServiceTest.java`

- [ ] **Step 1: 写工具路由失败测试**

```java
@Test
void hidesRetrievalToolsWhenPlanDoesNotRequireRetrieval() {
    QueryPlan plan = plan(QueryPlan.Intent.CHAT, false);
    List<AgentTool> selected = selector.select(plan, tools("search_knowledge", "generate_summary", "submit_feedback"));
    assertThat(selected).extracting(AgentTool::name).containsExactly("submit_feedback");
}

@Test
void summaryIntentAvoidsDuplicateSearchTool() {
    QueryPlan plan = plan(QueryPlan.Intent.SUMMARY, true);
    List<AgentTool> selected = selector.select(plan, tools("search_knowledge", "generate_summary", "submit_feedback"));
    assertThat(selected).extracting(AgentTool::name)
            .contains("generate_summary", "submit_feedback")
            .doesNotContain("search_knowledge");
}
```

- [ ] **Step 2: 运行测试确认因类不存在而失败**

Run:

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.20/bin:$PATH \
mvn -q -Dtest=AgentToolSelectorTest test
```

Expected: FAIL，提示 `AgentToolSelector` 不存在。

- [ ] **Step 3: 实现最小工具选择器**

```java
@Service
public class AgentToolSelector {
    public List<AgentToolRegistry.AgentTool> select(QueryPlan plan, List<AgentToolRegistry.AgentTool> tools) {
        if (plan == null) return List.copyOf(tools);
        return tools.stream().filter(tool -> {
            if (!plan.retrievalRequired()) {
                return !Set.of("search_knowledge", "generate_summary").contains(tool.name());
            }
            if (plan.intent() == QueryPlan.Intent.SUMMARY) {
                return !"search_knowledge".equals(tool.name());
            }
            return true;
        }).toList();
    }
}
```

- [ ] **Step 4: 写 RAG 复用 QueryPlan 失败测试**

构造固定 `QueryPlan` 调用新重载 `retrieve(plan, userId, topK)`，验证 `QueryPlanningService.plan` 调用次数为 0，并验证原 `retrieve(String,...)` 仍调用一次 Planner。

- [ ] **Step 5: 实现 QueryPlan 重载并贯穿工具执行**

`AgenticRetrievalService.retrieve(String,...)` 只负责规划并委托：

```java
public RetrievalOutcome retrieve(String query, String userId, int topK) {
    return retrieve(queryPlanningService.plan(query, userId), userId, topK);
}
```

新增 `retrieve(QueryPlan plan, String userId, int topK)` 执行既有检索逻辑。`ChatHandler` 将同一 plan 传给 `AgentToolRegistry.executeTool`，检索和摘要工具调用新重载。

- [ ] **Step 6: 运行定向测试并提交**

```bash
mvn -q -Dtest='AgentToolSelectorTest,AgenticRetrievalServiceTest,QueryPlanningServiceTest' test
git add docs src/main src/test
git commit -m "feat: 统一Agent查询计划与工具路由"
```

### Task 2: Tool Batch 协议完整性

**Files:**
- Create: `src/main/java/com/yizhaoqi/smartpai/service/AgentToolBatchExecutor.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/AgentToolBatchExecutorTest.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/ChatHandler.java`

- [ ] **Step 1: 写提前终止失败测试**

```java
@Test
void closesEveryToolCallAfterFirstTerminalResult() {
    List<ToolCallDecision> calls = List.of(call("a"), call("b"), call("c"));
    BatchResult result = executor.execute(calls, call ->
            call.id().equals("a")
                    ? ToolOutcome.executed("partial", AgentTerminalReason.PARTIAL_EVIDENCE)
                    : ToolOutcome.executed("unexpected", null));

    assertThat(result.outcomes()).hasSize(3);
    assertThat(result.outcomes().get(0).status()).isEqualTo(EXECUTED);
    assertThat(result.outcomes().subList(1, 3)).allMatch(item -> item.status() == CANCELLED_BY_RUNTIME);
    assertThat(result.outcomes()).extracting(item -> item.call().id()).containsExactly("a", "b", "c");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run `mvn -q -Dtest=AgentToolBatchExecutorTest test`。

Expected: FAIL，提示 `AgentToolBatchExecutor` 不存在。

- [ ] **Step 3: 实现批次执行器**

执行器在首个 terminal outcome 后停止调用回调，为剩余调用生成包含 terminal reason 的取消 outcome；保留每个原始 tool call ID 和顺序。

- [ ] **Step 4: 接入 ChatHandler**

用批次执行结果替换当前 `for + break reactLoop`。所有 outcome 先转换为 ToolMessage 并追加，再决定继续下一轮、最终收敛或直接结束流式摘要。

- [ ] **Step 5: 运行回归并提交**

```bash
mvn -q -Dtest='AgentToolBatchExecutorTest,AgentContextBudgetServiceTest,AgentLoopGuardTest' test
git add src/main src/test
git commit -m "fix: 保证多工具调用协议完整闭合"
```

### Task 3: Runtime 硬预算与类型化工具错误

**Files:**
- Create: `src/main/java/com/yizhaoqi/smartpai/service/AgentRunBudgetController.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/service/AgentToolErrorClassifier.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/AgentRunBudgetControllerTest.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/AgentToolErrorClassifierTest.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/config/AgenticRagProperties.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/ChatHandler.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 写模型轮次、Token、时间和工具数预算失败测试**

测试 `beforeModelTurn` 和 `beforeToolCall` 分别返回 `ALLOWED`、`ROUND_BUDGET_EXHAUSTED`、`TOKEN_BUDGET_EXHAUSTED`、`TOOL_BUDGET_EXHAUSTED`，时间测试使用注入的 `LongSupplier` 单调时钟。

- [ ] **Step 2: 运行预算测试确认失败**

Run `mvn -q -Dtest=AgentRunBudgetControllerTest test`。

- [ ] **Step 3: 实现预算控制器并接入主循环**

每个 generation 注册独立 Usage；模型调用完成后累计 prompt/completion tokens。每次模型和工具执行前检查预算，结束时清理状态。

- [ ] **Step 4: 写错误分类失败测试**

验证 `IllegalArgumentException -> INVALID_ARGUMENT/non-retryable`、`TimeoutException -> TIMEOUT/retryable`、`RateLimitExceededException -> RATE_LIMITED/retryable`、其他异常 -> INTERNAL/non-retryable`。

- [ ] **Step 5: 实现错误分类并替换通用失败字符串**

ToolMessage 返回稳定 JSON：

```json
{"status":"FAILED","errorType":"TIMEOUT","retryable":true,"suggestedAction":"RETRY_WITH_BACKOFF","message":"工具执行超时"}
```

- [ ] **Step 6: 运行回归并提交**

```bash
mvn -q -Dtest='AgentRunBudgetControllerTest,AgentToolErrorClassifierTest,AgentToolExecutionGuardTest,AgentLoopGuardTest' test
git add src/main src/test
git commit -m "feat: 增加Agent硬预算与类型化错误"
```

### Task 4: 版本化 Runtime Snapshot 与真实审批状态

**Files:**
- Create: `src/main/java/com/yizhaoqi/smartpai/model/AgentRuntimeStateSnapshot.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/service/AgentRuntimeStateService.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/service/AgentToolApprovalService.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/AgentRuntimeStateServiceTest.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/AgentToolApprovalServiceTest.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/AgentRunService.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/AgentToolLedgerService.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/controller/ChatController.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/repository/AgentToolCallRepository.java`

- [ ] **Step 1: 写状态迁移和版本失败测试**

验证 stateVersion 单调递增、终态不可回到 RUNNING、schemaVersion 不兼容时拒绝恢复、WAITING_APPROVAL 可以通过新 attempt 恢复。

- [ ] **Step 2: 实现 Snapshot 和状态服务**

Snapshot 使用强类型 record；checkpoint 写入失败向调用方抛出 `AgentCheckpointException`，不再吞掉关键状态失败。

- [ ] **Step 3: 写审批归属与状态迁移失败测试**

验证只有 Run owner 能批准；仅 WAITING_APPROVAL 可转 APPROVED/REJECTED；重复相同决定幂等，不同决定冲突。

- [ ] **Step 4: 实现审批接口和重试入口**

Controller 校验 JWT userId，ApprovalService 更新 Tool Ledger，ChatHandler 从 source Run 创建新 attempt。`AgentRunService` 的 recoverable status 增加 WAITING_APPROVAL，但不计入普通 COMPLETED。

- [ ] **Step 5: 运行回归并提交**

```bash
mvn -q -Dtest='AgentRuntimeStateServiceTest,AgentToolApprovalServiceTest,AgentRunServiceTest,AgentToolLedgerServiceTest' test
git add src/main src/test docs/databases
git commit -m "feat: 完善Agent检查点与工具审批恢复"
```

### Task 5: 真实 Agent Eval Runner

**Files:**
- Create: `src/main/java/com/yizhaoqi/smartpai/evaluation/AgentTraceProjector.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/evaluation/AgentEvaluationRunner.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/AgentEvaluationDataset.java`
- Create: `src/main/java/com/yizhaoqi/smartpai/model/AgentEvaluationCase.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/evaluation/AgentTraceProjectorTest.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/evaluation/AgentEvaluationRunnerTest.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/controller/ChatController.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/evaluation/AgentEvaluationService.java`

- [ ] **Step 1: 写 Trace Projector 失败测试**

使用固定 Run、Step、ToolCall 和 checkpoint，验证自动投影 actual intent、trajectory、tools、terminal reason、token、latency 和 recovery signals。

- [ ] **Step 2: 实现 Trace Projector**

只读取服务端持久化事实；客户端请求仅包含 datasetId、version、sampleCount 和运行配置。

- [ ] **Step 3: 写 Runner 多次采样失败测试**

验证每个 case 运行 k 次，attemptIndex 连续，`passed` 由 expected rubric 与投影信号计算，而不是请求传入。

- [ ] **Step 4: 实现 Runner 和新 API**

保留旧计算器作为内部 Metric Engine，移除公共 API 对 caller-provided actual 的依赖。

- [ ] **Step 5: 运行回归并提交**

```bash
mvn -q -Dtest='AgentTraceProjectorTest,AgentEvaluationRunnerTest,AgentEvaluationServiceTest,RetrievalMetricsTest' test
git add src/main src/test docs/evaluation
git commit -m "feat: 建立真实Agent轨迹评测运行器"
```

### Task 6: Agent Control Center UI

**Files:**
- Modify: `frontend/src/typings/api.d.ts`
- Modify: `frontend/src/store/modules/chat/index.ts`
- Modify: `frontend/src/views/chat/modules/chat-message.vue`
- Create: `frontend/src/views/chat/modules/agent-runtime-panel.vue`
- Create: `frontend/src/views/chat/modules/agent-approval-card.vue`

- [ ] **Step 1: 定义前端状态契约**

新增 RuntimeSnapshot、BudgetUsage、TaskLedger、ApprovalRequest 类型，避免在组件中使用任意 metadata。

- [ ] **Step 2: 实现 Runtime Panel**

结构化展示 Plan、Budget、Evidence 和 Terminal Reason；默认折叠高级信息，运行中的当前步骤保持突出。

- [ ] **Step 3: 实现审批卡片**

批准和拒绝按钮调用审批接口；提交期间禁用重复操作；成功后绑定新的 generationId。

- [ ] **Step 4: 验证 TypeScript、ESLint 和浏览器流程**

```bash
cd frontend
pnpm typecheck
pnpm exec eslint src/views/chat/modules/chat-message.vue src/views/chat/modules/agent-runtime-panel.vue src/views/chat/modules/agent-approval-card.vue
```

浏览器验证 `/chat` 的普通问答、澄清、预算终止和审批恢复。

- [ ] **Step 5: 提交 UI**

```bash
git add frontend
git commit -m "feat: 增加Agent运行控制中心"
```

### Task 7: 全量验证、文档回写和推送

**Files:**
- Modify: `docs/agent-runtime-kernel-prd.md`
- Modify: `docs/agent-runtime-kernel-architecture.md`
- Modify: `docs/agentic-rag-interview-guide.md`

- [ ] **Step 1: 运行后端定向测试**

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.20/bin:$PATH \
mvn -q -Dtest='Agent*Test,QueryPlanningServiceTest,EvidenceVerifierServiceTest,RetrievalMetricsTest' test
```

- [ ] **Step 2: 运行完整后端测试并记录历史失败**

Run `mvn -q test`。如果仍只有已知 UploadService 两个失败和性能测试环境错误，在交付说明中准确列出；新增 Agent 测试不得失败。

- [ ] **Step 3: 运行前端检查和真实浏览器验证**

执行 typecheck、目标 ESLint，并检查网络响应、控制台、审批和恢复状态。

- [ ] **Step 4: 回写实现状态和面试讲解**

PRD 与架构文档中标记实际交付范围，面试指南补充单次规划、协议闭合、预算、恢复和真实评测的设计权衡。

- [ ] **Step 5: 提交并推送**

```bash
git add docs
git commit -m "docs: 完善Agent运行时实现与面试说明"
git push -u origin codex/agent-runtime-kernel
```
