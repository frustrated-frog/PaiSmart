export interface ProviderPresentation {
  name: string;
  vendor: string;
}

export function getProviderPresentation(provider: Api.Admin.ModelProviderItem): ProviderPresentation {
  const baseUrl = provider.apiBaseUrl.toLowerCase();

  if (baseUrl.includes('jdcloud.com')) {
    return {
      name: `JD Cloud · ${provider.model}`,
      vendor: 'JD CLOUD'
    };
  }

  if (baseUrl.includes('dashscope.aliyuncs.com')) {
    return {
      name: `DashScope · ${provider.model}`,
      vendor: 'DASHSCOPE'
    };
  }

  return {
    name: provider.displayName,
    vendor: provider.apiStyle.replaceAll('_', ' ')
  };
}
