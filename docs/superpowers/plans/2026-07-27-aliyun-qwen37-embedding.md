# 阿里云 Qwen3.7 Embedding 硬编码实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 PaiSmart 的三个后端运行配置统一切换到用户阿里云工作区的 `qwen3.7-text-embedding`，并验证 2048 维向量调用成功。

**Architecture:** 沿用现有 OpenAI 兼容 `EmbeddingClient`，只替换 Spring YAML 中的工作区 Base URL、用户提供的 API Key 和模型名。保留 2048 维与现有 Elasticsearch 映射兼容，并在本地数据库存在供应商覆盖记录时同步该记录。

**Tech Stack:** Spring Boot 3.4、YAML、WebClient、MySQL、阿里云百炼 OpenAI-compatible Embeddings API

---

### Task 1: 固定三个运行配置

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/resources/application-docker.yml`
- Modify locally: `.env`（Git 忽略）

- [ ] **Step 1: 更新默认配置**

  将 `embedding.api` 设置为工作区 Base URL、会话中用户明确提供的 API Key、`qwen3.7-text-embedding` 和 `2048`。密钥不得复制进本计划或命令输出。

- [ ] **Step 2: 更新开发配置**

  使用与默认配置完全相同的四项值，确保 `.env` 中旧的 `EMBEDDING_API_MODEL` 不再覆盖硬编码结果。

- [ ] **Step 3: 更新 Docker 配置**

  使用与默认配置完全相同的四项值，保证不同 profile 行为一致。

- [ ] **Step 4: 同步本地 `.env`**

  将 `EMBEDDING_API_URL`、`EMBEDDING_API_MODEL` 和 `EMBEDDING_API_KEY` 同步为本次配置，避免高优先级的 `paismartDotenv` 属性源覆盖 YAML。

- [ ] **Step 5: 静态校验**

  Run: `git diff --check && rg -n 'qwen3\.7-text-embedding|llm-33f9u44cujsi4b1d' src/main/resources/application*.yml`

  Expected: 无 whitespace 错误，三个文件各包含相同模型名和工作区 Base URL。

### Task 2: 同步可选数据库覆盖配置

**Files:**
- Modify if present: MySQL table `model_provider_configs`, row `config_scope='embedding' AND provider_code='aliyun'`

- [ ] **Step 1: 检查本地数据库连通性及覆盖记录**

  从项目 `.env` 安全读取数据库连接参数，不使用 `source .env`，查询时不打印密码或 API Key。

- [ ] **Step 2: 同步已有记录**

  仅当记录存在时，将 Base URL、模型、维度和加密后的用户 API Key 同步为本次配置；若没有记录则不插入，因为 YAML 默认配置已足够。

- [ ] **Step 3: 复查非敏感字段**

  查询 provider、模型、维度和 Base URL，确认数据库不会覆盖回旧模型；不读取或显示密钥密文。

### Task 3: 编译和真实接口验证

**Files:**
- Verify: backend source and runtime configuration

- [ ] **Step 1: 编译后端**

  Run: `mvn -q -DskipTests compile`

  Expected: exit code 0。

- [ ] **Step 2: 调用真实 Embeddings Endpoint**

  向 `https://llm-33f9u44cujsi4b1d.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/embeddings` 发送单条文本，模型为 `qwen3.7-text-embedding`，`dimension` 为 `2048`；密钥通过进程内变量传入且不输出。

  Expected: HTTP 200，响应 `data` 长度为 1，首个 `embedding` 长度为 2048。

- [ ] **Step 3: 检查运行时状态**

  检查 8081 和 9527 端口。若后端未运行，不主动启动；若正在运行，检查活动供应商的非敏感字段并确认热加载状态。

- [ ] **Step 4: 汇总安全影响**

  明确指出受 Git 跟踪的 YAML 已包含密钥、配置是否已通过真实调用，以及是否存在需用户自行重启/轮换密钥的事项。
