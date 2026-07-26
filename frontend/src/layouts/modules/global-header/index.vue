<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useFullscreen } from '@vueuse/core';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import GlobalSearch from '../global-search/index.vue';
import ThemeButton from './components/theme-button.vue';
import UserAvatar from './components/user-avatar.vue';

defineOptions({
  name: 'GlobalHeader'
});

interface Props {
  /** Whether to show the logo */
  // showLogo?: App.Global.HeaderProps['showLogo'];
  /** Whether to show the menu toggler */
  showMenuToggler?: App.Global.HeaderProps['showMenuToggler'];
  /** Whether to show the menu */
  // showMenu?: App.Global.HeaderProps['showMenu'];
}

defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();
const route = useRoute();
const { isFullscreen, toggle } = useFullscreen();

const isDev = import.meta.env.DEV;

const pageContextMap: Record<string, { eyebrow: string; title: string; description: string }> = {
  chat: { eyebrow: 'KNOWLEDGE AGENT', title: '智能问答', description: '基于企业知识与可观测 Agent 工作流生成回答' },
  'chat-history': { eyebrow: 'CONVERSATION ARCHIVE', title: '聊天记录', description: '检索、审计并复盘历史会话' },
  'knowledge-base': {
    eyebrow: 'KNOWLEDGE PIPELINE',
    title: '知识库',
    description: '管理文档从解析、分块到索引的完整生命周期'
  },
  'model-provider': { eyebrow: 'MODEL RUNTIME', title: '模型配置', description: '管理推理模型连接、能力与运行状态' },
  'usage-monitor': { eyebrow: 'AGENT OBSERVABILITY', title: '用量监控', description: '跟踪 Token、请求量与运行成本' },
  user: { eyebrow: 'ACCESS CONTROL', title: '用户管理', description: '管理成员身份、角色与访问权限' },
  'org-tag': { eyebrow: 'TENANT GOVERNANCE', title: '组织标签', description: '维护知识隔离与组织边界' },
  'invite-code': { eyebrow: 'ACCESS CONTROL', title: '邀请码管理', description: '控制新成员进入与注册码状态' },
  'personal-center': { eyebrow: 'ACCOUNT', title: '个人中心', description: '维护个人资料与账户偏好' },
  recharge: { eyebrow: 'ACCOUNT', title: '余额充值', description: '管理模型调用余额' },
  'recharge-manage': { eyebrow: 'ACCOUNT', title: '充值管理', description: '查看和处理充值记录' }
};

const pageContext = computed(
  () =>
    pageContextMap[String(route.name)] || {
      eyebrow: 'ZHISHU CONSOLE',
      title: String(route.meta.title || '工作台'),
      description: '企业知识智能工作台'
    }
);
</script>

<template>
  <DarkModeContainer class="h-full flex-y-center justify-between bg-transparent">
    <!-- <GlobalLogo v-if="showLogo" class="h-full" :style="{ width: themeStore.sider.width + 'px' }" /> -->
    <MenuToggler
      v-if="showMenuToggler && appStore.isMobile"
      :collapsed="appStore.siderCollapse"
      @click="appStore.toggleSiderCollapse"
    />
    <div id="header-extra" class="min-w-0 flex flex-1 items-center px-6">
      <div class="min-w-0">
        <div class="text-8px text-[var(--zs-signal-cyan)] font-750 tracking-[0.16em]">
          {{ pageContext.eyebrow }}
        </div>
        <div class="mt-0.5 flex items-baseline gap-3">
          <h1 class="m-0 truncate text-16px text-[var(--zs-ink-primary)] font-700">{{ pageContext.title }}</h1>
          <span class="truncate text-11px text-[var(--zs-ink-secondary)]">{{ pageContext.description }}</span>
        </div>
      </div>
    </div>
    <!--
    <div v-if="showMenu" :id="GLOBAL_HEADER_MENU_ID" class="h-full flex-y-center flex-1-hidden"></div>
    <div v-else class="h-full flex-y-center flex-1-hidden">
      <GlobalBreadcrumb v-if="!appStore.isMobile" class="ml-12px" />
    </div>
-->
    <div class="h-full flex-y-center justify-end pr-5">
      <GlobalSearch />
      <FullScreen v-if="!appStore.isMobile" :full="isFullscreen" @click="toggle" />
      <LangSwitch
        v-if="themeStore.header.multilingual.visible"
        :lang="appStore.locale"
        :lang-options="appStore.localeOptions"
        @change-lang="appStore.changeLocale"
      />
      <ThemeSchemaSwitch
        :theme-schema="themeStore.themeScheme"
        :is-dark="themeStore.darkMode"
        @switch="themeStore.toggleThemeScheme"
      />
      <ThemeButton v-if="isDev" />
      <UserAvatar />
    </div>
  </DarkModeContainer>
</template>

<style scoped></style>
