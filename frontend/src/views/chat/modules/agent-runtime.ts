export type AgentTerminalState = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | string;

export function resolveAgentTerminal(steps: Api.Chat.AgentStepEvent[], messageRunning: boolean): AgentTerminalState {
  const explicitTerminal = [...steps].reverse().find(step => step.metadata?.terminalReason)?.metadata?.terminalReason;

  if (explicitTerminal) {
    return explicitTerminal;
  }

  if (steps.some(step => step.status === 'failed')) {
    return 'FAILED';
  }

  if (steps.some(step => step.status === 'cancelled')) {
    return 'CANCELLED';
  }

  if (messageRunning || steps.some(step => step.status === 'running')) {
    return 'RUNNING';
  }

  return 'COMPLETED';
}
