/** Default theme settings */
export const themeSettings: App.Theme.ThemeSetting = {
  themeScheme: 'auto',
  grayscale: false,
  colourWeakness: false,
  recommendColor: true,
  themeColor: '#5657D9',
  otherColor: { info: '#0891B2', success: '#059669', warning: '#D97706', error: '#DC2626' },
  isInfoFollowPrimary: true,
  resetCacheStrategy: 'close',
  layout: { mode: 'vertical', scrollMode: 'content', reverseHorizontalMix: false },
  page: { animate: true, animateMode: 'fade-slide' },
  header: { height: 64, breadcrumb: { visible: false, showIcon: true }, multilingual: { visible: false } },
  tab: { visible: false, cache: true, height: 44, mode: 'chrome' },
  fixedHeaderAndTab: true,
  sider: {
    inverted: false,
    width: 212,
    collapsedWidth: 64,
    mixWidth: 90,
    mixCollapsedWidth: 64,
    mixChildMenuWidth: 200
  },
  footer: { visible: false, fixed: false, height: 48, right: true },
  watermark: { visible: false, text: '知枢 ZhiShu' },
  tokens: {
    light: {
      colors: {
        container: 'rgb(255, 255, 255)',
        layout: 'rgb(245, 247, 251)',
        inverted: 'rgb(23, 32, 51)',
        'base-text': 'rgb(23, 32, 51)'
      },
      boxShadow: {
        header: '0 1px 0 rgb(23, 32, 51, 0.08)',
        sider: '1px 0 0 rgb(23, 32, 51, 0.08)',
        tab: '0 1px 0 rgb(23, 32, 51, 0.08)'
      }
    },
    dark: {
      colors: {
        container: 'rgb(25, 28, 34)',
        layout: 'rgb(17, 19, 24)',
        inverted: 'rgb(238, 242, 255)',
        'base-text': 'rgb(238, 242, 255)'
      },
      boxShadow: {
        header: '0 1px 0 rgb(255, 255, 255, 0.06)',
        sider: '1px 0 0 rgb(255, 255, 255, 0.06)',
        tab: '0 1px 0 rgb(255, 255, 255, 0.06)'
      }
    }
  }
};

/**
 * Override theme settings
 *
 * If publish new version, use `overrideThemeSettings` to override certain theme settings
 */
export const overrideThemeSettings: Partial<App.Theme.ThemeSetting> = {};
