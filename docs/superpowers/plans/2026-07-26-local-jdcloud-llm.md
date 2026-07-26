# PaiSmart Local JD Cloud LLM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure the local PaiSmart backend to use JD Cloud GLM-5 for the conversation Agent without exposing its API key in Git.

**Architecture:** Reuse the existing OpenAI-compatible `deepseek.api.*` configuration seam and `LlmProviderRouter`. Store the concrete endpoint, model, and user-supplied credential in an ignored Spring `local` profile, then run the backend with the composite `dev,local` profile.

**Tech Stack:** Spring Boot 3, YAML configuration, WebClient, OpenAI-compatible Chat Completions

---

### Task 1: Protect the local model configuration

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Add the exact local profile path to Git ignore**

```gitignore
# Local Spring profile may contain developer credentials.
/src/main/resources/application-local.yml
```

- [ ] **Step 2: Verify the ignore rule**

Run:

```bash
git check-ignore -v src/main/resources/application-local.yml
```

Expected: output identifies the new `.gitignore` rule.

- [ ] **Step 3: Commit the safe repository change**

```bash
git add .gitignore
git commit -m "chore: 隔离本地模型密钥配置"
```

### Task 2: Configure JD Cloud locally

**Files:**
- Create locally, ignored: `src/main/resources/application-local.yml`

- [ ] **Step 1: Create the local Spring profile**

Write the credential supplied by the user in the current task directly to `deepseek.api.key`; do not copy it into this tracked implementation plan.

```yaml
deepseek:
  api:
    url: https://modelservice.jdcloud.com/v1
    key: "<the exact credential supplied by the user in this task>"
    model: GLM-5
```

- [ ] **Step 2: Confirm the file remains untracked**

Run:

```bash
git status --short --ignored src/main/resources/application-local.yml
```

Expected: `!! src/main/resources/application-local.yml`.

### Task 3: Validate the provider contract

**Files:**
- No repository changes

- [ ] **Step 1: Test non-streaming Chat Completions**

Send `model=GLM-5`, a one-token-limit user message, and `stream=false` to `https://modelservice.jdcloud.com/v1/chat/completions`.

Expected: HTTP 200 with an assistant message or a provider-valid completion response.

- [ ] **Step 2: Test streaming**

Send a minimal request with `stream=true`.

Expected: HTTP 200 and SSE `data:` frames ending with `[DONE]`.

- [ ] **Step 3: Test tool calling**

Send one harmless function definition and a prompt that requires it.

Expected: HTTP 200 with a `tool_calls` response compatible with the project's ReAct parser.

### Task 4: Run and verify PaiSmart

**Files:**
- No repository changes

- [ ] **Step 1: Stop only the existing backend process on port 8081**

Resolve the exact PID with:

```bash
lsof -tiTCP:8081 -sTCP:LISTEN
```

Terminate only that PID and leave the frontend process untouched.

- [ ] **Step 2: Start the backend with both profiles**

Run:

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.20/bin:$PATH \
mvn spring-boot:run -Dspring-boot.run.profiles=dev,local
```

Expected: `Started SmartPaiApplication` and Tomcat listening on 8081.

- [ ] **Step 3: Compile and check both services**

Run:

```bash
mvn -q -DskipTests compile
lsof -nP -iTCP:8081 -sTCP:LISTEN
lsof -nP -iTCP:9527 -sTCP:LISTEN
curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:9527/
curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/api/v1/chat/active-generation
```

Expected: compile exit 0, both ports listening, frontend HTTP 200, and protected backend endpoint HTTP 403 without a token.

- [ ] **Step 4: Verify the real Agent path**

Use the existing authenticated frontend session to send a harmless chat message. Confirm in browser network traffic and backend logs that the request reaches the Agent route and receives model output without provider authentication, model-name, streaming, or tool-call parsing errors.
