import type { RouteMeta } from 'vue-router';
import ElegantVueRouter from '@elegant-router/vue/vite';
import type { RouteKey } from '@elegant-router/types';

const routeMetaOverrides: Partial<Record<RouteKey, Partial<RouteMeta>>> = {
  '403': { hideInMenu: true },
  '404': { hideInMenu: true },
  '500': { hideInMenu: true },
  chat: { icon: 'solar:chat-round-call-line-duotone', order: 1 },
  'chat-history': { icon: 'solar:hashtag-chat-broken', roles: ['ADMIN'], order: 2 },
  'iframe-page': { constant: true, hideInMenu: true, keepAlive: true },
  'invite-code': { icon: 'solar:key-minimalistic-square-line-duotone', roles: ['ADMIN'], order: 6 },
  'knowledge-base': { icon: 'solar:folder-line-duotone', order: 3 },
  login: { hideInMenu: true },
  'model-provider': { icon: 'solar:tuning-square-line-duotone', roles: ['ADMIN'], order: 5 },
  'org-tag': { icon: 'solar:tag-line-duotone', roles: ['ADMIN'], order: 4 },
  'personal-center': { icon: 'solar:people-nearby-line-duotone', order: 7 },
  recharge: { icon: 'solar:airbuds-case-charge-broken', order: 8 },
  'recharge-manage': { icon: 'solar:shop-minimalistic-broken', roles: ['ADMIN'], order: 9 },
  'usage-monitor': { icon: 'solar:chart-2-line-duotone', roles: ['ADMIN'], order: 6 },
  user: { icon: 'solar:users-group-two-rounded-line-duotone', roles: ['ADMIN'] }
};

export function setupElegantRouter() {
  return ElegantVueRouter({
    layouts: {
      base: 'src/layouts/base-layout/index.vue',
      blank: 'src/layouts/blank-layout/index.vue'
    },
    routePathTransformer(routeName, routePath) {
      const key = routeName as RouteKey;

      if (key === 'login') {
        const modules: UnionKey.LoginModule[] = ['pwd-login', 'code-login', 'register', 'reset-pwd', 'bind-wechat'];

        const moduleReg = modules.join('|');

        return `/login/:module(${moduleReg})?`;
      }

      return routePath;
    },
    onRouteMetaGen(routeName) {
      const key = routeName as RouteKey;

      const constantRoutes: RouteKey[] = ['login', '403', '404', '500'];

      const meta: Partial<RouteMeta> = {
        title: key,
        i18nKey: `route.${key}` as App.I18n.I18nKey
      };

      if (constantRoutes.includes(key)) {
        meta.constant = true;
      }

      return {
        ...meta,
        ...routeMetaOverrides[key]
      };
    }
  });
}
