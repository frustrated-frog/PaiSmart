<script setup lang="ts">
import { NScrollbar } from 'naive-ui';
import { VueMarkdownItProvider } from '@/vendor/vue-markdown-shiki';
import ChatMessage from './chat-message.vue';

defineOptions({
  name: 'ChatList'
});

const chatStore = useChatStore();
const { list, sessionId, conversationId, input } = storeToRefs(chatStore);

const starterPrompts = [
  {
    icon: 'solar:document-text-line-duotone',
    title: '总结知识',
    prompt: '总结当前知识库的核心主题，并列出最值得关注的三条信息。'
  },
  {
    icon: 'solar:magnifer-line-duotone',
    title: '查找依据',
    prompt: '从知识库中查找与当前项目架构相关的资料，并标注引用来源。'
  },
  {
    icon: 'solar:diagram-up-line-duotone',
    title: '对比分析',
    prompt: '对比知识库中不同方案的优缺点，并给出有依据的推荐。'
  }
];

const loading = ref(false);
const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

watch(() => [...list.value], scrollToBottom);

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999999999999,
      behavior: 'auto'
    });
  }, 100);
}

const range = ref<[number, number] | null>(null);

function getRetrievalQueryFallback(index: number) {
  for (let i = index - 1; i >= 0; i -= 1) {
    const candidate = list.value[i];
    if (candidate?.role === 'user') {
      return candidate.content || '';
    }
  }
  return '';
}

const params = computed(() => {
  const p: Record<string, string> = {};
  if (range.value) {
    p.start_date = dayjs(range.value[0]).format('YYYY-MM-DD');
    p.end_date = dayjs(range.value[1]).format('YYYY-MM-DD');
  }
  if (conversationId.value) {
    p.conversationId = conversationId.value;
  }
  return p;
});

watchEffect(() => {
  getList();
});

async function getList() {
  loading.value = true;
  const { error, data } = await request<Api.Chat.Message[]>({
    url: 'users/conversation',
    params: params.value
  });
  if (!error) {
    list.value = data;
  }
  loading.value = false;
}

onMounted(() => {
  chatStore.scrollToBottom = scrollToBottom;
});

const showEmpty = computed(() => !loading.value && list.value.length === 0);

function selectStarterPrompt(prompt: string) {
  input.value.message = prompt;
}
</script>

<template>
  <Suspense>
    <div class="h-0 flex flex-col flex-1">
      <div v-if="!showEmpty" class="chat-toolbar">
        <div>
          <div class="text-8px text-[var(--zs-signal-cyan)] font-750 tracking-[0.14em]">CONVERSATION</div>
          <div class="mt-0.5 text-12px text-[var(--zs-ink-secondary)]">回答、引用与 Agent 执行记录</div>
        </div>
        <NDatePicker
          v-model:value="range"
          type="daterange"
          clearable
          size="small"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </div>

      <!-- Empty state -->
      <div v-if="showEmpty" class="chat-empty">
        <div class="chat-empty__mark">
          <SystemLogo class="text-34px text-primary" />
        </div>
        <div class="text-center">
          <div class="zs-display-type text-22px font-650">
            {{ conversationId ? '从企业知识中获得可靠答案' : '先选择或创建一个对话' }}
          </div>
          <div class="mt-2 text-13px text-[var(--zs-ink-secondary)]">
            {{
              conversationId
                ? '知枢会规划检索、融合证据并展示 Agent 执行过程'
                : '在左侧选择历史对话，或点击「新对话」开始'
            }}
          </div>
        </div>
        <div v-if="conversationId" class="chat-empty__prompts">
          <button
            v-for="item in starterPrompts"
            :key="item.title"
            type="button"
            class="starter-prompt"
            @click="selectStarterPrompt(item.prompt)"
          >
            <SvgIcon :icon="item.icon" class="text-18px text-[var(--zs-knowledge-indigo)]" />
            <span>
              <b>{{ item.title }}</b>
              <small>{{ item.prompt }}</small>
            </span>
            <icon-material-symbols:arrow-forward-rounded class="ml-auto shrink-0 text-16px" />
          </button>
        </div>
      </div>

      <!-- Message list -->
      <NScrollbar v-else ref="scrollbarRef" class="flex-1">
        <NSpin :show="loading">
          <div class="mx-auto max-w-[1040px] w-full px-7 py-6">
            <VueMarkdownItProvider>
              <ChatMessage
                v-for="(item, index) in list"
                :key="item.generationId || `${item.role}-${item.timestamp || index}`"
                :msg="item"
                :session-id="sessionId"
                :retrieval-query-fallback="getRetrievalQueryFallback(index)"
              />
            </VueMarkdownItProvider>
          </div>
        </NSpin>
      </NScrollbar>
    </div>
  </Suspense>
</template>

<style scoped lang="scss">
.chat-toolbar {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--zs-border);
  padding: 8px 18px 8px 22px;
  background: var(--zs-surface-panel);
}

.chat-empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 48px;
}

.chat-empty__mark {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  border: 1px solid rgb(86 87 217 / 14%);
  border-radius: 16px;
  background: rgb(86 87 217 / 7%);
  box-shadow: 0 12px 30px rgb(86 87 217 / 10%);
}

.chat-empty__prompts {
  display: grid;
  width: min(780px, 100%);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.starter-prompt {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 10px;
  border: 1px solid var(--zs-border);
  border-radius: 12px;
  padding: 13px;
  color: var(--zs-ink-primary);
  background: var(--zs-surface-panel);
  cursor: pointer;
  text-align: left;
  transition:
    border-color 160ms ease,
    transform 160ms ease,
    box-shadow 160ms ease;
}

.starter-prompt:hover {
  border-color: rgb(86 87 217 / 28%);
  box-shadow: 0 10px 24px rgb(33 43 69 / 7%);
  transform: translateY(-2px);
}

.starter-prompt > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.starter-prompt b {
  font-size: 12px;
  font-weight: 650;
}

.starter-prompt small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--zs-ink-secondary);
  font-size: 10px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
