# PaiSmart 本地 JD Cloud LLM 接入设计

## 目标

让本地 PaiSmart 对话 Agent 使用 JD Cloud 提供的 OpenAI-compatible `GLM-5` 模型，同时保持改动最小，并避免将明文 API Key 推送到 GitHub。

## 方案

复用项目现有 `deepseek.api.*` 配置入口和 `LlmProviderRouter`。该路由已经按 OpenAI-compatible 协议请求 `${baseUrl}/chat/completions`，因此不新增 Provider、客户端或协议适配代码。

本机新增不纳入版本控制的 `src/main/resources/application-local.yml`，直接配置：

- `deepseek.api.url`: `https://modelservice.jdcloud.com/v1`
- `deepseek.api.model`: `GLM-5`
- `deepseek.api.key`: 本地明文密钥

启动后端时同时激活 `dev,local`。`local` 配置覆盖通用配置，Agent、查询规划和摘要生成继续统一通过现有 LLM 路由调用该模型。

## 安全边界

在 `.gitignore` 中忽略 `src/main/resources/application-local.yml`。仓库只提交忽略规则和本文档，不提交真实密钥。若密钥已在聊天、日志或其他外部系统中暴露，应在完成联调后轮换。

## 验证

1. 使用 `dev,local` 重启后端并确认 8081 健康响应。
2. 直连 JD Cloud 验证普通 Chat Completions。
3. 验证流式输出与 OpenAI tool-calling 响应结构。
4. 登录本地前端发送一轮真实 Agent 对话，确认模型回复、工具轨迹和错误日志正常。

## 非目标

- 不改 Embedding 服务。
- 不新增模型供应商管理页面或 JD Cloud 专用 SDK。
- 不把本地密钥写入 Git 历史。
