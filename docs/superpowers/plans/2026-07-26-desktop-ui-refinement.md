# 知枢桌面端 UI 与交互改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将知枢桌面端改造成信息层级清晰、主题一致、能突出 Agentic RAG 技术路径的知识智能控制台。

**Architecture:** 保留 Vue 3、Naive UI、Pinia 和既有路由结构，通过纯函数整理菜单和运行态语义，通过页面级组件重构视觉层级。所有主题颜色从统一 Token 派生，业务 API 与状态管理保持不变。

**Tech Stack:** Vue 3、TypeScript、Naive UI、UnoCSS、SCSS、Node Test Runner、tsx、Vite

---

## 文件结构

- `frontend/src/styles/app.css`：增加产品级字体、Focus、Surface 和减少动画规则。
- `frontend/src/theme/settings.ts`：调整主色、Surface 和阴影 Token。
- `frontend/src/layouts/modules/global-menu/menu-groups.ts`：把权限过滤后的扁平菜单映射为桌面分组。
- `frontend/src/layouts/modules/global-menu/menu-groups.test.ts`：验证菜单分组、权限缺项和未知菜单行为。
- `frontend/src/layouts/modules/global-menu/modules/vertical-menu.vue`：使用分组菜单并补充侧栏产品说明。
- `frontend/src/layouts/modules/global-header/index.vue`：展示页面标题、描述和上下文状态。
- `frontend/src/views/chat/modules/agent-runtime.ts`：集中计算 Agent 运行摘要和终态。
- `frontend/src/views/chat/modules/agent-runtime.test.ts`：验证完成、运行中和失败状态。
- `frontend/src/views/chat/modules/agent-runtime-panel.vue`：实现摘要/详情两层运行面板。
- `frontend/src/views/chat/modules/conversation-sidebar.vue`：折叠运行健康度并改善可访问交互。
- `frontend/src/views/chat/modules/chat-list.vue`：改造空状态、筛选区和消息阅读宽度。
- `frontend/src/views/chat/index.vue`：统一聊天工作区 Surface。
- `frontend/src/views/_builtin/login/modules/remembered-login.ts`：生成仅包含用户名的安全记忆数据。
- `frontend/src/views/_builtin/login/modules/remembered-login.test.ts`：证明密码不会进入持久化对象。
- `frontend/src/views/_builtin/login/modules/pwd-login.vue`：接入安全记忆逻辑并优化表单文案。
- `frontend/src/views/_builtin/login/index.vue`：实现桌面双栏登录与检索执行轨道。
- `frontend/src/views/model-provider/index.vue`：修复主题对比度并重组配置层级。
- `frontend/src/views/knowledge-base/index.vue`：增加引导型空状态和处理链路。

### Task 1: 主题与导航基础

- [ ] **Step 1: 编写菜单分组失败测试**

创建 `menu-groups.test.ts`，构造包含 `chat`、`knowledge-base`、`model-provider`、`user` 和未知菜单的输入，断言输出按“核心工作台 / Agent 治理 / 组织与系统 / 其他”分组且不丢失原对象。

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```bash
cd frontend
node --import tsx --test src/layouts/modules/global-menu/menu-groups.test.ts
```

Expected: 因 `menu-groups.ts` 不存在而失败。

- [ ] **Step 3: 实现菜单分组与视觉 Token**

创建 `menu-groups.ts` 导出：

```ts
export interface DesktopMenuGroup {
  key: string;
  label: string;
  children: App.Global.Menu[];
}

export function groupDesktopMenus(menus: App.Global.Menu[]): DesktopMenuGroup[];
```

随后修改主题、全局样式、侧栏和页头。分组只消费已经过权限过滤的 `routeStore.menus`，不自行判断角色。

- [ ] **Step 4: 运行单测、类型检查和定向 Lint**

Run:

```bash
cd frontend
node --import tsx --test src/layouts/modules/global-menu/menu-groups.test.ts
./node_modules/.bin/vue-tsc --noEmit --skipLibCheck
./node_modules/.bin/eslint src/theme/settings.ts src/layouts/modules/global-header/index.vue src/layouts/modules/global-menu/menu-groups.ts src/layouts/modules/global-menu/menu-groups.test.ts src/layouts/modules/global-menu/modules/vertical-menu.vue
```

Expected: 新增测试通过；类型检查除仓库已有 Playwright 依赖问题外无新增错误；定向 Lint 退出码为 0。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src/styles/app.css frontend/src/theme/settings.ts frontend/src/layouts/modules/global-header/index.vue frontend/src/layouts/modules/global-menu
git commit -m "feat: 重塑桌面端导航与主题层级"
git push -u origin codex/desktop-ui-refinement
```

### Task 2: Agent 对话运行层级

- [ ] **Step 1: 编写运行终态失败测试**

创建 `agent-runtime.test.ts`，覆盖：

```ts
assert.equal(resolveAgentTerminal([], false), 'COMPLETED');
assert.equal(resolveAgentTerminal([], true), 'RUNNING');
assert.equal(resolveAgentTerminal([{ metadata: { terminalReason: 'FAILED' } }], false), 'FAILED');
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```bash
cd frontend
node --import tsx --test src/views/chat/modules/agent-runtime.test.ts
```

Expected: 因 `agent-runtime.ts` 不存在而失败。

- [ ] **Step 3: 实现运行摘要与交互分层**

