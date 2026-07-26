import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

function read(relativePath: string) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8');
}

test('顶部页面信息使用安全留白、独立标题行和视觉分隔', () => {
  const source = read('../../../layouts/modules/global-header/index.vue');

  assert.match(source, /class="page-context[^"]*"/);
  assert.match(source, /page-context__title-line/);
  assert.match(source, /page-context__divider/);
});

test('桌面导航折叠按钮位于菜单滚动区域之外', () => {
  const source = read('../../../layouts/modules/global-menu/modules/vertical-menu.vue');
  const scrollbarEnd = source.indexOf('</SimpleScrollbar>');
  const toggler = source.indexOf('<MenuToggler');

  assert.ok(scrollbarEnd >= 0, '应保留菜单滚动容器');
  assert.ok(toggler > scrollbarEnd, '折叠按钮必须位于滚动容器之后，避免覆盖业务菜单');
  assert.match(source, /navigation-collapse-footer/);
});

test('聊天工作区将会话列表和主内容渲染为有间距的独立面板', () => {
  const source = read('../index.vue');
  const sidebarSource = read('./conversation-sidebar.vue');

  assert.match(source, /chat-workspace--sidebar-open/);
  assert.match(source, /chat-workspace__main/);
  assert.match(source, /chat-workspace__main--sidebar-collapsed/);
  assert.match(source, /chat-workspace__main--sidebar-collapsed\s+:deep\(\.chat-toolbar\)/);
  assert.doesNotMatch(source, /shadow-\[var\(--zs-shadow-workspace\)\][^\n]*ConversationSidebar/);
  assert.match(sidebarSource, /aria-label="收起对话列表"/);
});

test('AI 回答重置 Markdown 嵌套 body 的页面级背景', () => {
  const source = read('./chat-message.vue');

  assert.match(source, /\.assistant-answer\s+:deep\(\.vp-doc\s*>\s*body\)/);
  assert.match(source, /\.assistant-answer\s+:deep\(\.vp-doc\s*>\s*body\)[\s\S]*?background:\s*transparent/);
});
