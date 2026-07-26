<script setup lang="ts">
defineOptions({
  name: 'ConversationSidebar'
});

const collapsed = defineModel<boolean>('collapsed', { default: false });

const chatStore = useChatStore();
const { conversationId, sessionsLoading, filteredSessions, activeTab, agentRunMetrics } = storeToRefs(chatStore);
const healthExpanded = ref(false);

const successRateLabel = computed(() => `${Math.round((agentRunMetrics.value?.successRate || 0) * 100)}%`);
const p95LatencyLabel = computed(() => {
  const latency = agentRunMetrics.value?.p95LatencyMs || 0;
  return latency < 1000 ? `${latency}ms` : `${(latency / 1000).toFixed(1)}s`;
});

onMounted(() => {
  chatStore.loadSessions();
});

function handleCollapse() {
  collapsed.value = true;
}

function handleNewChat() {
  chatStore.createNewSession();
}

function handleSelect(cid: string) {
  chatStore.switchSession(cid);
}

function handleArchive(cid: string) {
  chatStore.archiveSession(cid);
}

function handleUnarchive(cid: string) {
  chatStore.unarchiveSession(cid);
}

function setActiveTab(tab: 'active' | 'archived') {
  activeTab.value = tab;
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '';
  const date = dayjs(dateStr);
  const now = dayjs();
  if (date.isSame(now, 'day')) {
    return date.format('HH:mm');
  }
  if (date.isSame(now, 'week')) {
    return date.format('ddd');
  }
  if (date.isSame(now, 'year')) {
    return date.format('MM-DD');
  }
  return date.format('YYYY-MM-DD');
}
</script>

<template>
  <div
    class="relative h-full flex flex-col shrink-0 overflow-hidden border border-[var(--zs-border)] rounded-2xl bg-#f8f9fb shadow-[0_10px_30px_rgb(33_43_69/5%)] transition-[width,opacity] duration-200 ease-out dark:bg-#ffffff05"
    :class="collapsed ? 'w-0 min-w-0 border-0 opacity-0' : 'w-[260px] min-w-[260px] opacity-100'"
  >
    <div class="w-[260px] flex flex-col flex-1 overflow-hidden" :class="{ 'pointer-events-none invisible': collapsed }">
      <div class="flex items-center justify-between px-4 pb-2 pt-4">
        <span class="text-15px font-600">对话列表</span>
        <div class="flex items-center gap-1">
          <NButton type="primary" size="small" secondary @click="handleNewChat">
            <template #icon>
              <icon-material-symbols:add-rounded />
            </template>
            新对话
          </NButton>
          <NButton text size="tiny" aria-label="收起对话列表" title="收起对话列表" @click="handleCollapse">
            <template #icon>
              <icon-material-symbols:left-panel-close-outline-rounded />
            </template>
          </NButton>
        </div>
      </div>

      <!-- Tabs -->
      <div class="mx-4 mb-2 flex rounded-lg bg-[rgb(var(--border-color)/0.15)] p-0.5 dark:bg-[#FFFFFF0A]">
        <button
          type="button"
          class="flex-1 cursor-pointer rounded-md px-3 py-1.5 text-center text-12px font-500 transition-all"
          :class="
            activeTab === 'active'
              ? 'bg-white text-[rgb(var(--primary-color))] shadow-sm dark:bg-[#FFFFFF14] dark:text-(--primary-color)'
              : 'text-#666 dark:text-#999'
          "
          @click="setActiveTab('active')"
        >
          活跃
        </button>
        <button
          type="button"
          class="flex-1 cursor-pointer rounded-md px-3 py-1.5 text-center text-12px font-500 transition-all"
          :class="
            activeTab === 'archived'
              ? 'bg-white text-[rgb(var(--primary-color))] shadow-sm dark:bg-[#FFFFFF14] dark:text-(--primary-color)'
              : 'text-#666 dark:text-#999'
          "
          @click="setActiveTab('archived')"
        >
          已归档
        </button>
      </div>

      <section v-if="conversationId && agentRunMetrics?.totalRuns" class="run-health">
        <button
          type="button"
          class="run-health__summary"
          :aria-expanded="healthExpanded"
          @click="healthExpanded = !healthExpanded"
        >
          <span class="run-health__indicator" />
          <span>
            <b>RUN HEALTH · 30D</b>
            <small>{{ successRateLabel }} 成功 · P95 {{ p95LatencyLabel }}</small>
          </span>
          <span class="run-health__count">{{ agentRunMetrics.totalRuns }} runs</span>
          <icon-material-symbols:keyboard-arrow-down-rounded
            class="transition-transform"
            :class="{ 'rotate-180': healthExpanded }"
          />
        </button>
        <Transition name="health-fold">
          <div v-if="healthExpanded" class="run-health__details">
            <div>
              <span>成功率</span>
              <b>{{ successRateLabel }}</b>
            </div>
            <div>
              <span>P95</span>
              <b>{{ p95LatencyLabel }}</b>
            </div>
            <div>
              <span>平均步骤</span>
              <b>{{ agentRunMetrics.averageSteps }}</b>
            </div>
            <p v-if="agentRunMetrics.retryCount">
              checkpoint 恢复 {{ agentRunMetrics.retryCount }} 次 · 成功率
              {{ Math.round(agentRunMetrics.retryRecoveryRate * 100) }}%
            </p>
          </div>
        </Transition>
      </section>

      <!-- List -->
      <div class="flex-1 overflow-y-auto px-2">
        <NSpin :show="sessionsLoading" class="h-full">
          <TransitionGroup name="session-list" tag="div">
            <div
              v-if="filteredSessions.length === 0 && !sessionsLoading"
              class="flex flex-col items-center justify-center gap-3 py-16"
            >
              <icon-material-symbols:chat-outline-rounded class="text-36px color-#ccc dark:color-#444" />
              <span class="text-13px color-#aaa">{{ activeTab === 'active' ? '暂无对话记录' : '暂无归档对话' }}</span>
            </div>

            <div
              v-for="session in filteredSessions"
              :key="session.conversationId"
              role="button"
              tabindex="0"
              class="group mx-1 mb-0.5 flex cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2.5 transition-all"
              :class="
                session.conversationId === conversationId
                  ? 'bg-[rgb(var(--primary-color)/0.08)]'
                  : 'hover:bg-[rgb(var(--border-color)/0.3)] dark:hover:bg-[#FFFFFF08]'
              "
              @click="handleSelect(session.conversationId)"
              @keydown.enter="handleSelect(session.conversationId)"
              @keydown.space.prevent="handleSelect(session.conversationId)"
            >
              <div
                class="h-7 w-7 flex shrink-0 items-center justify-center rounded-lg text-15px"
                :class="
                  session.conversationId === conversationId
                    ? 'bg-[rgb(var(--primary-color)/0.12)] text-[rgb(var(--primary-color))]'
                    : 'bg-[rgb(var(--border-color)/0.2)] text-#999 dark:bg-[#FFFFFF0A]'
                "
              >
                <icon-material-symbols:chat-outline-rounded />
              </div>
              <div class="min-w-0 flex-1">
                <div
                  class="truncate text-13px font-500"
                  :class="session.conversationId === conversationId ? 'text-[rgb(var(--primary-color))]' : ''"
                >
                  {{ session.title }}
                </div>
                <div class="text-11px color-#aaa">{{ formatDate(session.updatedAt) }}</div>
              </div>

              <!-- Action button -->
              <NPopconfirm v-if="activeTab === 'active'" @positive-click="handleArchive(session.conversationId)">
                <template #trigger>
                  <NButton
                    class="shrink-0 transition-opacity"
                    :class="session.conversationId === conversationId ? '' : 'opacity-0 group-hover:opacity-100'"
                    text
                    size="tiny"
                    @click.stop
                  >
                    <template #icon>
                      <icon-material-symbols:archive-outline-rounded class="text-15px color-#999 hover:color-#666" />
                    </template>
                  </NButton>
                </template>
                归档后可在「已归档」中找回
              </NPopconfirm>
              <NButton
                v-else
                class="shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
                text
                size="tiny"
                @click.stop="handleUnarchive(session.conversationId)"
              >
                <template #icon>
                  <icon-material-symbols:unarchive-outline-rounded class="text-15px color-#999 hover:color-#666" />
                </template>
              </NButton>
            </div>
          </TransitionGroup>
        </NSpin>
      </div>
    </div>
  </div>