创建纯函数模块并让 `agent-runtime-panel.vue` 使用它。面板默认显示意图、当前/最终阶段、耗时和证据状态，详细预算、工具、Token 通过展开按钮显示。同步调整会话侧栏、聊天空状态和筛选位置。

- [ ] **Step 4: 运行单测、类型检查和定向 Lint**

Run:

```bash
cd frontend
node --import tsx --test src/views/chat/modules/agent-runtime.test.ts
./node_modules/.bin/vue-tsc --noEmit --skipLibCheck
./node_modules/.bin/eslint src/views/chat/index.vue src/views/chat/modules/agent-runtime.ts src/views/chat/modules/agent-runtime.test.ts src/views/chat/modules/agent-runtime-panel.vue src/views/chat/modules/conversation-sidebar.vue src/views/chat/modules/chat-list.vue
```

Expected: 运行态测试全部通过；无新增类型错误；定向 Lint 退出码为 0。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src/views/chat
git commit -m "feat: 优化Agent对话与运行观测层级"
git push
```

### Task 3: 模型配置与知识库引导

- [ ] **Step 1: 建立视觉回归检查清单**

检查模型配置页不再出现 `bg-white`、`stone-*` 和硬编码白色渐变；检查知识库空状态包含“上传第一份文档”和五段处理链路。

- [ ] **Step 2: 重构模型配置页**

保留现有表单字段、保存和测试连接函数，将展示层拆为当前运行配置摘要、能力标签、详细配置和连接测试结果。所有颜色使用 CSS 变量或主题感知类。

- [ ] **Step 3: 重构知识库空状态**

保留现有 DataTable、上传弹窗和接口，在空数据时展示引导面板；上传按钮继续调用现有 `openUploadModal`。

- [ ] **Step 4: 运行类型检查、定向 Lint 和文本断言**

Run:

```bash
cd frontend
! rg "bg-white|stone-" src/views/model-provider/index.vue
rg "上传第一份文档|上传|解析|分块|向量化|索引" src/views/knowledge-base/index.vue
./node_modules/.bin/vue-tsc --noEmit --skipLibCheck
./node_modules/.bin/eslint src/views/model-provider/index.vue src/views/knowledge-base/index.vue
```

Expected: 模型配置无硬编码亮色类；知识库链路文本齐全；无新增类型和 Lint 错误。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src/views/model-provider/index.vue frontend/src/views/knowledge-base/index.vue
git commit -m "feat: 完善模型配置与知识库引导"
git push
```

### Task 4: 登录品牌与密码存储安全

- [ ] **Step 1: 编写安全记忆失败测试**

创建 `remembered-login.test.ts`，断言输入用户名、密码和勾选状态后，生成的持久化对象严格等于：

```ts
{ userName: 'admin' }
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```bash
cd frontend
node --import tsx --test src/views/_builtin/login/modules/remembered-login.test.ts
```

Expected: 因 `remembered-login.ts` 不存在而失败。

- [ ] **Step 3: 实现安全存储和双栏登录页**

实现仅保存用户名的纯函数，密码始终初始化为空。登录页左侧展示产品陈述和检索执行轨道，右侧保留已有登录模块切换和表单校验。

- [ ] **Step 4: 运行单测、类型检查和定向 Lint**

Run:

```bash
cd frontend
node --import tsx --test src/views/_builtin/login/modules/remembered-login.test.ts
./node_modules/.bin/vue-tsc --noEmit --skipLibCheck
./node_modules/.bin/eslint src/views/_builtin/login/index.vue src/views/_builtin/login/modules/pwd-login.vue src/views/_builtin/login/modules/remembered-login.ts src/views/_builtin/login/modules/remembered-login.test.ts
```

Expected: 安全记忆测试通过；源码不再向 `rememberedLogin` 写入 password；无新增类型和 Lint 错误。

- [ ] **Step 5: 提交并推送**

```bash
git add frontend/src/views/_builtin/login
git commit -m "feat: 重构登录品牌体验并移除明文密码记忆"
git push
```

### Task 5: 桌面浏览器回归与收尾

- [ ] **Step 1: 修复仓库类型检查基线**

将独立 Playwright 脚本从应用 TypeScript 编译范围排除，或补齐其独立配置，确保 `pnpm typecheck` 不再因为未声明的测试依赖失败。

- [ ] **Step 2: 运行完整验证**

Run:

```bash
cd frontend
node --import tsx --test src/layouts/modules/global-menu/menu-groups.test.ts src/views/chat/modules/agent-runtime.test.ts src/views/_builtin/login/modules/remembered-login.test.ts
./node_modules/.bin/vue-tsc --noEmit --skipLibCheck
./node_modules/.bin/eslint src/theme/settings.ts src/layouts/modules/global-header/index.vue src/layouts/modules/global-menu src/views/chat src/views/model-provider/index.vue src/views/knowledge-base/index.vue src/views/_builtin/login
./node_modules/.bin/vite build --mode test
```

Expected: 单测、类型检查、Lint 和构建均退出码 0。

- [ ] **Step 3: 真实浏览器回归**

在 1440×900 桌面视口检查：

- `/login`：双栏布局、亮暗主题、密码不持久化；
- `/chat`：导航分组、页头标题、空状态、运行面板展开；
- `/knowledge-base`：空状态引导和上传入口；
- `/model-provider`：暗色主题输入值和连接状态清晰。

- [ ] **Step 4: 提交收尾并推送**

```bash
git add frontend/tsconfig.json
git commit -m "test: 完善桌面端UI验证基线"
git push
```
