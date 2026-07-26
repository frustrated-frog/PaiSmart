<script setup lang="ts">
import { computed } from 'vue';
import type { Component } from 'vue';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import PwdLogin from './modules/pwd-login.vue';
import CodeLogin from './modules/code-login.vue';
import Register from './modules/register.vue';
import ResetPwd from './modules/reset-pwd.vue';
import BindWechat from './modules/bind-wechat.vue';

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: string;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const retrievalPipeline = [
  { key: 'query', label: 'Query', detail: '意图识别与 Multi-query 改写' },
  { key: 'recall', label: 'BM25 + Vector', detail: '关键词与语义双路召回' },
  { key: 'fusion', label: 'RRF', detail: '跨通道排名融合' },
  { key: 'rerank', label: 'Rerank', detail: '轻量模型精排' },
  { key: 'evidence', label: 'Evidence', detail: '证据评估与引用' }
];

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);
const isRegisterModule = computed(() => (props.module || 'pwd-login') === 'register');
const activeDescription = computed(() => {
  if (isRegisterModule.value) {
    return '创建团队账号，开始构建可检索、可追溯的企业知识。';
  }
  return '进入知识智能工作台，继续你的 Agent 与知识库任务。';
});
</script>

<template>
  <div class="login-shell" :class="{ 'login-shell--register': isRegisterModule }">
    <div class="login-shell__grid" aria-hidden="true" />

    <main class="login-frame">
      <section class="login-story">
        <header class="login-brand">
          <SystemLogo class="text-38px text-primary" />
          <div>
            <div class="login-brand__name">{{ $t('system.title') }}</div>
            <div class="login-brand__tag">KNOWLEDGE AGENT</div>
          </div>
        </header>

        <div class="login-story__copy">
          <div class="login-story__eyebrow">TRACEABLE ANSWERS · CONTROLLED RUNS</div>
          <h1>
            让每一次回答，
            <br />
            都能回到证据与执行过程
          </h1>
          <p>知枢把企业文档、混合检索与 Agent 工作流放进同一个控制台，答案不只正确，还能解释为什么。</p>
        </div>

        <div class="retrieval-rail" aria-label="Agentic RAG 检索执行轨道">
          <div v-for="(stage, index) in retrievalPipeline" :key="stage.key" class="retrieval-rail__stage">
            <span class="retrieval-rail__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="retrieval-rail__node">
              <i />
            </span>
            <span class="retrieval-rail__copy">
              <b>{{ stage.label }}</b>
              <small>{{ stage.detail }}</small>
            </span>
          </div>
        </div>

        <footer class="login-story__footer">
          <span>
            <i />
            Agent Runtime Ready
          </span>
          <span>Hybrid Retrieval</span>
          <span>Evidence Grounded</span>
        </footer>
      </section>

      <section class="login-auth">
        <div class="login-auth__tools">
          <ThemeSchemaSwitch
            :theme-schema="themeStore.themeScheme"
            :show-tooltip="false"
            class="text-18px"
            @switch="themeStore.toggleThemeScheme"
          />
          <LangSwitch
            v-if="themeStore.header.multilingual.visible"
            :lang="appStore.locale"
            :lang-options="appStore.localeOptions"
            :show-tooltip="false"
            @change-lang="appStore.changeLocale"
          />
        </div>

        <div class="login-auth__card" :class="{ 'login-auth__card--register': isRegisterModule }">
          <div class="login-auth__heading">
            <span>{{ isRegisterModule ? 'CREATE WORKSPACE ACCESS' : 'WELCOME BACK' }}</span>
            <h2>{{ $t(activeModule.label) }}</h2>
            <p>{{ activeDescription }}</p>
          </div>

          <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
            <component :is="activeModule.component" />
          </Transition>

          <div class="login-auth__security">
            <icon-solar:shield-keyhole-minimalistic-line-duotone />
            <span>登录凭据仅用于身份校验，知枢不会在浏览器中保存密码。</span>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped lang="scss">
.login-shell {
  position: relative;
  min-width: 1080px;
  min-height: 100%;
  overflow: hidden;
  color: var(--zs-ink-primary);
  background: var(--zs-surface-canvas);
}

.login-shell__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--zs-border) 1px, transparent 1px),
    linear-gradient(90deg, var(--zs-border) 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: linear-gradient(90deg, rgb(0 0 0 / 68%), transparent 72%);
  opacity: 0.28;
}

