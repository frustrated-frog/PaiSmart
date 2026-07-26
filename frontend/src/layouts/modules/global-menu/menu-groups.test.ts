import assert from 'node:assert/strict';
import test from 'node:test';
import { groupDesktopMenus } from './menu-groups';

function menu(key: string, label = key) {
  return {
    key,
    label,
    routeKey: key,
    routePath: `/${key}`
  } as App.Global.Menu;
}

test('按知识工作流分组，并保持组内原有顺序', () => {
  const chat = menu('chat');
  const knowledgeBase = menu('knowledge-base');
  const modelProvider = menu('model-provider');
  const user = menu('user');

  const groups = groupDesktopMenus([chat, knowledgeBase, modelProvider, user]);

  assert.deepEqual(
    groups.map(group => [group.label, group.children.map(item => item.key)]),
    [
      ['核心工作台', ['chat', 'knowledge-base']],
      ['Agent 治理', ['model-provider']],
      ['组织与系统', ['user']]
    ]
  );
  assert.equal(groups[0]?.children[0], chat);
});

test('权限过滤导致菜单缺失时不创建空分组', () => {
  const groups = groupDesktopMenus([menu('chat'), menu('personal-center')]);

  assert.deepEqual(
    groups.map(group => group.label),
    ['核心工作台', '账户服务']
  );
});

test('未知菜单归入其他能力且不会丢失', () => {
  const unknown = menu('evaluation-lab', '评测实验室');
  const groups = groupDesktopMenus([unknown]);

  assert.equal(groups[0]?.label, '其他能力');
  assert.equal(groups[0]?.children[0], unknown);
});
