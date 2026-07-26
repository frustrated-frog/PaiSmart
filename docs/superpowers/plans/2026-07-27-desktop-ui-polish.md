# Desktop UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复桌面端顶部标题、导航折叠按钮、聊天双栏接缝和 AI 回答背景四类视觉问题。

**Architecture:** 保留既有 Vue 组件和业务数据流，仅调整外层布局职责与 Markdown 样式边界。用源码契约测试锁定关键 DOM/CSS 结构，再通过真实浏览器截图验证最终观感。

**Tech Stack:** Vue 3、TypeScript、UnoCSS、SCSS、Naive UI、Node.js test runner、Vite

---

### Task 1: 建立桌面端布局回归契约

**Files:**
- Create: `frontend/src/views/chat/modules/desktop-ui-contract.test.ts`

- [ ] **Step 1: 写入失败测试**

测试读取相关 Vue 源文件，并断言：导航折叠按钮不在滚动容器内；聊天工作区存在独立双面板类；AI Markdown 嵌套 `body` 被设为透明背景；顶部存在独立标题行和分隔符。

- [ ] **Step 2: 验证测试失败**

```bash
cd frontend
node --import tsx --test src/views/chat/modules/desktop-ui-contract.test.ts
```

预期：四项契约至少一项失败，失败原因对应当前缺失的结构或样式。

### Task 2: 修正全局标题和导航折叠按钮

**Files:**
- Modify: `frontend/src/layouts/modules/global-header/index.vue`
- Modify: `frontend/src/layouts/modules/global-menu/modules/vertical-menu.vue`

- [ ] **Step 1: 重构标题信息行**

为顶部信息块增加语义类名和 24px 安全留白；标题不可压缩，说明可截断，中间加入发丝分隔线。

- [ ] **Step 2: 将导航折叠按钮移出滚动容器**

使用纵向 Flex 容器包裹菜单滚动区和固定底栏。折叠按钮放入底栏，并为展开/收起状态提供可访问名称。

- [ ] **Step 3: 运行布局契约测试**

```bash
cd frontend
node --import tsx --test src/views/chat/modules/desktop-ui-contract.test.ts
```

预期：标题和导航相关断言通过，聊天区相关断言仍失败。

### Task 3: 分离聊天双栏并清理回答背景

**Files:**
- Modify: `frontend/src/views/chat/index.vue`
- Modify: `frontend/src/views/chat/modules/conversation-sidebar.vue`
- Modify: `frontend/src/views/chat/modules/chat-message.vue`

- [ ] **Step 1: 分离工作区面板**

移除聊天外层统一边框和阴影，给对话列表、主聊天区分别设置面板边框、圆角和底色；展开列表时使用 12px 间距，收起时取消间距。

- [ ] **Step 2: 清理 Markdown 嵌套背景**

在 `.assistant-answer` 作用域中将 `.vp-doc` 和其直接子 `body` 背景重置为透明，并统一正文、段落、列表间距。

- [ ] **Step 3: 验证回归测试全部通过**

```bash
cd frontend
node --import tsx --test src/views/chat/modules/desktop-ui-contract.test.ts
```

预期：全部通过。

### Task 4: 完整验证与提交

**Files:**
- Verify: `frontend/src/layouts/modules/global-header/index.vue`
- Verify: `frontend/src/layouts/modules/global-menu/modules/vertical-menu.vue`
- Verify: `frontend/src/views/chat/index.vue`
- Verify: `frontend/src/views/chat/modules/conversation-sidebar.vue`
- Verify: `frontend/src/views/chat/modules/chat-message.vue`

- [ ] **Step 1: 运行自动化验证**

```bash
cd frontend
node --import tsx --test src/views/chat/modules/desktop-ui-contract.test.ts
./node_modules/.bin/vue-tsc --noEmit --skipLibCheck
./node_modules/.bin/eslint src/layouts/modules/global-header/index.vue src/layouts/modules/global-menu/modules/vertical-menu.vue src/views/chat/index.vue src/views/chat/modules/conversation-sidebar.vue src/views/chat/modules/chat-message.vue src/views/chat/modules/desktop-ui-contract.test.ts
./node_modules/.bin/vite build --mode test
```

预期：测试、类型检查、Lint 和构建均以退出码 0 完成。

- [ ] **Step 2: 真实浏览器复测**

在 `http://localhost:9527/#/chat` 验证顶部标题、导航底栏、双栏间距、AI 正文透明背景和折叠交互。

- [ ] **Step 3: 提交并推送**

```bash
git add docs/superpowers frontend/src
git commit -m "fix: 精修桌面端聊天布局细节"
git push origin codex/desktop-ui-refinement
```
