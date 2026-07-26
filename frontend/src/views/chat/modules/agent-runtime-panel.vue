<script setup lang="ts">
import { resolveAgentTerminal } from './agent-runtime';

defineOptions({ name: 'AgentRuntimePanel' });

const props = withDefaults(
  defineProps<{
    steps: Api.Chat.AgentStepEvent[];
    running?: boolean;
  }>(),
  {
    running: false
  }
);

const detailsExpanded = ref(false);

const runtime = computed(() => {
  const metadata = props.steps.map(step => step.metadata).filter(Boolean);
  const latestBudget = [...metadata].reverse().find(item => item?.budget)?.budget;
  const latestRetrieval = [...metadata].reverse().find(item => item?.retrievalTrace)?.retrievalTrace;
  const latestEvidence = [...metadata].reverse().find(item => item?.evidenceAssessment)?.evidenceAssessment;
  const intent = [...metadata].reverse().find(item => item?.intent)?.intent || latestRetrieval?.queryPlan.intent;
  const visibleTools = [...metadata].reverse().find(item => item?.visibleTools)?.visibleTools || [];
  const terminal = resolveAgentTerminal(props.steps, props.running);

  return { latestBudget, latestRetrieval, latestEvidence, terminal, intent, visibleTools };
});

const hasRuntimeSignals = computed(() =>
  Boolean(
    runtime.value.intent || runtime.value.latestBudget || runtime.value.latestEvidence || runtime.value.latestRetrieval
  )
);

const pipeline = computed(() => {
  const stageNames = runtime.value.latestRetrieval?.stages.map(stage => stage.name.toUpperCase()) || [];
  const hasStage = (fragment: string) => stageNames.some(name => name.includes(fragment));

  return [
    { key: 'query', label: 'Query', complete: Boolean(runtime.value.latestRetrieval) },
    { key: 'recall', label: 'BM25 + Vector', complete: hasStage('RECALL') },
    { key: 'fusion', label: 'RRF', complete: hasStage('RRF') },
    { key: 'rerank', label: 'Rerank', complete: hasStage('RERANK') },
    { key: 'evidence', label: 'Evidence', complete: Boolean(runtime.value.latestEvidence) }
  ];
});

const terminalTone = computed(() => {
  if (runtime.value.terminal === 'RUNNING') return 'running';
  if (['FAILED', 'CANCELLED'].includes(runtime.value.terminal)) return 'failed';
  if (runtime.value.terminal.startsWith('WAITING_')) return 'waiting';
  return 'completed';
});

function formatTokens(value?: number) {
  if (!value) return '0';
  return value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value);
}
</script>

<template>
  <section v-if="hasRuntimeSignals" class="runtime-panel" :class="`runtime-panel--${terminalTone}`">
    <button
      type="button"
      class="runtime-panel__summary"
      :aria-expanded="detailsExpanded"
      @click="detailsExpanded = !detailsExpanded"
    >
      <span class="runtime-panel__status">
        <span class="runtime-panel__pulse" />
        <span>
          <span class="runtime-panel__eyebrow">AGENT RUN</span>
          <strong>{{ runtime.terminal }}</strong>
        </span>
      </span>

      <span class="runtime-panel__pipeline" aria-label="检索执行轨道">
        <span
          v-for="(phase, index) in pipeline"
          :key="phase.key"
          class="runtime-phase"
          :class="{ 'runtime-phase--complete': phase.complete }"
        >
          <span class="runtime-phase__dot" />
          <span>{{ phase.label }}</span>
          <icon-material-symbols:chevron-right-rounded
            v-if="index < pipeline.length - 1"
            class="runtime-phase__arrow"
          />
        </span>
      </span>

      <span class="runtime-panel__toggle">
        运行详情
        <icon-material-symbols:keyboard-arrow-down-rounded
          class="transition-transform"
          :class="{ 'rotate-180': detailsExpanded }"
        />
      </span>
    </button>

    <Transition name="runtime-fold">
      <div v-if="detailsExpanded" class="runtime-panel__details">
        <div class="runtime-signal">
          <span class="runtime-signal__label">PLAN</span>
          <b>{{ runtime.intent || 'DIRECT' }}</b>
          <small>{{ runtime.visibleTools.length }} tools visible</small>
        </div>
        <div class="runtime-signal">
          <span class="runtime-signal__label">BUDGET</span>
          <b>
            {{ runtime.latestBudget?.modelTurnsUsed || 0 }} turns · {{ runtime.latestBudget?.toolCallsUsed || 0 }} calls
          </b>
          <small>
            {{
              formatTokens(
                (runtime.latestBudget?.promptTokensUsed || 0) + (runtime.latestBudget?.completionTokensUsed || 0)
              )
            }}
            tokens
          </small>
        </div>
        <div class="runtime-signal">
          <span class="runtime-signal__label">EVIDENCE</span>
          <b>{{ runtime.latestEvidence?.status || 'NOT_REQUIRED' }}</b>
          <small v-if="runtime.latestEvidence">
            confidence {{ Math.round(runtime.latestEvidence.confidence * 100) }}%
          </small>
          <small v-else>direct response</small>
        </div>
        <div class="runtime-signal">
          <span class="runtime-signal__label">LATENCY</span>
          <b>{{ runtime.latestBudget?.elapsedMillis || runtime.latestRetrieval?.totalLatencyMs || 0 }} ms</b>
          <small v-if="runtime.latestRetrieval?.traceId" class="font-mono">
            trace {{ runtime.latestRetrieval.traceId.slice(0, 8) }}
          </small>
          <small v-else>no retrieval trace</small>
        </div>
      </div>
    </Transition>
  </section>
