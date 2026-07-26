<script setup lang="tsx">
import { onMounted, ref } from 'vue';
import { NButton, NEmpty } from 'naive-ui';
import { getProviderPresentation } from './provider-presentation';

interface ConnectivityViewState {
  testing: boolean;
  success?: boolean;
  message?: string;
  latencyMs?: number;
}

const modelProvidersLoading = ref(false);
const modelProvidersSaving = ref(false);
const modelProviders = ref<Api.Admin.ModelProviderSettings | null>(null);
const connectivityStates = ref<Record<string, ConnectivityViewState>>({});
const scopeKeys = ['llm', 'embedding'] as const;

function cloneProviderItem(item: Api.Admin.ModelProviderItem): Api.Admin.ModelProviderItem {
  return {
    provider: item.provider,
    displayName: item.displayName,
    apiStyle: item.apiStyle,
    apiBaseUrl: item.apiBaseUrl,
    model: item.model,
    dimension: item.dimension ?? null,
    enabled: Boolean(item.enabled),
    active: Boolean(item.active),
    hasApiKey: Boolean(item.hasApiKey),
    maskedApiKey: item.maskedApiKey || '',
    apiKeyInput: ''
  };
}

function cloneModelProviderScope(payload: Api.Admin.ModelProviderScopeSettings): Api.Admin.ModelProviderScopeSettings {
  return {
    scope: payload.scope,
    activeProvider: payload.activeProvider,
    providers: (payload.providers || []).map(cloneProviderItem)
  };
}

function cloneModelProviderSettings(
  payload?: Api.Admin.ModelProviderSettings | null
): Api.Admin.ModelProviderSettings | null {
  if (!payload) {
    return null;
  }
  return {
    llm: cloneModelProviderScope(payload.llm),
    embedding: cloneModelProviderScope(payload.embedding)
  };
}

function getConnectivityKey(scopeKey: 'llm' | 'embedding', provider: Api.Admin.ModelProviderItem) {
  return `${scopeKey}:${provider.provider}`;
}

function getActiveProvider(scope: Api.Admin.ModelProviderScopeSettings) {
  return scope.providers.find(item => item.provider === scope.activeProvider);
}

async function getModelProviders() {
  modelProvidersLoading.value = true;
  const { error, data } = await request<Api.Admin.ModelProviderSettings>({
    url: '/admin/model-providers'
  });

  if (!error && data) {
    modelProviders.value = cloneModelProviderSettings(data);
  }
  modelProvidersLoading.value = false;
}

function buildProviderPayload(scope: Api.Admin.ModelProviderScopeSettings) {
  return {
    activeProvider: scope.activeProvider,
    providers: scope.providers.map(item => ({
      provider: item.provider,
      apiBaseUrl: item.apiBaseUrl,
      model: item.model,
      apiKey: item.apiKeyInput?.trim() || '',
      dimension: scope.scope === 'embedding' ? item.dimension : null,
      enabled: item.enabled
    }))
  };
}

async function submitModelProviders(scopeKey: 'llm' | 'embedding') {
  const scope = modelProviders.value?.[scopeKey];
  if (!scope) {
    return;
  }

  modelProvidersSaving.value = true;
  const { error, data } = await request<Api.Admin.ModelProviderScopeSettings>({
    url: `/admin/model-providers/${scopeKey}`,
    method: 'put',
    data: buildProviderPayload(scope)
  });

  if (!error && data && modelProviders.value) {
    modelProviders.value[scopeKey] = cloneModelProviderScope(data);
    window.$message?.success(scopeKey === 'llm' ? 'LLM 模型配置已更新' : 'Embedding 配置已更新');
  }
  modelProvidersSaving.value = false;
}

