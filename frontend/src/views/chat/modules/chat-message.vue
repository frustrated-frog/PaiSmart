<script setup lang="ts">
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { nextTick } from 'vue';
import { router } from '@/router';
import { request } from '@/service/request';
import { formatDate } from '@/utils/common';
import { VueMarkdownIt } from '@/vendor/vue-markdown-shiki';
import AgentRuntimePanel from './agent-runtime-panel.vue';
defineOptions({ name: 'ChatMessage' });

const props = defineProps<{
  msg: Api.Chat.Message,
  sessionId?: string,
  retrievalQueryFallback?: string
}>();

const authStore = useAuthStore();

function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}

const chatStore = useChatStore();
const feedbackSubmitting = ref<Record<string, boolean>>({});
const retrySubmitting = ref(false);
const approvalSubmitting = ref(false);
const approvalDecision = ref<'APPROVE' | 'REJECT' | null>(null);

function getMessageFeedbackKey(message: Api.Chat.Message) {
  return message.generationId || `${message.conversationId || 'unknown'}:${message.timestamp || ''}`;
}

async function handleFeedback(message: Api.Chat.Message, rating: 'good' | 'bad') {
  if (message.role !== 'assistant') {
    return;
  }

  const key = getMessageFeedbackKey(message);
  if (feedbackSubmitting.value[key]) {
    return;
  }

  feedbackSubmitting.value = {
    ...feedbackSubmitting.value,
    [key]: true
  };

  const { error } = await request({
    url: 'chat/feedback',
    method: 'POST',
    data: {
      rating,
      reason: rating === 'good' ? '用户点击点赞，表示认可本次回答' : '用户点击点踩，表示不满意本次回答',
      conversationId: message.conversationId || props.sessionId,
      generationId: message.generationId,
      query: props.retrievalQueryFallback
    }
  });

  feedbackSubmitting.value = {
    ...feedbackSubmitting.value,
    [key]: false
  };

  if (error) {
    window.$message?.error('反馈记录失败');
    return;
  }

  message.feedbackRating = rating;
  window.$message?.success(rating === 'good' ? '已记录点赞反馈' : '已记录点踩反馈');
}

