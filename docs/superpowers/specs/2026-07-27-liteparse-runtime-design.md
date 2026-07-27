# LiteParse 本地运行环境设计

## 目标

修复 PDF 摄取任务因找不到 `lit` 可执行文件而在解析阶段失败的问题，并验证现有 PDF 从解析、切块、Embedding 到 Elasticsearch 索引的完整链路。

## 方案

- 使用 `uv tool` 在当前用户目录安装固定版本 `liteparse==2.8.0`，避免污染系统 Python 和项目依赖。
- 使用 `uv tool dir --bin` 得到实际可执行目录，并把 `FILE_PARSING_LITEPARSE_COMMAND` 配置为 `lit` 的绝对路径，避免 IntelliJ、终端和后台进程的 `PATH` 差异。
- 同步更新 `.env.example` 的说明，保留新环境的可复现安装命令；本地 `.env` 写入当前机器的绝对路径。
- 保持 `FILE_PARSING_LITEPARSE_OCR_ENABLED=false`。当前测试 PDF 带文本层，不引入 Tesseract/OCR 这一额外变量。
- 不修改 PDF 解析、切块、Embedding 或 Elasticsearch 业务代码。

## 运行时生效

LiteParse 路径属于 Spring 启动配置。安装并修改配置后先编译触发仓库约定的 IDE 热部署；若运行进程没有加载新路径，再显式重启后端。不得留下两个竞争 `8081` 端口的进程。

## 验证

1. `lit --version` 和 `lit --help` 正常。
2. 使用项目同等参数 `--format json --max-pages 1000 --dpi 150 --no-ocr --quiet` 解析失败记录对应的原始 PDF，并确认输出含非空 `pages`。
3. 重新提交原文件的异步向量化任务。
4. 确认 MySQL 状态为 `COMPLETED`，实际切片数和 Embedding Token 大于零。
5. 确认 Elasticsearch 中该 `fileMd5` 的文档数量大于零且向量维度为 2048。
6. 在真实浏览器中确认知识库页面显示“向量化已完成”。

## 失败处理

- 若安装失败，保留完整安装错误并停止，不尝试同名 LLVM `lit`。
- 若 CLI 解析失败，先依据 CLI stderr 诊断依赖或参数，不进入 Embedding 调试。
- 若解析成功而异步任务失败，再按 Kafka、Embedding、Elasticsearch 的顺序定位，不混淆各阶段错误。
