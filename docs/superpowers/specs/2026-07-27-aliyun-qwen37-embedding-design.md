# 阿里云 Qwen3.7 Embedding 硬编码设计

## 目标

让 PaiSmart 在默认、开发和 Docker 配置下统一调用用户阿里云百炼工作区中的 `qwen3.7-text-embedding` 模型。

## 配置设计

- API Base URL 固定为工作区级 OpenAI 兼容根地址：`https://llm-33f9u44cujsi4b1d.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`。
- `EmbeddingClient` 已自动追加 `/embeddings`，配置中不重复加入该路径。
- 模型固定为 `qwen3.7-text-embedding`。
- 向量维度保持 `2048`，兼容当前应用配置与现有 Elasticsearch 向量映射。
- API Key 使用用户本次提供的值直接写入三个后端 YAML 配置，但不在设计或计划文档中重复记录。
- 同步更新被 Git 忽略的本地 `.env`；该文件由自定义 `DotenvEnvironmentPostProcessor` 以高于 YAML 的优先级加载，否则旧的本地值会覆盖硬编码配置。
- 保留现有 `ModelProviderConfigService` 和后台供应商管理逻辑；若本地数据库存在阿里云 Embedding 覆盖记录，则将该记录同步为相同配置，避免数据库优先级导致 YAML 未生效。

## 变更范围

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-docker.yml`
- 本地 `.env`（Git 忽略）
- 本地 MySQL 的 `model_provider_configs` 表（仅在存在对应覆盖记录时同步，不新增迁移文件）

不修改 Embedding 客户端、Elasticsearch 映射、前端或其他模型供应商。

## 验证

1. 检查三个 YAML 文件中的 Base URL、模型和维度一致。
2. 执行 `mvn -q -DskipTests compile`。
3. 使用工作区 Endpoint 发出单条最小 Embedding 请求，确认返回 HTTP 200、一个向量且维度为 2048；验证输出不得包含 API Key。
4. 若后端正在运行，再检查实际活动供应商配置；若未运行，遵守仓库约定不主动启动或重启。

## 安全说明

本设计按用户明确要求硬编码密钥。密钥将出现在受 Git 跟踪的 YAML 文件中，后续提交或推送会进入版本历史；建议完成当前使用后轮换密钥并恢复为环境变量注入。
