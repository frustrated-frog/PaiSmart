<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import type { MenuOption } from 'naive-ui';
import { SimpleScrollbar } from '@sa/materials';
import { GLOBAL_SIDER_MENU_ID } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { useRouteStore } from '@/store/modules/route';
import { useRouterPush } from '@/hooks/common/router';
import { useMenu } from '../../../context';
import { groupDesktopMenus } from '../menu-groups';

defineOptions({
  name: 'VerticalMenu'
});

const route = useRoute();
const appStore = useAppStore();
const themeStore = useThemeStore();
const routeStore = useRouteStore();
const { routerPushByKeyWithMetaQuery } = useRouterPush();
const { selectedKey } = useMenu();

const inverted = computed(() => !themeStore.darkMode && themeStore.sider.inverted);
const desktopMenuOptions = computed<MenuOption[]>(() => {
  if (appStore.siderCollapse) {
    return routeStore.menus as unknown as MenuOption[];
  }

  return groupDesktopMenus(routeStore.menus).map(group => ({
    type: 'group',
    key: group.key,
    label: group.label,
    children: group.children as unknown as MenuOption[]
  }));
});

const expandedKeys = ref<string[]>([]);

function updateExpandedKeys() {
  if (appStore.siderCollapse || !selectedKey.value) {
    expandedKeys.value = [];
    return;
  }
  expandedKeys.value = routeStore.getSelectedMenuKeyPath(selectedKey.value);
}

watch(
  () => route.name,
  () => {
    updateExpandedKeys();
  },
  { immediate: true }
);
</script>

<template>
  <Teleport :to="`#${GLOBAL_SIDER_MENU_ID}`">
    <div class="knowledge-menu-shell">
      <SimpleScrollbar class="knowledge-menu-scroll">
        <div v-if="!appStore.siderCollapse" class="px-5 pb-3 pt-4">
          <div class="text-9px text-[var(--zs-signal-cyan)] font-700 tracking-[0.18em]">KNOWLEDGE OPS</div>
          <div class="mt-1 text-11px text-[var(--zs-ink-secondary)]">企业知识智能工作台</div>
        </div>
        <NMenu
          v-model:expanded-keys="expandedKeys"
          mode="vertical"
          :value="selectedKey"
          :collapsed="appStore.siderCollapse"
          :collapsed-width="themeStore.sider.collapsedWidth"
          :collapsed-icon-size="22"
          :options="desktopMenuOptions"
          :inverted="inverted"
          :indent="18"
          @update:value="routerPushByKeyWithMetaQuery"
        />
      </SimpleScrollbar>
      <div
        v-if="!appStore.isMobile"
        class="navigation-collapse-footer"
        :class="{ 'navigation-collapse-footer--collapsed': appStore.siderCollapse }"
      >
        <span v-if="!appStore.siderCollapse" class="navigation-collapse-footer__label">收起导航</span>
        <MenuToggler
          :collapsed="appStore.siderCollapse"
          :aria-label="appStore.siderCollapse ? '展开导航' : '收起导航'"
          :title="appStore.siderCollapse ? '展开导航' : '收起导航'"
          @click="appStore.toggleSiderCollapse"
        />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.knowledge-menu-shell {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.knowledge-menu-scroll {
  min-height: 0;
  flex: 1;
}

.navigation-collapse-footer {
  display: flex;
  height: 44px;
  flex: 0 0 44px;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  border-top: 1px solid var(--zs-border);
  padding: 0 10px 0 16px;
  color: var(--zs-ink-secondary);
  background: var(--zs-surface-panel);
}

.navigation-collapse-footer--collapsed {
  justify-content: center;
  padding: 0;
}

.navigation-collapse-footer__label {
  font-size: 10px;
  letter-spacing: 0.04em;
}

:deep(.n-menu-item-group-title) {
  padding: 14px 20px 6px !important;
  color: var(--zs-ink-secondary) !important;
  font-size: 10px !important;
  font-weight: 650 !important;
  letter-spacing: 0.08em;
}

:deep(.n-menu-item-content) {
  margin: 2px 10px;
  border-radius: 8px;
}

:deep(.n-menu-item-content--selected) {
  background: rgb(86 87 217 / 10%);
}
</style>