async function testModelProvider(scopeKey: 'llm' | 'embedding', provider: Api.Admin.ModelProviderItem) {
  const stateKey = getConnectivityKey(scopeKey, provider);
  connectivityStates.value[stateKey] = { testing: true };

  const { error, data } = await request<Api.Admin.ConnectivityTestResult>({
    url: `/admin/model-providers/${scopeKey}/test`,
    method: 'post',
    data: {
      provider: provider.provider,
      apiBaseUrl: provider.apiBaseUrl,
      model: provider.model,
      apiKey: provider.apiKeyInput?.trim() || '',
      dimension: scopeKey === 'embedding' ? provider.dimension : null
    }
  });

  if (error || !data) {
    connectivityStates.value[stateKey] = {
      testing: false,
      success: false,
      message: '连接请求失败，请检查地址、密钥或服务状态'
    };
    return;
  }

  connectivityStates.value[stateKey] = {
    testing: false,
    success: data.success,
    message: data.message,
    latencyMs: data.latencyMs
  };

  if (data.success) {
    window.$message?.success(`${getProviderPresentation(provider).name} 连接成功`);
  } else {
    window.$message?.error(`${getProviderPresentation(provider).name} 连接失败`);
  }
}

onMounted(() => {
  getModelProviders();
});
</script>

