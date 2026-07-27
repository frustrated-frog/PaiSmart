# LiteParse 本地运行环境 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 安装并固定 PaiSmart 所需的 LiteParse CLI，使现有失败 PDF 能完成解析、Embedding 和 Elasticsearch 入库。

**Architecture:** LiteParse 作为用户级隔离工具由 `uv tool` 管理，Spring 配置使用其绝对路径，不依赖 IDE 的 `PATH`。业务代码保持不变，通过原始失败文档执行端到端验证。

**Tech Stack:** uv、LiteParse 2.8.0、Spring Boot、Kafka、MySQL、Elasticsearch、Vue/Vite

---

### Task 1: 安装并验证 LiteParse CLI

**Files:**
- External install: uv user tool directory

- [ ] **Step 1: 保留失败基线证据**

  Run: `command -v lit`

  Expected before installation: exit code 1，日志中的失败为 `Cannot run program "lit": error=2`。

- [ ] **Step 2: 安装固定版本**

  Run: `uv tool install 'liteparse==2.8.0'`

  Expected: 安装成功并提供 `lit` 可执行文件。

- [ ] **Step 3: 解析实际路径并验证命令**

  Run: `lit_bin_dir=$(uv tool dir --bin); "$lit_bin_dir/lit" --version; "$lit_bin_dir/lit" parse --help`

  Expected: 版本为 2.8.0，帮助中包含 `--format`、`--output`、`--max-pages`、`--dpi`、`--no-ocr` 和 `--quiet`。

### Task 2: 固定后端运行配置

**Files:**
- Modify locally: `.env`
- Modify: `.env.example`

- [ ] **Step 1: 写入本机绝对路径**

  在 `.env` 添加 `FILE_PARSING_LITEPARSE_COMMAND=<uv tool dir --bin 的实际输出>/lit`；绝对路径必须存在且可执行。

- [ ] **Step 2: 补充可复现说明**

  在 `.env.example` 的 LiteParse 段落加入 `uv tool install 'liteparse==2.8.0'`，保留生产环境配置绝对路径的说明。

- [ ] **Step 3: 静态验证**

  确认 `.env` 指向的文件可执行，`.env.example` 不包含当前用户目录或敏感信息，并运行 `git diff --check`。

### Task 3: 使用失败 PDF 验证解析

**Files:**
- Read: MinIO object `uploads/merged/6acf4d33675d0527f501310f187140e7`
- Temporary: system temporary directory

- [ ] **Step 1: 从 MinIO 下载原始 PDF 到临时目录**

  使用容器内已有对象存储凭据或当前有效预签名 URL读取对象，不修改 MinIO 内容。

- [ ] **Step 2: 使用项目同等参数解析**

  Run: `lit parse <pdf> --format json --output <json> --max-pages 1000 --dpi 150 --no-ocr --quiet`

  Expected: exit code 0，JSON `pages` 非空并含非空文本。

### Task 4: 让运行中后端加载配置

**Files:**
- Compile: backend project

- [ ] **Step 1: 使用 JDK 17 编译触发热部署**

  Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q -DskipTests compile`

  Expected: exit code 0。

- [ ] **Step 2: 核对运行时**

  检查 8081 监听 PID 与最新启动日志。若运行进程未加载绝对路径，停止旧进程后使用 JDK 17 启动唯一一个后端进程。

### Task 5: 重跑并验证完整摄取链路

**Files:**
- Runtime state: MySQL、Kafka、Elasticsearch

- [ ] **Step 1: 提交重试任务**

  通过已登录浏览器或项目内部受保护接口，为 MD5 `6acf4d33675d0527f501310f187140e7` 提交一次向量化重试。

- [ ] **Step 2: 等待终态并检查日志**

  等待 MySQL `vectorization_status` 进入 `COMPLETED` 或 `FAILED`。必须在 `COMPLETED` 时确认日志依次出现文件解析完成、Embedding 成功和向量化完成。

- [ ] **Step 3: 验证持久化与索引**

  MySQL 的 `actual_embedding_tokens`、`actual_chunk_count` 必须大于零；Elasticsearch 中该 MD5 的文档数必须大于零，抽样向量长度必须为 2048。

- [ ] **Step 4: 浏览器复核**

  打开 `http://127.0.0.1:9527/#/knowledge-base`，确认该 PDF 显示“向量化已完成”，且实际 Token/切片数可见。