// 存储文件名和对应的事件处理
const sourceFiles = ref<Array<{fileName: string, id: string, referenceNumber: number, fileMd5?: string, pageNumber?: number}>>([]);
const bareUrlPattern = /https?:\/\/[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+/g;
const toolNameLabels: Record<string, string> = {
  search_knowledge: '检索知识库',
  generate_summary: '生成知识摘要',
  submit_feedback: '记录反馈',
  knowledge_stats: '读取知识库统计'
};
const toolStatusLabels: Record<Api.Chat.AgentToolEvent['status'], string> = {
  executing: '执行中',
  success: '已完成',
  failed: '失败',
  waiting_approval: '等待确认'
};

const toolEvents = computed(() => props.msg.toolEvents || []);
const traceExpanded = ref(true);

const agentSteps = computed(() => {
  const merged = new Map<string, Api.Chat.AgentStepEvent & { startedAt: number }>();
  for (const event of props.msg.agentEvents || []) {
    const previous = merged.get(event.stepId);
    merged.set(event.stepId, {
      ...event,
      startedAt: previous?.startedAt || event.timestamp
    });
  }
  return [...merged.values()].sort((a, b) => a.startedAt - b.startedAt);
});

const agentTraceSummary = computed(() => {
  const steps = agentSteps.value;
  if (steps.some(step => step.metadata?.terminalReason === 'WAITING_APPROVAL')) {
    return '一个高风险工具正在等待你确认';
  }
  if (steps.some(step => step.metadata?.terminalReason === 'WAITING_CLARIFICATION')) {
    return '等待你补充一个关键信息';
  }
  const running = steps.find(step => step.status === 'running');
  if (running) {
    return running.title;
  }
  if (steps.some(step => step.status === 'failed')) {
    return '部分步骤执行失败，已完成降级处理';
  }
  if (steps.some(step => step.status === 'cancelled')) {
    return 'Agent 已停止';
  }
  return `已完成 ${steps.length} 个执行步骤`;
});

const clarificationStep = computed(() =>
  [...agentSteps.value].reverse().find(step => step.metadata?.terminalReason === 'WAITING_CLARIFICATION')
);

const approvalStep = computed(() =>
  [...agentSteps.value].reverse().find(step => step.metadata?.terminalReason === 'WAITING_APPROVAL')
);

const approvalToolLedgerId = computed(() => Number(approvalStep.value?.metadata?.toolLedgerId));

async function handleToolApproval(decision: 'APPROVE' | 'REJECT') {
  if (!props.msg.generationId || !Number.isFinite(approvalToolLedgerId.value) || approvalSubmitting.value) {
    return;
  }
  approvalSubmitting.value = true;
  const succeeded = await chatStore.decideToolApproval(
    props.msg.generationId,
    approvalToolLedgerId.value,
    decision
  );
  approvalSubmitting.value = false;
  if (!succeeded) {
    window.$message?.error('审批提交失败，请确认当前没有其他 Agent 任务正在运行');
    return;
  }
  approvalDecision.value = decision;
  window.$message?.success(decision === 'APPROVE' ? '已授权，Agent 正在恢复执行' : '已拒绝，Agent 将改用安全方案');
  chatStore.scrollToBottom?.();
}

const clarificationOptions = computed(() => clarificationStep.value?.metadata?.options || []);

function selectClarificationOption(option: string) {
  chatStore.input.message = option;
  window.$message?.info('已填入澄清选项，确认后发送即可继续原任务');
}

const canRetryRun = computed(() => Boolean(
  props.msg.role === 'assistant'
  && props.msg.generationId
  && (props.msg.status === 'error' || agentSteps.value.some(step => step.status === 'cancelled'))
));

async function handleRetryRun() {
  if (!props.msg.generationId || retrySubmitting.value || !props.retrievalQueryFallback?.trim()) {
    return;
  }
  retrySubmitting.value = true;
  const succeeded = await chatStore.retryAgentRun(props.msg.generationId, props.retrievalQueryFallback);
  retrySubmitting.value = false;
  if (succeeded) {
    window.$message?.success('已从 checkpoint 创建新的 Agent 运行');
    chatStore.scrollToBottom?.();
  }
}

function getAgentStepStatusLabel(status: Api.Chat.AgentStepEvent['status']) {
  return {
    running: '进行中',
    completed: '已完成',
    failed: '已降级',
    cancelled: '已停止'
  }[status];
}

function getAgentStepDuration(step: Api.Chat.AgentStepEvent & { startedAt: number }) {
  if (step.status === 'running') return '';
  const duration = Math.max(0, step.timestamp - step.startedAt);
  if (duration < 1000) return '< 1s';
  return `${(duration / 1000).toFixed(duration < 10_000 ? 1 : 0)}s`;
}

function getRetrievalTrace(step: Api.Chat.AgentStepEvent) {
  return step.metadata?.retrievalTrace;
}

function getEvidenceAssessment(step: Api.Chat.AgentStepEvent) {
  return step.metadata?.evidenceAssessment;
}

function isRetrievalStageWarning(status: string) {
  return ['DEGRADED', 'FAILED', 'PARTIAL', 'CONFLICTED', 'INSUFFICIENT'].includes(status);
}

const retrievalStageLabels: Record<string, string> = {
  QUERY_PLANNING: '意图规划',
  PARALLEL_RECALL: '并行召回',
  RRF_FUSION: 'RRF 融合',
  RERANK: '相关性重排',
  PARENT_CONTEXT_ASSEMBLY: '父上下文扩展',
  EVIDENCE_VERIFY: '证据充分性判断'
};

function getRetrievalStageLabel(name: string) {
  if (name.startsWith('CORRECTIVE_RECALL_')) return `第 ${name.split('_').at(-1)} 轮纠正检索`;
  if (name.startsWith('EVIDENCE_VERIFY_')) return `第 ${name.split('_').at(-1)} 轮证据判断`;
  if (name.startsWith('RRF_FUSION_')) return `第 ${name.split('_').at(-1)} 轮 RRF 融合`;
  if (name.startsWith('RERANK_')) return `第 ${name.split('_').at(-1)} 轮相关性重排`;
  if (name.startsWith('PARENT_CONTEXT_ASSEMBLY_')) return `第 ${name.split('_').at(-1)} 轮父上下文扩展`;
  return retrievalStageLabels[name] || name;
}

function getToolLabel(tool: string) {
  return toolNameLabels[tool] || tool;
}

function getToolStatusLabel(status: Api.Chat.AgentToolEvent['status']) {
  return toolStatusLabels[status] || status;
}

function splitTrailingUrlPunctuation(rawUrl: string) {
  let url = rawUrl;
  let trailing = '';

  while (url) {
    const lastChar = url.at(-1);
    if (!lastChar) break;

    if (/[，。！？；：、,.!?;:]/.test(lastChar)) {
      trailing = `${lastChar}${trailing}`;
      url = url.slice(0, -1);
      continue;
    }

    if (lastChar === ')' || lastChar === '）') {
      const openingChar = lastChar === ')' ? '(' : '（';
      const closingChar = lastChar;
      const openingCount = (url.match(new RegExp(`\\${openingChar}`, 'g')) || []).length;
      const closingCount = (url.match(new RegExp(`\\${closingChar}`, 'g')) || []).length;

      if (closingCount > openingCount) {
        trailing = `${lastChar}${trailing}`;
        url = url.slice(0, -1);
        continue;
      }
    }

    break;
  }

  return { url, trailing };
}

function normalizeBareUrls(text: string) {
  return text.replace(bareUrlPattern, (match, offset: number, source: string) => {
    const previousChar = source[offset - 1] || '';
    const previousTwoChars = source.slice(Math.max(0, offset - 2), offset);
    const previousTenChars = source.slice(Math.max(0, offset - 10), offset).toLowerCase();

    if (previousChar === '<' || previousTwoChars === '](' || /(?:href|src)=["']?$/.test(previousTenChars)) {
      return match;
    }

    const { url, trailing } = splitTrailingUrlPunctuation(match);
    return url ? `<${url}>${trailing}` : match;
  });
}

function createSourceLink(
  sourceNum: string,
  fileName: string,
  extras?: { fileMd5?: string; pageNumber?: number; displayName?: string }
): string {
  const linkClass = 'source-file-link';
  const trimmedFileName = fileName.trim();
  const fileId = `source-file-${sourceFiles.value.length}`;
  const referenceNumber = parseInt(sourceNum, 10);

  sourceFiles.value.push({
    fileName: trimmedFileName,
    id: fileId,
    referenceNumber,
    fileMd5: extras?.fileMd5,
    pageNumber: extras?.pageNumber
  });

  return `来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${extras?.displayName || trimmedFileName}</span>`;
}

// 处理来源文件链接的函数
function processSourceLinks(text: string): string {
  // 重置来源文件列表，避免重复
  sourceFiles.value = [];

  // 支持单个来源，也支持一个括号里包含多个来源：
  // (来源#1: test.pdf | 第5页; 来源#2: other.pdf | 第8页)
  const entryBoundary = '(?=\\s*(?:[;；,，、。！？!?\\)）]|$))';
  const pagePattern = new RegExp(
    `来源#(\\d+):\\s*([^|;；,，、。！？!?\\n\\r]+?)\\s*\\|\\s*第(\\d+)页${entryBoundary}`,
    'g'
  );
  const md5Pattern = new RegExp(
    `来源#(\\d+):\\s*([^|;；,，、。！？!?\\n\\r]+?)\\s*\\|\\s*MD5:\\s*([a-fA-F0-9]+)${entryBoundary}`,
    'g'
  );
  const simplePattern = new RegExp(
    `来源#(\\d+):\\s*([^<>\\n\\r|;；,，、。！？!?]+?)${entryBoundary}`,
    'g'
  );

  let processedText = text.replace(pagePattern, (_match, sourceNum, fileName, pageNum) => {
    return createSourceLink(sourceNum, fileName, {
      pageNumber: parseInt(pageNum, 10),
      displayName: `${fileName.trim()} (第${pageNum}页)`
    });
  });

  processedText = processedText.replace(md5Pattern, (_match, sourceNum, fileName, fileMd5) => {
    return createSourceLink(sourceNum, fileName, {
      fileMd5: fileMd5.trim()
    });
  });

  processedText = processedText.replace(simplePattern, (_match, sourceNum, fileName) => {
    return createSourceLink(sourceNum, fileName);
  });

  return processedText;
}

const content = computed(() => {
  chatStore.scrollToBottom?.();
  const rawContent = props.msg.content ?? '';

  // 只对助手消息处理来源链接
  if (props.msg.role === 'assistant') {
    return normalizeBareUrls(processSourceLinks(rawContent));
  }

  return rawContent;
});

function extractContextAnchorText(target: HTMLElement) {
  const scope = target.closest('li, p, blockquote, td, th');
  const rawText = scope?.textContent?.replace(/\s+/g, ' ').trim() || '';
  if (!rawText) return '';

  const beforeCitation = rawText.split(/(?:\(|（)?来源#\d+:/)[0] || rawText;
  return beforeCitation
    .replace(/^\s*\d+\.\s*/, '')
    .replace(/[（(]\s*$/, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function openReferencePreviewPage(payload: {
  retrievalMode?: Api.Chat.ReferenceEvidence['retrievalMode'];
  retrievalLabel?: string | null;
  retrievalQuery?: string | null;
  evidenceSnippet?: string | null;
  matchedChunkText?: string | null;
  score?: number | null;
  chunkId?: number | null;
  fileName: string;
  fileMd5?: string | null;
  pageNumber?: number | null;
  anchorText?: string | null;
  sessionId?: string;
  referenceNumber: number;
}) {
  const previewKey = `reference-preview:${Date.now()}:${Math.random().toString(36).slice(2, 8)}`;
  localStorage.setItem(previewKey, JSON.stringify(payload));

  const routeLocation = router.resolve({
    path: '/chat',
    query: {
      preview: 'reference',
      previewKey
    }
  });

  window.open(routeLocation.href, '_blank', 'noopener,noreferrer');
}

// 处理内容点击事件（事件委托）
function handleContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement;

  // 检查点击的是否是文件链接
  if (target.classList.contains('source-file-link')) {
    const fileId = target.getAttribute('data-file-id');
    if (fileId) {
      const file = sourceFiles.value.find(f => f.id === fileId);
      if (file) {
        const contextAnchorText = extractContextAnchorText(target);
        handleSourceFileClick({
          fileName: file.fileName,
          referenceNumber: file.referenceNumber,
          fileMd5: file.fileMd5,
          anchorText: contextAnchorText
        });
      }
    }
  }
}

// 处理来源文件点击事件
async function handleSourceFileClick(fileInfo: {
  fileName: string;
  referenceNumber: number;
  fileMd5?: string;
  anchorText?: string;
}) {
  const { fileName, referenceNumber, fileMd5: extractedMd5, anchorText: clickedAnchorText } = fileInfo;
  const persistedDetail = props.msg.referenceMappings?.[String(referenceNumber)] || props.msg.referenceMappings?.[referenceNumber];
  const referenceSessionId = props.msg.generationId || props.msg.conversationId || props.sessionId;
  console.log('点击了来源文件:', fileName, '引用编号:', referenceNumber, '提取的MD5:', extractedMd5, '会话ID:', referenceSessionId);

  try {
    let detail: Api.Document.ReferenceDetailResponse | null = null;
    const fallbackRetrievalQuery = props.retrievalQueryFallback || '';

    if (referenceSessionId && (!persistedDetail?.retrievalQuery || !persistedDetail?.matchedChunkText || !persistedDetail?.evidenceSnippet)) {
      try {
        const { error: detailError, data: detailData } = await request<Api.Document.ReferenceDetailResponse>({
          url: 'documents/reference-detail',
          params: {
            sessionId: referenceSessionId,
            referenceNumber: referenceNumber.toString()
          }
        });

        if (!detailError && detailData?.fileMd5) {
          detail = detailData;
        }
      } catch (detailErr) {
        console.warn('通过API查询引用详情失败:', detailErr);
      }
    }

    if (persistedDetail?.fileMd5 && !detail) {
      openReferencePreviewPage({
        fileName: persistedDetail.fileName || fileName,
        fileMd5: persistedDetail.fileMd5,
        pageNumber: persistedDetail.pageNumber,
        anchorText: clickedAnchorText || persistedDetail.anchorText || '',
        retrievalMode: persistedDetail.retrievalMode,
        retrievalLabel: persistedDetail.retrievalLabel,
        retrievalQuery: persistedDetail.retrievalQuery || fallbackRetrievalQuery,
        evidenceSnippet: persistedDetail.evidenceSnippet,
        matchedChunkText: persistedDetail.matchedChunkText,
        score: persistedDetail.score,
        chunkId: persistedDetail.chunkId,
        sessionId: referenceSessionId,
        referenceNumber
      });
      return;
    }

    const targetMd5 = detail?.fileMd5 || extractedMd5 || null;
    openReferencePreviewPage({
      fileName: detail?.fileName || fileName,
      fileMd5: targetMd5,
      pageNumber: detail?.pageNumber,
      anchorText: clickedAnchorText || detail?.anchorText || '',
      retrievalMode: detail?.retrievalMode,
      retrievalLabel: detail?.retrievalLabel,
      retrievalQuery: detail?.retrievalQuery || fallbackRetrievalQuery,
      evidenceSnippet: detail?.evidenceSnippet,
      matchedChunkText: detail?.matchedChunkText,
      score: detail?.score,
      chunkId: detail?.chunkId,
      sessionId: referenceSessionId,
      referenceNumber
    });
  } catch (err) {
    console.error('文件下载失败:', err);
    window.$message?.error(`文件下载失败: ${fileName}`);
  }
}
</script>

<template>
  <div class="mb-8 flex-col gap-2">
    <div v-if="msg.role === 'user'" class="flex items-center gap-4">
      <NAvatar class="bg-success">
        <SvgIcon icon="ph:user-circle" class="text-icon-large color-white" />
      </NAvatar>
      <div class="flex-col gap-1">
        <NText class="text-4 font-bold">{{ msg.username || authStore.userInfo.username }}</NText>
        <NText class="text-3 color-gray-500">{{ formatDate(msg.timestamp) }}</NText>
      </div>
    </div>
    <div v-else class="flex items-center gap-4">
      <NAvatar class="bg-primary">
        <SystemLogo class="text-6 text-white" />
      </NAvatar>
      <div class="flex-col gap-1">
        <NText class="text-4 font-bold">知枢</NText>
        <NText class="text-3 color-gray-500">{{ formatDate(msg.timestamp) }}</NText>
      </div>
    </div>
    <div v-if="msg.role === 'assistant' && agentSteps.length > 0" class="agent-trace ml-12 mt-3">
      <button class="agent-trace__header" type="button" @click="traceExpanded = !traceExpanded">
        <span class="agent-trace__mark">
          <icon-ph:sparkle-fill />
        </span>
        <span class="agent-trace__heading">
          <span class="agent-trace__eyebrow">AGENT WORKFLOW</span>
          <span class="agent-trace__summary">{{ agentTraceSummary }}</span>
        </span>
        <span class="agent-trace__count">{{ agentSteps.length }} 步</span>
        <icon-material-symbols:keyboard-arrow-down-rounded
          class="agent-trace__arrow"
          :class="{ 'agent-trace__arrow--open': traceExpanded }"
        />
      </button>

      <Transition name="trace-fold">
        <div v-if="traceExpanded" class="agent-trace__body">
          <div
            v-for="step in agentSteps"
            :key="step.stepId"
            class="agent-step"
            :class="`agent-step--${step.status}`"
          >
            <div class="agent-step__rail">
              <span class="agent-step__node">
                <icon-eos-icons:three-dots-loading v-if="step.status === 'running'" />
                <icon-material-symbols:check-rounded v-else-if="step.status === 'completed'" />
                <icon-material-symbols:stop-rounded v-else-if="step.status === 'cancelled'" />
                <icon-material-symbols:priority-high-rounded v-else />
              </span>
            </div>
            <div class="agent-step__content">
              <div class="agent-step__topline">
                <span class="agent-step__title">{{ step.title }}</span>
                <span class="agent-step__status">{{ getAgentStepStatusLabel(step.status) }}</span>
                <span v-if="getAgentStepDuration(step)" class="agent-step__duration">
                  {{ getAgentStepDuration(step) }}
                </span>
              </div>
              <div v-if="step.detail" class="agent-step__detail">{{ step.detail }}</div>
              <div v-if="getRetrievalTrace(step)" class="retrieval-trace">
                <div class="retrieval-trace__overview">
                  <span class="retrieval-trace__badge">
                    {{ getRetrievalTrace(step)?.queryPlan.intent }}
                  </span>
                  <span>{{ getRetrievalTrace(step)?.queryPlan.variants.length }} 路查询</span>
                  <span>{{ getRetrievalTrace(step)?.totalLatencyMs }} ms</span>
                  <span v-if="getEvidenceAssessment(step)" class="retrieval-trace__badge">
                    {{ getEvidenceAssessment(step)?.status }}
                  </span>
                  <span class="font-mono">{{ getRetrievalTrace(step)?.traceId.slice(0, 8) }}</span>
                </div>
                <div class="retrieval-trace__queries">
                  <span
                    v-for="variant in getRetrievalTrace(step)?.queryPlan.variants || []"
                    :key="`${variant.type}-${variant.query}`"
                    class="retrieval-query"
                    :title="variant.purpose"
                  >
                    <b>{{ variant.type }}</b>{{ variant.query }}
                  </span>
                </div>
                <div class="retrieval-stages">
                  <div
                    v-for="stage in getRetrievalTrace(step)?.stages || []"
                    :key="stage.name"
                    class="retrieval-stage"
                  >
                    <span
                      class="retrieval-stage__dot"
                      :class="{ 'retrieval-stage__dot--degraded': isRetrievalStageWarning(stage.status) }"
                    />
                    <span class="retrieval-stage__name">{{ getRetrievalStageLabel(stage.name) }}</span>
                    <span class="retrieval-stage__count">{{ stage.inputCount }} → {{ stage.outputCount }}</span>
                    <span class="retrieval-stage__latency">{{ stage.latencyMs }} ms</span>
                  </div>
                </div>
                <div v-if="getRetrievalTrace(step)?.degradations.length" class="retrieval-trace__warning">
                  <icon-material-symbols:warning-outline-rounded />
                  已启用降级策略：{{ getRetrievalTrace(step)?.degradations.join('；') }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </div>
    <AgentRuntimePanel
      v-if="msg.role === 'assistant' && agentSteps.length > 0"
      class="ml-12 mt-3"
      :steps="agentSteps"
    />
    <div v-if="msg.role === 'assistant' && approvalStep" class="approval-card ml-12 mt-3">
      <div class="approval-card__heading">
        <span class="approval-card__icon"><icon-material-symbols:shield-lock-outline-rounded /></span>
        <div class="approval-card__heading-copy">
          <div class="approval-card__eyebrow">HUMAN APPROVAL</div>
          <div class="approval-card__title">Agent 请求执行 {{ getToolLabel(approvalStep.toolName || '') }}</div>
        </div>
        <span class="approval-card__risk">高风险操作</span>
      </div>
      <p class="approval-card__detail">
        {{ approvalStep.metadata?.ledgerMessage || '该操作可能产生外部副作用，只有得到你的明确授权后才会执行。' }}
      </p>
      <div class="approval-card__meta">
        <span>策略 {{ approvalStep.metadata?.replayPolicy || 'REQUIRES_APPROVAL' }}</span>
        <span class="font-mono">Ledger #{{ approvalToolLedgerId }}</span>
      </div>
      <div v-if="!approvalDecision" class="approval-card__actions">
        <NButton
          type="primary"
          size="small"
          :loading="approvalSubmitting"
          @click="handleToolApproval('APPROVE')"
        >
          允许执行
        </NButton>
        <NButton
          size="small"
          secondary
          type="error"
          :disabled="approvalSubmitting"
          @click="handleToolApproval('REJECT')"
        >
          拒绝操作
        </NButton>
      </div>
      <div v-else class="approval-card__decision">
        <icon-material-symbols:check-circle-outline-rounded />
        {{ approvalDecision === 'APPROVE' ? '已授权，正在新的运行中继续' : '已拒绝，正在生成安全替代方案' }}
      </div>
    </div>
    <div v-else-if="msg.role === 'assistant' && clarificationStep" class="clarification-card ml-12 mt-3">
      <div class="clarification-card__heading">
        <span class="clarification-card__icon"><icon-material-symbols:help-outline-rounded /></span>
        <div>
          <div class="clarification-card__eyebrow">CLARIFICATION</div>
          <div class="clarification-card__title">补充后会继续原任务，不会重新开始</div>
        </div>
      </div>
      <div v-if="clarificationOptions.length" class="clarification-card__options">
        <NButton
          v-for="option in clarificationOptions"
          :key="option"
          size="small"
          round
          secondary
          type="primary"
          @click="selectClarificationOption(option)"
        >
          {{ option }}
        </NButton>
      </div>
    </div>
    <div
      v-else-if="msg.role === 'assistant' && toolEvents.length > 0"
      class="ml-12 mt-3 flex flex-col gap-2"
    >
      <div
        v-for="event in toolEvents"
        :key="event.id || event.tool"
        class="tool-event"
        :class="`tool-event--${event.status}`"
      >
        <icon-eos-icons:three-dots-loading v-if="event.status === 'executing'" class="text-4" />
        <icon-material-symbols:check-circle-rounded v-else-if="event.status === 'success'" class="text-4" />
        <icon-material-symbols:error-rounded v-else class="text-4" />
        <span class="tool-event__name">{{ getToolLabel(event.tool) }}</span>
        <span class="tool-event__status">{{ getToolStatusLabel(event.status) }}</span>
      </div>
    </div>
    <NText v-if="msg.status === 'pending' || (msg.status === 'loading' && msg.role === 'assistant' && !msg.content)">
      <icon-eos-icons:three-dots-loading class="ml-12 mt-2 text-8" />
    </NText>
    <NText v-else-if="msg.status === 'error'" class="ml-12 mt-2 italic color-#d03050">
      {{ msg.content || '服务器繁忙，请稍后再试' }}
    </NText>
    <div v-else-if="msg.role === 'assistant'" class="mt-2 pl-12" @click="handleContentClick">
      <VueMarkdownIt :content="content" />
    </div>
    <NText v-else-if="msg.role === 'user'" class="ml-12 mt-2 text-4">{{ content }}</NText>
    <NDivider class="ml-12 w-[calc(100%-3rem)] mb-0! mt-2!" />
    <div class="ml-12 flex gap-2">
      <NButton quaternary title="复制回答" aria-label="复制回答" @click="handleCopy(msg.content)">
        <template #icon>
          <icon-mynaui:copy />
        </template>
      </NButton>
      <NButton
        v-if="msg.role === 'assistant'"
        quaternary
        title="点赞"
        aria-label="点赞"
        :type="msg.feedbackRating === 'good' ? 'primary' : 'default'"
        :loading="feedbackSubmitting[getMessageFeedbackKey(msg)]"
        @click="handleFeedback(msg, 'good')"
      >
        <template #icon>
          <icon-material-symbols:thumb-up-outline-rounded />
        </template>
      </NButton>
      <NButton
        v-if="canRetryRun"
        quaternary
        title="从 checkpoint 重新运行"
        aria-label="重新运行 Agent"
        :loading="retrySubmitting"
        @click="handleRetryRun"
      >
        <template #icon>
          <icon-material-symbols:replay-rounded />
        </template>
        重新运行
      </NButton>
      <NButton
        v-if="msg.role === 'assistant'"
        quaternary
        title="点踩"
        aria-label="点踩"
        :type="msg.feedbackRating === 'bad' ? 'error' : 'default'"
        :loading="feedbackSubmitting[getMessageFeedbackKey(msg)]"
        @click="handleFeedback(msg, 'bad')"
      >
        <template #icon>
          <icon-material-symbols:thumb-down-outline-rounded />
        </template>
      </NButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
.approval-card {
  max-width: 680px;
  border: 1px solid rgb(245 158 11 / 0.32);
  border-radius: 16px;
  background:
    radial-gradient(circle at 100% 0, rgb(245 158 11 / 0.12), transparent 42%),
    rgb(var(--card-color));
  padding: 15px 16px;
  box-shadow: 0 10px 30px rgb(120 53 15 / 0.07);
}

.approval-card__heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.approval-card__icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 11px;
  background: rgb(245 158 11 / 0.14);
  color: #d97706;
  font-size: 19px;
}

.approval-card__heading-copy {
  min-width: 0;
  flex: 1;
}

.approval-card__eyebrow {
  color: #d97706;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.15em;
}

.approval-card__title {
  margin-top: 2px;
  font-size: 13px;
  font-weight: 650;
  color: rgb(var(--text-color));
}

.approval-card__risk {
  flex: 0 0 auto;
  border-radius: 999px;
  background: rgb(245 158 11 / 0.12);
  padding: 4px 8px;
  color: #b45309;
  font-size: 10px;
  font-weight: 650;
}

.approval-card__detail {
  margin: 12px 0 0;
  color: rgb(var(--text-color-2));
  font-size: 12px;
  line-height: 1.65;
}

.approval-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;
  color: rgb(var(--text-color-3));
  font-size: 10px;
}

.approval-card__meta span {
  border: 1px solid rgb(var(--border-color) / 0.18);
  border-radius: 999px;
  padding: 3px 7px;
}

.approval-card__actions {
  display: flex;
  gap: 8px;
  margin-top: 13px;
}

.approval-card__decision {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 13px;
  color: #18a058;
  font-size: 12px;
  font-weight: 600;
}

.clarification-card {
  max-width: 680px;
  border: 1px solid rgb(var(--primary-color) / 0.18);
  border-radius: 16px;
  background: linear-gradient(135deg, rgb(var(--primary-color) / 0.08), rgb(var(--primary-color) / 0.025));
  padding: 14px 16px;
}

.clarification-card__heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.clarification-card__icon {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 10px;
  background: rgb(var(--primary-color) / 0.12);
  color: rgb(var(--primary-color));
  font-size: 18px;
}

.clarification-card__eyebrow {
  color: rgb(var(--primary-color));
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.clarification-card__title {
  margin-top: 2px;
  color: rgb(var(--text-color));
  font-size: 13px;
  font-weight: 600;
}

.clarification-card__options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

:deep(.source-file-link) {
  color: #1890ff;
  cursor: pointer;
  text-decoration: underline;
  transition: color 0.2s;

  &:hover {
    color: #40a9ff;
    text-decoration: none;
  }

  &:active {
    color: #096dd9;
  }
}

.tool-event {
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  align-items: center;
  gap: 8px;
  border: 1px solid rgb(var(--border-color) / 0.18);
  border-radius: 6px;
  background: rgb(var(--card-color));
  padding: 5px 9px;
  font-size: 12px;
  line-height: 18px;
  color: rgb(var(--text-color-2));
}

.tool-event__name {
  font-weight: 500;
  color: rgb(var(--text-color));
}

.tool-event__status {
  color: rgb(var(--text-color-3));
}

.tool-event--success {
  border-color: rgb(24 160 88 / 0.25);
  color: #18a058;
}

.tool-event--failed {
  border-color: rgb(208 48 80 / 0.25);
  color: #d03050;
}

.agent-trace {
  max-width: 680px;
  overflow: hidden;
  border: 1px solid rgb(var(--primary-color) / 0.16);
  border-radius: 14px;
  background:
    radial-gradient(circle at 0 0, rgb(var(--primary-color) / 0.09), transparent 42%),
    rgb(var(--card-color) / 0.78);
  box-shadow: 0 8px 28px rgb(15 23 42 / 0.06);
  backdrop-filter: blur(12px);
}

.agent-trace__header {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  padding: 12px 14px;
  color: rgb(var(--text-color));
  text-align: left;
  cursor: pointer;
}

.agent-trace__mark {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(135deg, rgb(var(--primary-color)), #7c3aed);
  color: white;
  box-shadow: 0 5px 14px rgb(var(--primary-color) / 0.24);
}

.agent-trace__heading {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.agent-trace__eyebrow {
  font-size: 9px;
  font-weight: 700;
  line-height: 12px;
  letter-spacing: 0.12em;
  color: rgb(var(--primary-color));
}

.agent-trace__summary {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-trace__count {
  flex: 0 0 auto;
  border-radius: 999px;
  background: rgb(var(--primary-color) / 0.09);
  padding: 3px 8px;
  font-size: 10px;
  font-weight: 600;
  color: rgb(var(--primary-color));
}

.agent-trace__arrow {
  flex: 0 0 auto;
  font-size: 18px;
  color: rgb(var(--text-color-3));
  transition: transform 0.2s ease;
}

.agent-trace__arrow--open {
  transform: rotate(180deg);
}

.agent-trace__body {
  border-top: 1px solid rgb(var(--border-color) / 0.12);
  padding: 10px 14px 12px;
}

.agent-step {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 9px;
  color: rgb(var(--text-color-2));
}

.agent-step__rail {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 2px;
}

.agent-step:not(:last-child) .agent-step__rail::after {
  position: absolute;
  top: 24px;
  bottom: 0;
  width: 1px;
  background: linear-gradient(rgb(var(--primary-color) / 0.24), rgb(var(--border-color) / 0.16));
  content: '';
}

.agent-step__node {
  z-index: 1;
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  border: 1px solid rgb(24 160 88 / 0.22);
  border-radius: 50%;
  background: rgb(24 160 88 / 0.1);
  color: #18a058;
  font-size: 12px;
}

.agent-step__content {
  min-width: 0;
  padding-bottom: 13px;
}

.retrieval-trace {
  margin-top: 9px;
  border: 1px solid rgb(var(--primary-color) / 0.13);
  border-radius: 10px;
  background: rgb(var(--body-color) / 0.48);
  padding: 9px 10px;
}

.retrieval-trace__overview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
  font-size: 10px;
  color: rgb(var(--text-color-3));
}

.retrieval-trace__badge {
  border-radius: 5px;
  background: rgb(var(--primary-color) / 0.12);
  padding: 2px 6px;
  font-weight: 700;
  color: rgb(var(--primary-color));
}

.retrieval-trace__queries {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}

.retrieval-query {
  display: inline-flex;
  max-width: 100%;
  gap: 5px;
  border: 1px solid rgb(var(--border-color) / 0.2);
  border-radius: 6px;
  background: rgb(var(--card-color) / 0.65);
  padding: 3px 7px;
  font-size: 10px;
  color: rgb(var(--text-color-2));
}

.retrieval-query b {
  color: rgb(var(--primary-color));
  font-size: 9px;
}

.retrieval-stages {
  display: grid;
  gap: 5px;
  margin-top: 9px;
}

.retrieval-stage {
  display: grid;
  grid-template-columns: 8px minmax(90px, 1fr) auto auto;
  align-items: center;
  gap: 7px;
  font-size: 10px;
}

.retrieval-stage__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #18a058;
  box-shadow: 0 0 0 3px rgb(24 160 88 / 0.1);
}

.retrieval-stage__dot--degraded {
  background: #f0a020;
  box-shadow: 0 0 0 3px rgb(240 160 32 / 0.1);
}

.retrieval-stage__name {
  font-weight: 600;
  color: rgb(var(--text-color-2));
}

.retrieval-stage__count,
.retrieval-stage__latency {
  color: rgb(var(--text-color-3));
  font-variant-numeric: tabular-nums;
}

.retrieval-trace__warning {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 8px;
  border-radius: 6px;
  background: rgb(240 160 32 / 0.09);
  padding: 6px 7px;
  font-size: 10px;
  color: #d48806;
}

.agent-step:last-child .agent-step__content {
  padding-bottom: 1px;
}

.agent-step__topline {
  display: flex;
  min-height: 22px;
  align-items: center;
  gap: 7px;
}

.agent-step__title {
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  color: rgb(var(--text-color));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-step__status,
.agent-step__duration {
  flex: 0 0 auto;
  font-size: 10px;
  color: rgb(var(--text-color-3));
}

.agent-step__detail {
  margin-top: 1px;
  overflow: hidden;
  font-size: 11px;
  line-height: 17px;
  color: rgb(var(--text-color-3));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-step--running .agent-step__node {
  border-color: rgb(var(--primary-color) / 0.28);
  background: rgb(var(--primary-color) / 0.12);
  color: rgb(var(--primary-color));
  box-shadow: 0 0 0 4px rgb(var(--primary-color) / 0.06);
}

.agent-step--running .agent-step__status {
  color: rgb(var(--primary-color));
}

.agent-step--failed .agent-step__node {
  border-color: rgb(245 158 11 / 0.3);
  background: rgb(245 158 11 / 0.12);
  color: #d97706;
}

.agent-step--cancelled .agent-step__node {
  border-color: rgb(var(--border-color) / 0.24);
  background: rgb(var(--border-color) / 0.12);
  color: rgb(var(--text-color-3));
}

.trace-fold-enter-active,
.trace-fold-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
  transform-origin: top;
}

.trace-fold-enter-from,
.trace-fold-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 640px) {
  .agent-trace__count,
  .agent-step__duration {
    display: none;
  }
}
</style>
