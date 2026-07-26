<script setup lang="ts">
defineOptions({ name: 'AgentRuntimePanel' });

const props = defineProps<{
  steps: Api.Chat.AgentStepEvent[];
}>();

const runtime = computed(() => {
  const metadata = props.steps.map(step => step.metadata).filter(Boolean);
  const latestBudget = [...metadata].reverse().find(item => item?.budget)?.budget;
  const latestRetrieval = [...metadata].reverse().find(item => item?.retrievalTrace)?.retrievalTrace;
  const latestEvidence = [...metadata].reverse().find(item => item?.evidenceAssessment)?.evidenceAssessment;
  const terminalReason = [...metadata].reverse().find(item => item?.terminalReason)?.terminalReason;
  const intent = [...metadata].reverse().find(item => item?.intent)?.intent || latestRetrieval?.queryPlan.intent;
  const visibleTools = [...metadata].reverse().find(item => item?.visibleTools)?.visibleTools || [];
  return { latestBudget, latestRetrieval, latestEvidence, terminalReason, intent, visibleTools };
});

const hasRuntimeSignals = computed(() =>
  Boolean(
    runtime.value.intent || runtime.value.latestBudget || runtime.value.latestEvidence || runtime.value.terminalReason
  )
);

function formatTokens(value?: number) {
  if (!value) return '0';
  return value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value);
}
</script>

<template>
  <div v-if="hasRuntimeSignals" class="runtime-panel">
    <div class="runtime-panel__title">
      <span class="runtime-panel__pulse" />
      <span>RUNTIME CONTROL</span>
      <span v-if="runtime.latestRetrieval?.traceId" class="runtime-panel__trace font-mono">
        {{ runtime.latestRetrieval.traceId.slice(0, 8) }}
      </span>
    </div>
    <div class="runtime-panel__grid">
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
        <span class="runtime-signal__label">TERMINAL</span>
        <b>{{ runtime.terminalReason || 'RUNNING' }}</b>
        <small>{{ runtime.latestBudget?.elapsedMillis || runtime.latestRetrieval?.totalLatencyMs || 0 }} ms</small>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.runtime-panel {
  max-width: 680px;
  overflow: hidden;
  border: 1px solid rgb(99 102 241 / 0.18);
  border-radius: 14px;
  background: linear-gradient(145deg, rgb(15 23 42 / 0.96), rgb(30 27 75 / 0.94));
  color: #e2e8f0;
  box-shadow: 0 12px 34px rgb(15 23 42 / 0.15);
}

.runtime-panel__title {
  display: flex;
  align-items: center;
  gap: 7px;
  border-bottom: 1px solid rgb(148 163 184 / 0.12);
  padding: 9px 12px;
  color: #a5b4fc;
  font-size: 9px;
  font-weight: 750;
  letter-spacing: 0.14em;
}

.runtime-panel__pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow:
    0 0 0 4px rgb(52 211 153 / 0.1),
    0 0 12px rgb(52 211 153 / 0.7);
}

.runtime-panel__trace {
  margin-left: auto;
  color: #64748b;
  letter-spacing: 0.04em;
}

.runtime-panel__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.runtime-signal {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  padding: 11px 12px 12px;
}

.runtime-signal:not(:last-child) {
  border-right: 1px solid rgb(148 163 184 / 0.1);
}

.runtime-signal__label {
  color: #64748b;
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.runtime-signal b {
  overflow: hidden;
  color: #f8fafc;
  font-size: 11px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-signal small {
  overflow: hidden;
  color: #94a3b8;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .runtime-panel__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .runtime-signal:nth-child(2) {
    border-right: 0;
  }

  .runtime-signal:nth-child(-n + 2) {
    border-bottom: 1px solid rgb(148 163 184 / 0.1);
  }
}
</style>
