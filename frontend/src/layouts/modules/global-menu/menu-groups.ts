export interface DesktopMenuGroup {
  key: string;
  label: string;
  children: App.Global.Menu[];
}

const menuGroupDefinitions = [
  {
    key: 'workspace',
    label: '核心工作台',
    menuKeys: ['chat', 'knowledge-base', 'chat-history']
  },
  {
    key: 'agent-governance',
    label: 'Agent 治理',
    menuKeys: ['model-provider', 'usage-monitor']
  },
  {
    key: 'organization',
    label: '组织与系统',
    menuKeys: ['user', 'org-tag', 'invite-code']
  },
  {
    key: 'account',
    label: '账户服务',
    menuKeys: ['personal-center', 'recharge', 'recharge-manage']
  }
] as const;

export function groupDesktopMenus(menus: App.Global.Menu[]): DesktopMenuGroup[] {
  const menuMap = new Map(menus.map(menu => [menu.key, menu]));
  const knownKeys = new Set<string>();

  const groups: DesktopMenuGroup[] = menuGroupDefinitions.flatMap(group => {
    const children = group.menuKeys.flatMap(key => {
      knownKeys.add(key);
      const item = menuMap.get(key);
      return item ? [item] : [];
    });

    return children.length ? [{ key: group.key, label: group.label, children }] : [];
  });

  const ungroupedMenus = menus.filter(menu => !knownKeys.has(menu.key));
  if (ungroupedMenus.length) {
    groups.push({
      key: 'other',
      label: '其他能力',
      children: ungroupedMenus
    });
  }

  return groups;
}