</template>

<style scoped lang="scss">
.runtime-panel {
  max-width: 760px;
  overflow: hidden;
  border: 1px solid var(--zs-border);
  border-radius: 12px;
  background: var(--zs-surface-panel);
}

.runtime-panel--running {
  border-color: rgb(8 145 178 / 24%);
}

.runtime-panel--failed {
  border-color: rgb(220 38 38 / 24%);
}

.runtime-panel--waiting {
  border-color: rgb(217 119 6 / 26%);
}

.runtime-panel__summary {
  display: grid;
  width: 100%;
  grid-template-columns: 130px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  border: 0;
  padding: 11px 13px;
  color: inherit;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.runtime-panel__summary:hover {
  background: rgb(86 87 217 / 4%);
}

.runtime-panel__status {
  display: flex;
  align-items: center;
  gap: 9px;
}

.runtime-panel__status > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.runtime-panel__status strong {
  overflow: hidden;
  color: var(--zs-ink-primary);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 10px;
  font-weight: 650;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-panel__eyebrow {
  color: var(--zs-ink-secondary);
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.14em;
}

.runtime-panel__pulse {
  width: 7px;
  height: 7px;
  flex: 0 0 7px;
  border-radius: 50%;
  background: var(--zs-evidence-emerald);
  box-shadow: 0 0 0 4px rgb(5 150 105 / 10%);
}

.runtime-panel--running .runtime-panel__pulse {
  background: var(--zs-signal-cyan);
  box-shadow: 0 0 0 4px rgb(8 145 178 / 10%);
  animation: runtime-pulse 1.8s ease-in-out infinite;
}

.runtime-panel--failed .runtime-panel__pulse {
  background: #dc2626;
  box-shadow: 0 0 0 4px rgb(220 38 38 / 10%);
}

.runtime-panel--waiting .runtime-panel__pulse {
  background: #d97706;
  box-shadow: 0 0 0 4px rgb(217 119 6 / 10%);
}

.runtime-panel__pipeline {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.runtime-phase {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  color: var(--zs-ink-secondary);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 9px;
  white-space: nowrap;
}

.runtime-phase__dot {
  width: 5px;
  height: 5px;
  flex: 0 0 5px;
  border: 1px solid var(--zs-ink-secondary);
  border-radius: 50%;
}

.runtime-phase--complete {
  color: var(--zs-knowledge-indigo);
}

.runtime-phase--complete .runtime-phase__dot {
  border-color: var(--zs-knowledge-indigo);
  background: var(--zs-knowledge-indigo);
}

.runtime-phase__arrow {
  color: var(--zs-border);
  font-size: 13px;
}

.runtime-panel__toggle {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--zs-ink-secondary);
  font-size: 10px;
  white-space: nowrap;
}

.runtime-panel__details {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid var(--zs-border);
  background: var(--zs-surface-muted);
}

.runtime-signal {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  padding: 11px 13px 12px;
}

.runtime-signal:not(:last-child) {
  border-right: 1px solid var(--zs-border);
}

.runtime-signal__label {
  color: var(--zs-ink-secondary);
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.runtime-signal b {
  overflow: hidden;
  color: var(--zs-ink-primary);
  font-size: 11px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-signal small {
  overflow: hidden;
  color: var(--zs-ink-secondary);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-fold-enter-active,
.runtime-fold-leave-active {
  transition:
    opacity 160ms ease,
    transform 160ms ease;
  transform-origin: top;
}

.runtime-fold-enter-from,
.runtime-fold-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@keyframes runtime-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.45;
  }
}
</style>