</template>

<style scoped>
.run-health {
  margin: 0 12px 8px;
  overflow: hidden;
  border: 1px solid rgb(86 87 217 / 14%);
  border-radius: 12px;
  background: rgb(86 87 217 / 4%);
}

.run-health__summary {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  border: 0;
  padding: 9px 10px;
  color: inherit;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.run-health__summary > span:nth-child(2) {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.run-health__summary b {
  color: var(--zs-knowledge-indigo);
  font-size: 8px;
  letter-spacing: 0.11em;
}

.run-health__summary small {
  overflow: hidden;
  color: var(--zs-ink-secondary);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-health__indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zs-evidence-emerald);
  box-shadow: 0 0 0 3px rgb(5 150 105 / 10%);
}

.run-health__count {
  color: var(--zs-ink-secondary);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}

.run-health__details {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  border-top: 1px solid var(--zs-border);
  background: var(--zs-border);
}

.run-health__details > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 9px;
  background: var(--zs-surface-panel);
}

.run-health__details span,
.run-health__details p {
  color: var(--zs-ink-secondary);
  font-size: 9px;
}

.run-health__details b {
  font-size: 13px;
}

.run-health__details p {
  grid-column: 1 / -1;
  margin: 0;
  padding: 7px 9px;
  background: var(--zs-surface-panel);
}

.health-fold-enter-active,
.health-fold-leave-active {
  transition:
    opacity 160ms ease,
    transform 160ms ease;
}

.health-fold-enter-from,
.health-fold-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.session-list-enter-active,
.session-list-leave-active {
  transition: all 0.2s ease;
}
.session-list-enter-from,
.session-list-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
</style>
