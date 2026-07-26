<script setup lang="ts">
import { $t } from '@/locales';

defineOptions({
  name: 'GlobalLogo'
});

interface Props {
  /** Whether to show the title */
  showTitle?: boolean;
}

withDefaults(defineProps<Props>(), {
  showTitle: true
});
</script>

<template>
  <RouterLink to="/" class="w-full flex-center nowrap-hidden" aria-label="返回知枢工作台">
    <SystemLogo class="text-32px text-primary" />
    <div v-show="showTitle" class="ml-9px min-w-0 transition duration-300 ease-in-out">
      <h2 class="m-0 text-16px text-[var(--zs-ink-primary)] font-700 leading-5">
        {{ $t('system.title') }}
      </h2>
      <div class="text-8px text-[var(--zs-ink-secondary)] font-700 tracking-[0.16em]">KNOWLEDGE AGENT</div>
    </div>
  </RouterLink>
</template>

<style scoped></style>
