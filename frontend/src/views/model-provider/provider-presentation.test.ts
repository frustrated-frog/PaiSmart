import assert from 'node:assert/strict';
import test from 'node:test';
import { getProviderPresentation } from './provider-presentation';

function provider(overrides: Partial<Api.Admin.ModelProviderItem>): Api.Admin.ModelProviderItem {
  return {
    provider: 'deepseek',
    displayName: 'DeepSeek',
    apiStyle: 'OPENAI_COMPATIBLE',
    apiBaseUrl: 'https://api.deepseek.com/v1',
    model: 'deepseek-chat',
    dimension: null,
    enabled: true,
    active: true,
    hasApiKey: true,
    maskedApiKey: 'pk-***',
    apiKeyInput: '',
    ...overrides
  };
}

test('JD Cloud 兼容端点按实际模型展示，而不是沿用历史 Provider 名称', () => {
  const result = getProviderPresentation(
    provider({
      apiBaseUrl: 'https://modelservice.jdcloud.com/v1',
      model: 'GLM-5'
    })
  );

  assert.equal(result.name, 'JD Cloud · GLM-5');
  assert.equal(result.vendor, 'JD CLOUD');
});

test('DashScope 端点展示阿里云模型身份', () => {
  const result = getProviderPresentation(
    provider({
      apiBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      model: 'text-embedding-v4'
    })
  );

  assert.equal(result.name, 'DashScope · text-embedding-v4');
  assert.equal(result.vendor, 'DASHSCOPE');
});

test('未知兼容端点保留后端返回的展示名称', () => {
  const result = getProviderPresentation(provider({ displayName: 'Private Gateway' }));

  assert.equal(result.name, 'Private Gateway');
  assert.equal(result.vendor, 'OPENAI COMPATIBLE');
});