.login-frame {
  position: relative;
  z-index: 1;
  display: grid;
  width: min(1280px, calc(100vw - 88px));
  min-height: 100vh;
  grid-template-columns: minmax(560px, 1.25fr) minmax(420px, 0.75fr);
  margin: 0 auto;
}

.login-shell--register .login-frame {
  grid-template-columns: minmax(430px, 0.72fr) minmax(700px, 1.28fr);
}

.login-story,
.login-auth {
  min-height: 100vh;
}

.login-story {
  display: flex;
  flex-direction: column;
  padding: 42px 58px 34px 16px;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 11px;
}

.login-brand__name {
  font-family: 'Avenir Next', 'SF Pro Display', 'PingFang SC', sans-serif;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.1;
}

.login-brand__tag,
.login-story__eyebrow,
.login-auth__heading > span {
  color: var(--zs-signal-cyan);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.login-brand__tag {
  margin-top: 4px;
  color: var(--zs-ink-secondary);
}

.login-story__copy {
  max-width: 680px;
  margin-top: clamp(70px, 11vh, 118px);
}

.login-story__copy h1 {
  margin: 13px 0 0;
  font-family: 'Avenir Next', 'SF Pro Display', 'PingFang SC', sans-serif;
  font-size: clamp(36px, 3.6vw, 52px);
  font-weight: 650;
  letter-spacing: -0.035em;
  line-height: 1.18;
}

.login-story__copy p {
  max-width: 590px;
  margin: 18px 0 0;
  color: var(--zs-ink-secondary);
  font-size: 13px;
  line-height: 1.9;
}

.retrieval-rail {
  position: relative;
  display: grid;
  max-width: 690px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-top: 48px;
}

.retrieval-rail::before {
  position: absolute;
  top: 31px;
  right: 10%;
  left: 10%;
  height: 1px;
  background: linear-gradient(90deg, var(--zs-knowledge-indigo), var(--zs-signal-cyan));
  content: '';
  opacity: 0.45;
}

.retrieval-rail__stage {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.retrieval-rail__index {
  color: var(--zs-ink-secondary);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}

.retrieval-rail__node {
  z-index: 1;
  display: grid;
  width: 24px;
  height: 24px;
  margin-top: 8px;
  place-items: center;
  border: 1px solid rgb(86 87 217 / 34%);
  border-radius: 50%;
  background: var(--zs-surface-canvas);
}

.retrieval-rail__node i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zs-knowledge-indigo);
  box-shadow: 0 0 0 4px rgb(86 87 217 / 10%);
}

.retrieval-rail__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
  margin-top: 10px;
}

.retrieval-rail__copy b {
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 10px;
  font-weight: 650;
}

.retrieval-rail__copy small {
  padding: 0 5px;
  color: var(--zs-ink-secondary);
  font-size: 8px;
  line-height: 1.45;
}

.login-story__footer {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: auto;
  color: var(--zs-ink-secondary);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}

.login-story__footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--zs-border);
  border-radius: 999px;
  padding: 5px 8px;
  background: var(--zs-surface-panel);
}

.login-story__footer i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--zs-evidence-emerald);
}

.login-auth {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border-left: 1px solid var(--zs-border);
  padding: 72px 38px 48px;
  background: var(--zs-surface-panel);
}

.login-auth__tools {
  position: absolute;
  top: 28px;
  right: 4px;
  display: flex;
  gap: 8px;
}

.login-auth__card {
  width: min(400px, 100%);
}

.login-auth__card--register {
  width: min(760px, 100%);
}

.login-auth__heading {
  margin-bottom: 26px;
}

.login-auth__heading h2 {
  margin: 8px 0 0;
  font-family: 'Avenir Next', 'SF Pro Display', 'PingFang SC', sans-serif;
  font-size: 25px;
  font-weight: 650;
}

.login-auth__heading p {
  margin: 7px 0 0;
  color: var(--zs-ink-secondary);
  font-size: 11px;
  line-height: 1.6;
}

.login-auth__security {
  display: flex;
  align-items: center;
  gap: 7px;
  border-top: 1px solid var(--zs-border);
  margin-top: 24px;
  padding-top: 14px;
  color: var(--zs-ink-secondary);
  font-size: 9px;
  line-height: 1.5;
}

.login-auth__security > :first-child {
  flex: 0 0 auto;
  color: var(--zs-evidence-emerald);
  font-size: 16px;
}
</style>