<template>
  <div class="model-console min-h-500px overflow-auto">
    <section class="model-console__intro">
      <div class="model-console__intro-mark">
        <icon-solar:cpu-bolt-line-duotone />
      </div>
      <div class="min-w-0 flex-1">
        <div class="model-console__eyebrow">MODEL RUNTIME</div>
        <h2>推理与向量模型</h2>
        <p>统一管理 Agent 推理和知识向量化连接。密钥不会回显，留空表示继续使用现有值。</p>
      </div>
      <div class="model-capabilities">
        <span>
          <i class="model-capabilities__dot" />
          OpenAI Compatible
        </span>
        <span>Streaming</span>
        <span>Tool Calling</span>
      </div>
    </section>

    <NSpin :show="modelProvidersLoading">
      <div v-if="modelProviders" class="provider-scopes">
        <section v-for="scopeKey in scopeKeys" :key="scopeKey" class="provider-scope">
          <header class="provider-scope__header">
            <div class="provider-scope__identity">
              <span class="provider-scope__index">{{ scopeKey === 'llm' ? '01' : '02' }}</span>
              <div>
                <div class="provider-scope__eyebrow">{{ scopeKey === 'llm' ? 'GENERATION' : 'RETRIEVAL' }}</div>
                <h3>{{ scopeKey === 'llm' ? 'LLM 推理模型' : 'Embedding 向量模型' }}</h3>
                <p>
                  {{
                    scopeKey === 'llm'
                      ? '新请求会立即使用当前 Active Provider'
                      : '切换向量模型前需确认现有知识完成兼容重嵌入'
                  }}
                </p>
              </div>
            </div>

            <div class="provider-scope__active">
              <span>当前运行</span>
              <strong v-if="getActiveProvider(modelProviders[scopeKey])">
                {{ getProviderPresentation(getActiveProvider(modelProviders[scopeKey])!).name }}
              </strong>
              <strong v-else>未选择</strong>
            </div>

            <div class="provider-scope__actions">
              <NSelect
                v-model:value="modelProviders[scopeKey].activeProvider"
                :options="
                  modelProviders[scopeKey].providers.map(item => ({
                    label: getProviderPresentation(item).name,
                    value: item.provider,
                    disabled: !item.enabled
                  }))
                "
                class="w-220px"
              />
              <NButton type="primary" :loading="modelProvidersSaving" @click="submitModelProviders(scopeKey)">
                保存配置
              </NButton>
            </div>
          </header>

          <div class="provider-grid">
            <article
              v-for="item in modelProviders[scopeKey].providers"
              :key="`${scopeKey}-${item.provider}`"
              class="provider-card"
              :class="{ 'provider-card--active': item.provider === modelProviders[scopeKey].activeProvider }"
            >
              <div class="provider-card__header">
                <div class="provider-card__title">
                  <div class="provider-card__mark">
                    <icon-solar:server-square-cloud-line-duotone />
                  </div>
                  <div>
                    <div class="provider-card__vendor">{{ getProviderPresentation(item).vendor }}</div>
                    <h4>{{ getProviderPresentation(item).name }}</h4>
                  </div>
                </div>
                <div class="flex items-center gap-3">
                  <span
                    v-if="item.provider === modelProviders[scopeKey].activeProvider"
                    class="provider-card__active-badge"
                  >
                    ACTIVE
                  </span>
                  <NSwitch v-model:value="item.enabled" size="small" />
                </div>
              </div>

              <div class="provider-fields">
                <label>
                  <span>API BASE URL</span>
                  <NInput v-model:value="item.apiBaseUrl" />
                </label>
                <label>
                  <span>MODEL</span>
                  <NInput v-model:value="item.model" />
                </label>
                <label v-if="scopeKey === 'embedding'">
                  <span>DIMENSION</span>
                  <NInputNumber v-model:value="item.dimension" :min="1" class="w-full" />
                </label>
                <div>
                  <span class="provider-field-label">CURRENT KEY</span>
                  <div class="provider-mask">
                    <icon-solar:key-minimalistic-square-line-duotone />
                    {{ item.hasApiKey ? item.maskedApiKey : '未配置' }}
                  </div>
                </div>
                <label :class="{ 'provider-field--wide': scopeKey === 'embedding' }">
                  <span>REPLACE API KEY</span>
                  <NInput
                    v-model:value="item.apiKeyInput"
                    type="password"
                    show-password-on="click"
                    placeholder="留空则保留现有密钥"
                  />
                </label>
              </div>

              <footer class="provider-card__footer">
                <div
                  v-if="connectivityStates[getConnectivityKey(scopeKey, item)]?.success !== undefined"
                  class="connectivity-result"
                  :class="{
                    'connectivity-result--success': connectivityStates[getConnectivityKey(scopeKey, item)]?.success
                  }"
                >
                  <icon-material-symbols:check-circle-outline-rounded
                    v-if="connectivityStates[getConnectivityKey(scopeKey, item)]?.success"
                  />
                  <icon-material-symbols:error-outline-rounded v-else />
                  <span>
                    {{ connectivityStates[getConnectivityKey(scopeKey, item)]?.message }}
                    <b v-if="connectivityStates[getConnectivityKey(scopeKey, item)]?.latencyMs !== undefined">
                      {{ connectivityStates[getConnectivityKey(scopeKey, item)]?.latencyMs }} ms
                    </b>
                  </span>
                </div>
                <span v-else class="provider-card__hint">测试不会保存当前输入</span>
                <NButton
                  size="small"
                  secondary
                  :loading="connectivityStates[getConnectivityKey(scopeKey, item)]?.testing"
                  @click="testModelProvider(scopeKey, item)"
                >
                  <template #icon>
                    <icon-solar:bolt-circle-line-duotone />
                  </template>
                  测试连接
                </NButton>
              </footer>
            </article>
          </div>
        </section>
      </div>
      <NEmpty v-else size="small" description="暂未加载到模型配置" class="py-20" />
    </NSpin>
  </div>
</template>

<style scoped lang="scss">
.model-console {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.model-console__intro,
.provider-scope {
  border: 1px solid var(--zs-border);
  background: var(--zs-surface-panel);
}

.model-console__intro {
  display: flex;
  align-items: center;
  gap: 15px;
  border-radius: 16px;
  padding: 18px 20px;
  box-shadow: var(--zs-shadow-workspace);
}

.model-console__intro-mark {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  place-items: center;
  border-radius: 12px;
  color: var(--zs-knowledge-indigo);
  background: rgb(86 87 217 / 9%);
  font-size: 25px;
}

.model-console__eyebrow,
.provider-scope__eyebrow,
.provider-card__vendor {
  color: var(--zs-signal-cyan);
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

.model-console__intro h2,
.provider-scope h3,
.provider-card h4 {
  margin: 0;
  color: var(--zs-ink-primary);
}

.model-console__intro h2 {
  margin-top: 2px;
  font-size: 20px;
}

.model-console__intro p,
.provider-scope__identity p {
  margin: 3px 0 0;
  color: var(--zs-ink-secondary);
  font-size: 11px;
}

.model-capabilities {
  display: flex;
  align-items: center;
  gap: 7px;
}

.model-capabilities span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--zs-border);
  border-radius: 999px;
  padding: 5px 8px;
  color: var(--zs-ink-secondary);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}

