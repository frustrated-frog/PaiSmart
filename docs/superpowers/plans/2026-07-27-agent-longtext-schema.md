# Agent Longtext Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every unbounded Agent runtime text/JSON field is stored as MySQL `LONGTEXT` so generated answers and execution state persist without truncation.

**Architecture:** Keep the existing JPA entities and Hibernate schema-update workflow. Add a reflection contract test covering all 17 unbounded fields, then make each mapping explicit with `columnDefinition = "LONGTEXT"`; restart and validate the physical MySQL schema plus a real long-answer WebSocket run.

**Tech Stack:** Java 17, Spring Boot 3.4, Jakarta Persistence, JUnit 5, MySQL 8, WebSocket

---

### Task 1: Add the failing mapping contract

**Files:**
- Create: `src/test/java/com/yizhaoqi/smartpai/model/AgentLongTextSchemaMappingTest.java`

- [ ] **Step 1: Write a reflection test covering all 17 Agent long-text fields**

Create a parameterized list of entity class and field name pairs. For every pair, read `@Column.columnDefinition()` and assert it equals `LONGTEXT`.

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
runtime_java_home=$(/usr/libexec/java_home -v 17)
JAVA_HOME="$runtime_java_home" mvn -q -Dtest=AgentLongTextSchemaMappingTest test
```

Expected: failure showing an empty column definition for the first existing `@Lob` field.

### Task 2: Make Agent long-text mappings explicit

**Files:**
- Modify: `src/main/java/com/yizhaoqi/smartpai/model/AgentRun.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/model/AgentCheckpoint.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/model/AgentMemory.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/model/AgentPendingTask.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/model/AgentToolCall.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/model/AgentStep.java`
- Test: `src/test/java/com/yizhaoqi/smartpai/model/AgentLongTextSchemaMappingTest.java`

- [ ] **Step 1: Add `columnDefinition = "LONGTEXT"` to every field listed in the design**

Preserve the existing `name` and `nullable` attributes. Do not change field names, Java types, or service behavior.

- [ ] **Step 2: Run the mapping test and verify GREEN**

Run the Task 1 Maven command again.

Expected: exit code 0 with zero failures.

- [ ] **Step 3: Run related regression tests and compile**

```bash
runtime_java_home=$(/usr/libexec/java_home -v 17)
JAVA_HOME="$runtime_java_home" mvn -q -Dtest=AgentRunServiceTest,AgentRuntimeStateServiceTest,AgentMemoryServiceTest,AgentPendingTaskServiceTest,AgentToolLedgerServiceTest test
JAVA_HOME="$runtime_java_home" mvn -q -DskipTests compile
```

Expected: both commands exit 0.

### Task 3: Migrate and verify the running system

**Files:**
- No additional source changes

- [ ] **Step 1: Restart only the backend on port 8081**

Resolve the listening PID, verify it is `SmartPaiApplication`, terminate it gracefully, then start with Java 17 using `mvn spring-boot:run`. Keep the frontend on 9527 running.

- [ ] **Step 2: Verify the physical MySQL schema**

Query `information_schema.COLUMNS` for the six Agent tables and confirm all 17 target columns report `longtext`.

- [ ] **Step 3: Run a real long-answer chat**

Authenticate as the local admin, open `/chat/{token}`, send a prompt requiring a Chinese response longer than 255 bytes, and wait for the `completion` WebSocket event.

Expected: `status=finished`, answer length greater than 100 Chinese characters, and no error event.

- [ ] **Step 4: Verify persistence**

Query the newest `agent_runs` row and its checkpoints.

Expected: run status `COMPLETED`, full answer byte length greater than 255, non-null token counts, at least one checkpoint, and no new `Data truncation` log after restart.
