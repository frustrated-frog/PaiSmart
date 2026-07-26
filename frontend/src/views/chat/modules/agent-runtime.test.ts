import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveAgentTerminal } from './agent-runtime';

function step(status: Api.Chat.AgentStepEvent['status'], terminalReason?: string): Api.Chat.AgentStepEvent {
  return {
    stepId: `${status}-${terminalReason || 'none'}`,
    stage: 'finalizing',
    status,
    title: status,
    timestamp: 1,
    metadata: terminalReason ? { terminalReason } : undefined
  };
}

test('消息已结束且没有终止原因时显示 COMPLETED', () => {
  assert.equal(resolveAgentTerminal([step('completed')], false), 'COMPLETED');
});

test('消息仍在生成或步骤运行中时显示 RUNNING', () => {
  assert.equal(resolveAgentTerminal([step('completed')], true), 'RUNNING');
  assert.equal(resolveAgentTerminal([step('running')], false), 'RUNNING');
});

test('优先展示服务端终止原因', () => {
  assert.equal(resolveAgentTerminal([step('completed', 'WAITING_APPROVAL')], false), 'WAITING_APPROVAL');
});

test('缺少终止原因时仍保留失败和取消状态', () => {
  assert.equal(resolveAgentTerminal([step('failed')], false), 'FAILED');
  assert.equal(resolveAgentTerminal([step('cancelled')], false), 'CANCELLED');
});
