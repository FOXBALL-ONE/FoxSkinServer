import en from './locales/en.json'
import zhCN from './locales/zh-CN.json'

/**
 * 语言包直接内联进构建产物。
 *
 * 注意：nuxt-i18n 的 `vueI18n` 路径是相对 `restructureDir`（这里是 `i18n/`）解析的，所以本文件放在
 * `i18n/` 下、并配置为 `./i18n.config.ts`。
 *
 * 不用 langDir + locales[].file 那套：它走按需动态导入，而语言包在 app/ 之外，dev 下 Vite 不服务该
 * 路径会 404，客户端就拿不到文案。只有两个小文件，内联更省事，也顺带消掉首屏切换语言的闪烁。
 */
export default {
  messages: {
    'zh-CN': zhCN,
    en,
  },
}
