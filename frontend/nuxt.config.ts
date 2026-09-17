// https://nuxt.com/docs/api/configuration/nuxt-config
import AutoImport from 'unplugin-auto-import/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'
import Components from 'unplugin-vue-components/vite'


export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  devServer:{
    port: 7048
  },
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/api'
    }
  },
  nitro: {
    devProxy: {
      '/api': {
        // Nitro 的 devProxy 会把匹配到的 '/api' 前缀去掉再拼到 target 上，
        // 所以 target 必须自带 '/api'，否则后端收到的是 /auth/login 而被 Spring Security 判成 401。
        // 后端端口由仓库根目录 .env 的 SERVER_PORT 决定（当前为 4171）。
        target: process.env.NUXT_DEV_API_TARGET || 'http://127.0.0.1:4171/api',
        changeOrigin: true
      },
      // 皮肤/披风图片由后端的公开端点 /textures/{hash} 提供，同样会被剥掉前缀。
      '/textures': {
        target: process.env.NUXT_DEV_TEXTURE_TARGET || 'http://127.0.0.1:4171/textures',
        changeOrigin: true
      },
      // 头像是后端公开端点 /avatar/{uid}，同样会被剥掉前缀。
      '/avatar': {
        target: process.env.NUXT_DEV_AVATAR_TARGET || 'http://127.0.0.1:4171/avatar',
        changeOrigin: true
      }
    }
  },
  cap: {
    apiEndpoint: 'https://cap.example.com/<KEY>/',
  },
  // 深色模式的变量表，页面样式统一引用这些变量。
  css: ['~/assets/css/theme.css'],
  i18n: {
    defaultLocale: 'zh-CN',
    // 不在 URL 上加语言前缀，既有路由保持不变，语言只作为用户偏好。
    strategy: 'no_prefix',
    restructureDir: 'i18n',
    // 不使用 langDir/locales[].file：那套走按需动态导入，而语言包在 app/ 之外，dev 下 Vite 不服务
    // 该路径会 404。这里改成在 i18n.config.ts 里直接内联消息。
    lazy: false,
    vueI18n: './i18n.config.ts',
    // 语言代码写全（zh-CN），避免 vue-i18n 回退到 'zh' 时找不到消息。
    locales: [
      { code: 'zh-CN', language: 'zh-CN', name: '简体中文' },
      { code: 'en', language: 'en-US', name: 'English' },
    ],
    // 首次访问按 Accept-Language 请求头选语言，随后写进 cookie 持久化（键名与 preferences store 共用）。
    detectBrowserLanguage: {
      useCookie: true,
      cookieKey: 'foxskin_locale',
      redirectOn: 'root',
      alwaysRedirect: false,
      fallbackLocale: 'zh-CN',
    },
  },
  modules: [
    '@nuxt/eslint',
    '@nuxtjs/tailwindcss',
    '@pinia/nuxt',
    // 该模块生成的虚拟模块会 import 'dayjs'；pnpm 下 dayjs 必须是 package.json 里的直接依赖，
    // 否则 Vite 会把 CJS 的 dayjs.min.js 原样发给浏览器，导致客户端入口加载失败、整站无法 hydration。
    'dayjs-nuxt',
    '@bg-dev/nuxt-naiveui',
    'nuxt-cap',
    '@nuxtjs/i18n'
  ],
  vite: {
    plugins: [
      AutoImport({
        imports: [
          {
            'naive-ui': [
              'useDialog',
              'useMessage',
              'useNotification',
              'useLoadingBar'
            ]
          }
        ]
      }),
      Components({
        resolvers: [NaiveUiResolver()]
      })
    ]
  }
})