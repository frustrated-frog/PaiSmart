# Agent 长文本持久化修复设计

## 目标

修复 Agent 已成功生成回答、却因 MySQL `TINYTEXT` 容量不足而在最终持久化阶段失败的问题，并消除同类字段继续触发截断异常的风险。

## 根因

当前 Hibernate/MySQL 组合把 Agent 实体中的 `@Lob String` 建成了 `TINYTEXT`。真实数据库中 6 张 Agent 表的 17 个问题字段均只有 255 字节容量。中文回答、checkpoint JSON、工具参数与工具结果天然可能超过该限制。

## 方案

保留现有实体和数据流，只把所有 Agent 运行期非定长文本字段显式声明为 `columnDefinition = "LONGTEXT"`：

- `agent_runs`: `question`、`answer`、`error_message`
- `agent_checkpoints`: `state_json`
- `agent_memories`: `content`、`source_query`
- `agent_pending_tasks`: `original_query`、`known_slots_json`、`missing_slots_json`、`question`、`options_json`
- `agent_tool_calls`: `arguments_json`、`result_content`、`result_data_json`、`error_message`
- `agent_steps`: `detail`、`metadata_json`

开发环境继续使用现有 `spring.jpa.hibernate.ddl-auto=update`，重启时把已有列升级为 `LONGTEXT`。不截断已有数据，不改变业务接口，也不绕过异常处理。

## 测试与验收

1. 先添加反射回归测试，要求上述 17 个字段全部显式映射为 `LONGTEXT`，并观察测试在修改实体前失败。
2. 修改实体后让回归测试通过，并运行相关 Agent 服务测试与 Java 17 编译。
3. 重启后端，查询 `information_schema.COLUMNS`，确认 17 个字段均为 `longtext`。
4. 发送超过 255 字节的中文对话，确认 WebSocket 返回 `finished`，`agent_runs` 为 `COMPLETED`，回答完整落库，且 checkpoint 写入成功。

## 非目标

- 不修改模型、Token 配额或检索逻辑。
- 不改变前端展示。
- 不重构 Agent 运行架构。
