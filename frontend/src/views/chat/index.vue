<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';
import ConversationSidebar from './modules/conversation-sidebar.vue';
import ReferencePreviewPage from './modules/reference-preview-page.vue';

const route = useRoute();
const showReferencePreview = computed(() => route.query.preview === 'reference');
const sidebarCollapsed = ref(false);
</script>

<template>
  <div v-if="showReferencePreview" class="h-full">
    <ReferencePreviewPage />
  </div>
  <div v-else class="h-full bg-layout p-3">
    <div class="chat-workspace" :class="{ 'chat-workspace--sidebar-open': !sidebarCollapsed }">
      <ConversationSidebar v-model:collapsed="sidebarCollapsed" />
      <section class="chat-workspace__main" :class="{ 'chat-workspace__main--sidebar-collapsed': sidebarCollapsed }">
        <button
          v-show="sidebarCollapsed"
          class="chat-workspace__expand-button"
          aria-label="展开对话列表"
          @click="sidebarCollapsed = false"
        >
          <icon-material-symbols:left-panel-open-outline-rounded class="text-18px" />
        </button>
        <ChatList />
        <InputBox />
      </section>
    </div>
  </div>
</template>

<style scoped>
.chat-workspace {
  display: flex;
  height: 100%;
  min-width: 0;
  overflow: hidden;
}

.chat-workspace--sidebar-open {
  gap: 12px;
}

.chat-workspace__main {
  position: relative;
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--zs-border);
  border-radius: 16px;
  background: var(--zs-surface-panel);
  box-shadow: var(--zs-shadow-workspace);
}

.chat-workspace__main--sidebar-collapsed :deep(.chat-toolbar) {
  padding-left: 60px;
}

.chat-workspace__expand-button {
  position: absolute;
  z-index: 20;
  top: 12px;
  left: 12px;
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--zs-border);
  border-radius: 11px;
  color: var(--zs-ink-secondary);
  background: var(--zs-surface-panel);
  box-shadow: 0 6px 18px rgb(15 23 42 / 9%);
  cursor: pointer;
  transition:
    color 160ms ease,
    transform 160ms cubic-bezier(0.23, 1, 0.32, 1),
    border-color 160ms ease;
}

.chat-workspace__expand-button:hover {
  border-color: rgb(86 87 217 / 28%);
  color: var(--zs-knowledge-indigo);
}

.chat-workspace__expand-button:active {
  transform: scale(0.97);
}
</style>