.model-capabilities__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--zs-evidence-emerald);
}

.provider-scopes {
  display: grid;
  gap: 14px;
}

.provider-scope {
  overflow: hidden;
  border-radius: 16px;
}

.provider-scope__header {
  display: grid;
  grid-template-columns: minmax(300px, 1fr) minmax(180px, 0.55fr) auto;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid var(--zs-border);
  padding: 16px 18px;
  background: var(--zs-surface-muted);
}

.provider-scope__identity {
  display: flex;
  align-items: center;
  gap: 12px;
}

.provider-scope__index {
  color: rgb(86 87 217 / 35%);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 23px;
}

.provider-scope h3 {
  margin-top: 2px;
  font-size: 15px;
}

.provider-scope__active {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  border-left: 1px solid var(--zs-border);
  padding-left: 18px;
}

.provider-scope__active span {
  color: var(--zs-ink-secondary);
  font-size: 9px;
}

.provider-scope__active strong {
  overflow: hidden;
  color: var(--zs-ink-primary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.provider-scope__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 14px;
}

.provider-card {
  overflow: hidden;
  border: 1px solid var(--zs-border);
  border-radius: 12px;
  background: var(--zs-surface-panel);
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.provider-card--active {
  border-color: rgb(86 87 217 / 28%);
  box-shadow: inset 3px 0 0 var(--zs-knowledge-indigo);
}

.provider-card__header,
.provider-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.provider-card__header {
  border-bottom: 1px solid var(--zs-border);
  padding: 13px 14px;
}

.provider-card__title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.provider-card__mark {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 9px;
  color: var(--zs-knowledge-indigo);
  background: rgb(86 87 217 / 8%);
  font-size: 18px;
}

.provider-card h4 {
  margin-top: 2px;
  font-size: 13px;
}

.provider-card__active-badge {
  border-radius: 999px;
  padding: 3px 7px;
  color: var(--zs-evidence-emerald);
  background: rgb(5 150 105 / 9%);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.provider-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 14px;
}

.provider-fields label {
  display: block;
}

.provider-fields label > span,
.provider-field-label {
  display: block;
  margin-bottom: 6px;
  color: var(--zs-ink-secondary);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 8px;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.provider-field--wide {
  grid-column: span 2;
}

.provider-mask {
  display: flex;
  min-height: 34px;
  align-items: center;
  gap: 7px;
  border: 1px dashed var(--zs-border);
  border-radius: 8px;
  padding: 0 10px;
  color: var(--zs-ink-secondary);
  background: var(--zs-surface-muted);
  font-family: SFMono-Regular, Menlo, monospace;
  font-size: 10px;
}

.provider-card__footer {
  min-height: 49px;
  border-top: 1px solid var(--zs-border);
  padding: 8px 12px 8px 14px;
  background: var(--zs-surface-muted);
}

.provider-card__hint {
  color: var(--zs-ink-secondary);
  font-size: 9px;
}

.connectivity-result {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  color: #dc2626;
  font-size: 10px;
}

.connectivity-result--success {
  color: var(--zs-evidence-emerald);
}

.connectivity-result span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connectivity-result b {
  margin-left: 5px;
  font-family: SFMono-Regular, Menlo, monospace;
}
</style>
