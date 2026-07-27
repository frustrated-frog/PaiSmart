# 知枢全项目视角面试文档改写 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 20 道模拟面试题重写为全项目视角下的深度参考答案，同时保持真实的面试问答形式。

**Architecture:** 文档以面试官问题为一级内容单元，每个答案从全系统业务与架构切入，再深入具体模块、数据流、异常路径和设计取舍。题目覆盖项目全链路，复杂链路使用嵌入式 Mermaid 图辅助口述。

**Tech Stack:** Markdown、Mermaid、Spring Boot、Vue 3、MySQL、Redis、Elasticsearch、Kafka、MinIO、LLM/Embedding 服务。

---

### Task 1: 重组 20 道面试问题

**Files:**
- Modify: `docs/career/2026-07-27-interview-20-questions.md`

- [x] **Step 1: 保留面试问答形式并重新分配题目覆盖面**

将题目组织为 20 个面试官真实可能提出的问题，覆盖项目定位、总体架构、文档处理、检索生成、Agent Runtime、多租户、安全、存储一致性、可靠性、评测和系统演进，但不增加项目讲解式章节。

- [x] **Step 2: 检查题号连续性**

Run:

```bash
sed -n 's/^## \([0-9][0-9]*\)\..*/\1/p' docs/career/2026-07-27-interview-20-questions.md
```

Expected: 从 `1` 到 `20` 连续输出，且没有重复或缺失。

### Task 2: 用全项目视角重写详细答案

**Files:**
- Modify: `docs/career/2026-07-27-interview-20-questions.md`

- [x] **Step 1: 重写第 1—10 题**

每道答案采用多段文字，从项目业务目标和端到端架构出发，说明对应模块的职责、上下游关系、正常路径、异常路径、技术选型、替代方案和当前边界。

- [x] **Step 2: 重写第 11—20 题**

保持与前半部分一致的深度，突出数据一致性、安全隔离、运行时治理、故障恢复、可观测性和演进判断，避免只复述近期提交。

- [x] **Step 3: 核对实现事实**

使用 `rg` 在 `src/main/java`、`frontend/src`、`src/main/resources` 和现有架构文档中核对类名、组件职责、配置默认值与降级行为。没有实测结果时只描述评测方法，不编造数值。

### Task 3: 增加必要图示并完成格式验收

**Files:**
- Modify: `docs/career/2026-07-27-interview-20-questions.md`

- [x] **Step 1: 嵌入复杂链路 Mermaid 图**

图示至少覆盖总体架构、文档摄入、完整问答、运行时恢复、权限过滤、存储职责和评测闭环。流程图使用分层子图；状态图使用 ASCII 状态别名；时序图闭合全部控制块。

- [x] **Step 2: 执行结构检查**

Run:

```bash
rg -c '^## [0-9]+\.' docs/career/2026-07-27-interview-20-questions.md
rg -c '^```mermaid$' docs/career/2026-07-27-interview-20-questions.md
rg -c '^```' docs/career/2026-07-27-interview-20-questions.md
```

Expected: 问题数量为 `20`；Mermaid 块数量与设计一致；全部代码围栏成对闭合。

- [x] **Step 3: 执行文本质量检查**

Run:

```bash
rg -n '[[:blank:]]+$|待补充内容|在此填写' docs/career/2026-07-27-interview-20-questions.md
```

Expected: 无输出。
