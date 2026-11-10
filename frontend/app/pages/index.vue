<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'

useHead({
  title: 'FoxSkin · Minecraft 皮肤站',
  meta: [{name: 'description', content: '自托管 Minecraft 皮肤站：衣柜管理、皮肤库共享与 Yggdrasil 外置登录。'}],
})

const router = useRouter()
const config = useRuntimeConfig()
const auth = useAuthStore()

const menuOpen = ref(false)
const activeSection = ref('')
const activeSkin = ref(0)
const copied = ref(false)
const copyFailed = ref(false)
const yggdrasilRoot = ref('')
type ServiceStatus = 'checking' | 'online' | 'unknown'
const serviceStatus = ref<ServiceStatus>('checking')

/** 首页的展示角色：纯前端素材，不代表皮肤库真实数据。 */
const characters = [
  {
    name: 'Rustfur',
    cn: '赤狐巡守',
    creator: 'Lina Park',
    accent: '#ef7d32',
    jacket: '#b4461f',
    shirt: '#f4e3c6',
    hair: '#3a2014',
    legs: '#5a3620',
  },
  {
    name: 'Emberfall',
    cn: '暮光余烬',
    creator: 'Kaito',
    accent: '#ff9f45',
    jacket: '#8a4a26',
    shirt: '#e8c79a',
    hair: '#2b1a10',
    legs: '#44301d',
  },
  {
    name: 'Pinewatch',
    cn: '松林守夜',
    creator: 'mira.exe',
    accent: '#d8a24a',
    jacket: '#5a5338',
    shirt: '#ddd0a6',
    hair: '#2a2418',
    legs: '#3a3826',
  },
]
const selected = computed(() => characters[activeSkin.value]!)
const paddedIndex = computed(() => String(activeSkin.value + 1).padStart(2, '0'))

const navLinks = [
  {id: 'union', label: '联合认证'},
  {id: 'library', label: '皮肤库'},
  {id: 'start', label: '接入服务器'},
  {id: 'about', label: '关于'},
]

/** 页脚 ICP 备案：备案号文案与点击跳转地址都来自运行时配置，可按部署环境设置。 */
const icpText = computed(() => (config.public.icpText as string) || '')
const icpUrl = computed(() => (config.public.icpUrl as string) || 'https://beian.miit.gov.cn/')
const year = new Date().getFullYear()

/** 皮肤库最新公开材质：来自真实接口 /api/skinlib，失败时给出明确状态。 */
type ShowcaseEntry = { id: number; name: string; type: string; hash: string }
const showcase = ref<ShowcaseEntry[]>([])
const showcaseState = ref<'loading' | 'ready' | 'empty' | 'failed'>('loading')
const showcaseText = computed(() => ({
  loading: '正在读取皮肤库…',
  ready: '',
  empty: '皮肤库还没有公开材质，来传第一件。',
  failed: '皮肤库暂时无法访问。',
}[showcaseState.value]))
const showcaseItems = computed(() => showcase.value.slice(0, 8))
const isCape = (entry: { type: string }) => entry.type === 'cape'
const modelTypeOf = (entry: { type: string }): 'cape' | 'alex' | 'steve' =>
    isCape(entry) ? 'cape' : entry.type === 'alex' ? 'alex' : 'steve'
const textureUrl = (hash: string) => `/textures/${hash}`

async function loadShowcase() {
  showcaseState.value = 'loading'
  try {
    const base = config.public.apiBase || '/api'
    const rs = await $fetch<{ status?: number; data?: { list?: ShowcaseEntry[] } }>(
        `${base}/skinlib`, {params: {page: 1, size: 8}, timeout: 5000})
    showcase.value = rs.data?.list ?? []
    showcaseState.value = showcase.value.length > 0 ? 'ready' : 'empty'
  } catch {
    showcase.value = []
    showcaseState.value = 'failed'
  }
}

type PixelIcon = { palette: Record<string, string>; rows: string[] }

/* 8x8 像素图标：每行一个字符串，字符映射到调色板颜色，'.' 为透明。
   键名沿用历史（chest/grass/steve/beacon），图案已改为橘狐主题。 */
const pixelIcons: Record<string, PixelIcon> = {
  // 衣柜：暖木色箱子 + 金色锁扣。
  chest: {
    palette: {a: '#a06a38', b: '#5a3a22', c: '#2a1a10', d: '#f0b429'},
    rows: [
      'aaaaaaaa',
      'abaaaaab',
      'bbbbbbbb',
      'aadddaaa',
      'aadddaaa',
      'abaaaaab',
      'abaaaaab',
      'cccccccc',
    ],
  },
  // 皮肤库共享：一张正面狐狸脸。
  grass: {
    palette: {o: '#ef7d32', w: '#f4e3c6', k: '#2a1710'},
    rows: [
      'o......o',
      'oo....oo',
      'oooooooo',
      'owkwwkwo',
      'owwwwwwo',
      'oowkkwoo',
      '.owwwwo.',
      '..owwo..',
    ],
  },
  // 角色档案：一枚狐狸爪印。
  steve: {
    palette: {p: '#ef7d32'},
    rows: [
      'p.p.p.p.',
      'p.p.p.p.',
      '........',
      '.pppppp.',
      'pppppppp',
      'pppppppp',
      '.pppppp.',
      '..pppp..',
    ],
  },
  // Yggdrasil 认证：一把钥匙。
  beacon: {
    palette: {g: '#f0b429', o: '#ef7d32'},
    rows: [
      '.ggg....',
      'gg.gg...',
      'gg.gg...',
      '.ggg....',
      '..go....',
      '..go....',
      '..ggo...',
      '..go....',
    ],
  },
}

/** 把像素图转成一串 box-shadow：每格一条阴影，格距由 CSS 变量 --px 控制。 */
function pixelShadow(icon: PixelIcon): string {
  const shadows: string[] = []
  icon.rows.forEach((row, y) => {
    row.split('').forEach((ch, x) => {
      const color = icon.palette[ch]
      if (color) shadows.push(`calc(${x} * var(--px)) calc(${y} * var(--px)) 0 0 ${color}`)
    })
  })
  return shadows.join(', ')
}

const iconShadows = Object.fromEntries(
    Object.entries(pixelIcons).map(([key, icon]) => [key, pixelShadow(icon)]),
) as Record<string, string>

const statusText = computed(() => ({
  checking: '状态检测中',
  online: '服务运行正常',
  unknown: '状态未知',
}[serviceStatus.value]))

async function probeService() {
  serviceStatus.value = 'checking'
  try {
    const base = config.public.apiBase || '/api'
    const health = await $fetch<{ status?: string }>(`${base}/actuator/health`, {timeout: 4000})
    serviceStatus.value = health?.status === 'UP' ? 'online' : 'unknown'
  } catch {
    serviceStatus.value = 'unknown'
  }
}

/** 锚点高亮：滚动时记录最后一个越过顶栏下沿的板块。 */
function updateNav() {
  let current = ''
  for (const {id} of navLinks) {
    const el = document.getElementById(id)
    if (el && el.offsetTop - 140 <= window.scrollY) current = id
  }
  activeSection.value = current
}

async function copyYggdrasil() {
  try {
    await navigator.clipboard.writeText(yggdrasilRoot.value)
    copyFailed.value = false
    copied.value = true
  } catch {
    copied.value = false
    copyFailed.value = true
  }
  window.setTimeout(() => {
    copied.value = false
    copyFailed.value = false
  }, 2400)
}

function go(path: string) {
  menuOpen.value = false
  void router.push(path)
}

function closeMenu() {
  menuOpen.value = false
}

onMounted(async () => {
  await auth.hydrate()
  updateNav()
  window.addEventListener('scroll', updateNav, {passive: true})
  const base = config.public.apiBase || '/api'
  yggdrasilRoot.value = new URL(`${base}/yggdrasil`, window.location.origin).toString()
  void probeService()
  void loadShowcase()
})
onUnmounted(() => window.removeEventListener('scroll', updateNav))
</script>

<template>
  <main class="home-page">
    <div aria-hidden="true" class="home-bg"/>

    <header class="nav">
      <div class="nav__inner shell">
        <a aria-label="FoxSkin 首页" class="brand" href="#top" @click="closeMenu">
          <span class="brand__mark">F</span><span>FOX<span class="brand__thin">SKIN</span></span>
        </a>
        <button :aria-expanded="menuOpen" aria-controls="nav-menu" aria-label="切换菜单" class="nav__toggle"
                type="button" @click="menuOpen = !menuOpen"><i/><i/></button>
        <nav id="nav-menu" :class="{'nav__menu--open': menuOpen}" aria-label="站点导航" class="nav__menu">
          <a v-for="link in navLinks" :key="link.id" :class="{active: activeSection === link.id}"
             :href="`#${link.id}`" @click="closeMenu">{{ link.label }}</a>
          <span aria-hidden="true" class="nav__divider"/>
          <template v-if="auth.isAuthenticated">
            <button class="nav__cta" type="button" @click="go('/dashboard')">进入控制台</button>
          </template>
          <template v-else>
            <button class="nav__login" type="button" @click="go('/login')">登录</button>
            <button class="nav__cta" type="button" @click="go('/register')">创建账号</button>
          </template>
        </nav>
      </div>
    </header>

    <section id="top" class="hero">
      <div class="hero__content shell">
        <div class="hero__copy">
          <p class="kicker"><span :class="`dot dot--${serviceStatus}`" aria-hidden="true"/>{{ statusText }}</p>
          <h1>FoxSkin 皮肤站<br><em>给角色一个值得记住的样子</em></h1>
          <p class="hero__lead">
            上传皮肤与披风，把每一个形象收进衣柜；再通过 Yggdrasil，把它带进任何信任这个皮肤站的服务器。</p>
          <div class="hero__actions">
            <template v-if="auth.isAuthenticated">
              <button class="btn btn--primary" type="button" @click="go('/dashboard')">打开我的衣柜</button>
            </template>
            <template v-else>
              <button class="btn btn--primary" type="button" @click="go('/register')">创建账号</button>
            </template>
            <a class="btn btn--ghost" href="#start">看看怎么接入</a>
          </div>
          <p class="hero__meta">
            <span>Classic / Slim 模型</span><span>皮肤 · 披风</span><span>开源自托管</span>
          </p>
        </div>

        <aside aria-label="皮肤预览" class="stage">
          <div class="stage__head"><span>LIVE PREVIEW</span><b>{{ paddedIndex }} / {{ String(characters.length).padStart(2, '0') }}</b>
          </div>
          <div class="stage__body">
            <div
                :style="{ '--accent': selected.accent, '--jacket': selected.jacket, '--shirt': selected.shirt, '--hair': selected.hair, '--legs': selected.legs }"
                class="character">
              <i class="character__shadow"/>
              <i class="character__ear character__ear--l"/>
              <i class="character__ear character__ear--r"/>
              <i class="character__head"/>
              <i class="character__neck"/>
              <i class="character__torso"/>
              <i class="character__arm character__arm--l"/>
              <i class="character__arm character__arm--r"/>
              <i class="character__leg character__leg--l"/>
              <i class="character__leg character__leg--r"/>
            </div>
          </div>
          <div class="stage__foot">
            <div class="stage__meta"><b>{{ selected.cn }}</b>
              <small>{{ selected.name }} · by {{ selected.creator }}</small>
            </div>
            <div class="stage__switch">
              <button v-for="(item, index) in characters" :key="item.name"
                      :aria-label="`预览 ${item.cn}`" :class="{active: activeSkin === index}"
                      class="stage__swatch" type="button" @click="activeSkin = index">
                <span :style="{background: item.accent}"/>
              </button>
            </div>
          </div>
        </aside>
      </div>
      <div class="hero__foot shell"><span>JAVA · BEDROCK · AUTHLIB-INJECTOR</span><span>SCROLL <b>↓</b></span></div>
    </section>

    <section id="union" class="union shell">
      <header class="union__head">
        <p class="kicker">UNION · 联合皮肤认证</p>
        <h2>一处登录，<em>全服通行。</em></h2>
        <p class="union__lead">皮肤库共享、角色管理、Yggdrasil 外置登录——一座自托管皮肤站需要的，都在这里。</p>
      </header>
      <div class="union__grid">
        <article class="union__cell">
          <span aria-hidden="true" class="union__icon"><i :style="{boxShadow: iconShadows.grass}"/></span>
          <h3>联络</h3>
          <b>皮肤库共享</b>
          <p>全站玩家公开分享的皮肤与披风，互相浏览、一键收录进自己的衣柜。</p>
        </article>
        <article class="union__cell">
          <span aria-hidden="true" class="union__icon"><i :style="{boxShadow: iconShadows.steve}"/></span>
          <h3>联合</h3>
          <b>角色与衣柜</b>
          <p>一个账号挂多个游戏角色，各自绑定皮肤、披风，对应各自的 UUID。</p>
        </article>
        <article class="union__cell">
          <span aria-hidden="true" class="union__icon"><i :style="{boxShadow: iconShadows.beacon}"/></span>
          <h3>联盟</h3>
          <b>Yggdrasil 认证</b>
          <p>标准 authlib-injector 外置登录，兼容 Java 与基岩服务器。</p>
        </article>
      </div>
    </section>

    <section id="library" class="showcase shell">
      <div class="section-head">
        <div>
          <p class="kicker">SKIN LIBRARY / LIVE</p>
          <h2>皮肤库正在<em>上新。</em></h2>
        </div>
        <p class="section-head__aside">这里是全站玩家公开分享的最新材质。<br>点一件查看大图，喜欢就收进衣柜。</p>
      </div>

      <div v-if="showcaseState === 'ready'" class="showcase__grid">
        <button v-for="entry in showcaseItems" :key="entry.id" class="showcase__card panel" type="button"
                @click="go(`/library?view=${entry.id}`)">
          <span class="showcase__thumb">
            <img :alt="entry.name" :src="textureUrl(entry.hash)" loading="lazy">
          </span>
          <span class="showcase__meta">
            <b>{{ entry.name }}</b>
            <small>{{ isCape(entry) ? 'CAPE · 披风' : modelTypeOf(entry) === 'alex' ? 'SKIN · SLIM' : 'SKIN · CLASSIC' }}</small>
          </span>
          <span class="showcase__go" aria-hidden="true">→</span>
        </button>
      </div>
      <div v-else class="showcase__fallback panel">
        <p>{{ showcaseText }}</p>
        <button v-if="showcaseState === 'failed'" class="btn btn--ghost" type="button" @click="loadShowcase">重试</button>
        <button v-else-if="showcaseState === 'empty'" class="btn btn--primary" type="button"
                @click="go(auth.isAuthenticated ? '/closet' : '/register')">上传材质</button>
      </div>
      <NuxtLink class="showcase__more" to="/library">浏览完整皮肤库 →</NuxtLink>
    </section>

    <section id="start" class="start shell">
      <div class="section-head">
        <div>
          <p class="kicker">GETTING STARTED / 03</p>
          <h2>三步，把服务器<br><em>接到这座皮肤站。</em></h2>
        </div>
        <p class="section-head__aside">只需要 JVM 参数与一个 API 地址，<br>不用改客户端，也不用装插件。</p>
      </div>
      <div class="panel steps">
        <article class="step">
          <span class="step__no">STEP 01</span>
          <h3>创建账号</h3>
          <p>注册后上传第一套皮肤，角色即刻拥有外观。</p>
        </article>
        <article class="step">
          <span class="step__no">STEP 02</span>
          <h3>配置 authlib-injector</h3>
          <p>在启动参数里指向下方 API 根地址，重启服务器。</p>
        </article>
        <article class="step">
          <span class="step__no">STEP 03</span>
          <h3>玩家外置登录</h3>
          <p>玩家用皮肤站账号进服，皮肤与披风自动生效。</p>
        </article>
      </div>
      <div class="panel api">
        <div class="api__info">
          <span class="api__label">AUTHLIB-INJECTOR · API ROOT</span>
          <code class="api__url">{{ yggdrasilRoot || '正在解析 API 地址…' }}</code>
        </div>
        <button :class="{'api__copy--done': copied, 'api__copy--fail': copyFailed}" class="btn btn--ghost api__copy"
                type="button" @click="copyYggdrasil">
          {{ copied ? '已复制 ✓' : copyFailed ? '复制失败，请手动复制' : '复制地址' }}
        </button>
      </div>
    </section>

    <section id="about" class="cta">
      <div class="cta__inner shell">
        <div class="cta__copy">
          <p class="kicker">GET STARTED</p>
          <h2>你的社区，<em>值得一个自己的皮肤站。</em></h2>
          <p class="cta__text">开源、轻量、为 Minecraft 社区准备。部署一次，全服玩家的外观与外置登录都从这里出发。</p>
          <p class="cta__status"><span :class="`dot dot--${serviceStatus}`" aria-hidden="true"/>{{ statusText }} · powered by Yggdrasil</p>
        </div>
        <div class="cta__action">
          <template v-if="auth.isAuthenticated">
            <button class="btn btn--primary" type="button" @click="go('/dashboard')">打开控制台</button>
          </template>
          <template v-else>
            <button class="btn btn--primary" type="button" @click="go('/register')">开始使用</button>
          </template>
        </div>
      </div>
    </section>

    <footer class="footer">
      <div class="footer__inner shell">
        <p class="footer__copy">
          <span>© {{ year }} FoxSkin 皮肤站</span>
          <template v-if="icpText">
            <span aria-hidden="true" class="footer__sep">|</span>
            <a class="footer__icp" :href="icpUrl" rel="noreferrer" target="_blank">{{ icpText }}</a>
          </template>
        </p>
        <p class="footer__by">
          Powered by
          <a :href="yggdrasilRoot || '#'" rel="noreferrer" target="_blank">Yggdrasil</a>
        </p>
      </div>
    </footer>
  </main>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=Space+Grotesk:wght@400;500;600;700&family=Noto+Sans+SC:wght@400;500;600;700&display=swap');

/* 首页按美术规范固定为深色；其余页面仍由 data-theme 控制。 */
:root:has(.home-page) {
  color-scheme: dark;
}

html {
  scroll-behavior: smooth;
  scroll-padding-top: 84px;
}

.home-page {
  /* 橘色狐狸主题的设计令牌：暖炭底色 + 狐狸橘主色 + 琥珀/奶油点缀。 */
  --hp-ink: #130b06;
  --hp-panel: rgba(28, 18, 11, .94);
  --hp-raised: #241810;
  --hp-edge: #0a0502;
  --hp-line: #3b2a1c;
  --hp-text: #f7efe4;
  --hp-muted: #c9b4a2;
  --hp-faint: #93795f;
  /* --hp-green 这个名字沿用历史，实际承载主色「狐狸橘」，驱动主按钮与 CTA。 */
  --hp-green: #ef7d32;
  --hp-bright: #ff9f45;
  --hp-cream: #f4e3c6;
  --hp-blue: #6ba8c6;
  --hp-gold: #f0b429;
  --hp-red: #d1503a;
  --hp-ok: #67b36a;
  --hp-ink-on-accent: #1c0f06;

  position: relative;
  isolation: isolate;
  overflow: hidden;
  min-width: 320px;
  min-height: 100svh;
  background: var(--hp-ink);
  color: var(--hp-text);
  font-family: 'Noto Sans SC', 'Space Grotesk', sans-serif;
  font-size: 13px;
}

.home-page *,
.home-page *::before,
.home-page *::after {
  box-sizing: border-box;
}

.home-page :is(a, button) {
  font: inherit;
  color: inherit;
}

.home-page :is(a, button):focus-visible {
  outline: 2px solid var(--hp-bright);
  outline-offset: 2px;
}

/* —— 沉浸式背景：图片固定铺满，深色遮罩保证前景可读（§4.1）。 —— */
.home-bg {
  position: fixed;
  z-index: -1;
  inset: 0;
  background: url('/blessing-bg.webp') 64% 60% / cover no-repeat;
}

.home-bg::before {
  content: '';
  position: absolute;
  inset: 0;
  background: inherit;
  /* 把蓝天场景整体烤成琥珀秋色，像狐狸窝里的暖光。 */
  filter: sepia(.78) saturate(1.5) hue-rotate(-18deg) brightness(.42) contrast(1.06);
}

.home-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
      radial-gradient(120% 82% at 84% 6%, rgba(255, 159, 77, .26) 0%, rgba(255, 159, 77, 0) 46%),
      radial-gradient(96% 72% at 10% 94%, rgba(180, 70, 31, .24) 0%, rgba(180, 70, 31, 0) 52%),
      linear-gradient(90deg, rgba(19, 11, 6, .93) 0%, rgba(19, 11, 6, .74) 44%, rgba(19, 11, 6, .4) 100%),
      linear-gradient(0deg, rgba(19, 11, 6, .92) 8%, rgba(19, 11, 6, .36) 46%, rgba(19, 11, 6, .1) 100%);
}

.shell {
  width: min(1180px, calc(100% - 56px));
  margin: 0 auto;
}

/* —— 通用文字部件。 —— */
.kicker {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 14px;
  color: var(--hp-bright);
  font: 10px 'DM Mono', monospace;
  letter-spacing: .12em;
}

h1, h2 {
  margin: 0;
  color: var(--hp-text);
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-weight: 600;
  line-height: 1.14;
  letter-spacing: .01em;
}

h1 em, h2 em {
  color: var(--hp-bright);
  font-style: normal;
}

h1 {
  font-size: clamp(40px, 5.4vw, 64px);
  line-height: 1.1;
}

h2 {
  font-size: clamp(25px, 3vw, 36px);
}

.dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: var(--hp-faint);
}

.dot--checking {
  background: var(--hp-gold);
}

.dot--online {
  background: var(--hp-ok);
  box-shadow: 0 0 0 3px rgba(103, 179, 106, .2);
}

.dot--unknown {
  background: var(--hp-faint);
}

/* —— 按钮：绿色填充主操作 + 描边次操作（§7.4）。 —— */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 20px;
  border: 1px solid transparent;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  transition: background .15s, border-color .15s, color .15s;
}

.btn--primary {
  border-color: var(--hp-green);
  background: var(--hp-green);
  color: var(--hp-ink-on-accent);
}

.btn--primary:hover {
  border-color: var(--hp-bright);
  background: var(--hp-bright);
}

.btn--ghost {
  border-color: var(--hp-line);
  background: rgba(8, 12, 10, .4);
  color: var(--hp-muted);
}

.btn--ghost:hover {
  border-color: var(--hp-bright);
  color: var(--hp-bright);
}

.panel {
  border: 1px solid var(--hp-edge);
  background: var(--hp-panel);
}

/* —— 顶栏：近黑、细底边框、当前板块亮色加底标（§7.1）。 —— */
.nav {
  position: fixed;
  z-index: 20;
  top: 0;
  width: 100%;
  border-bottom: 1px solid var(--hp-line);
  background: rgba(8, 12, 10, .93);
}

.nav__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font: 700 14px 'Space Grotesk', sans-serif;
  letter-spacing: .04em;
  text-decoration: none;
}

.brand__mark {
  position: relative;
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  color: var(--hp-ink-on-accent);
  background: linear-gradient(150deg, var(--hp-bright), var(--hp-green));
  font: 700 16px 'Space Grotesk', sans-serif;
}

/* 商标上的两只狐狸耳朵。 */
.brand__mark::before,
.brand__mark::after {
  content: '';
  position: absolute;
  top: -5px;
  width: 0;
  height: 0;
  border: 5px solid transparent;
  border-bottom-color: var(--hp-green);
}

.brand__mark::before {
  left: 1px;
  transform: rotate(-20deg);
}

.brand__mark::after {
  right: 1px;
  transform: rotate(20deg);
}

.brand__thin {
  color: var(--hp-faint);
  font: 400 14px 'DM Mono', monospace;
}

.nav__menu {
  display: flex;
  align-items: center;
  gap: 22px;
}

.nav__menu a {
  position: relative;
  padding: 6px 0;
  color: var(--hp-muted);
  font-size: 12px;
  text-decoration: none;
  transition: color .15s;
}

.nav__menu a:hover {
  color: var(--hp-text);
}

.nav__menu a.active {
  color: var(--hp-text);
}

.nav__menu a.active::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -2px;
  left: 0;
  height: 2px;
  background: var(--hp-bright);
}

.nav__divider {
  width: 1px;
  height: 18px;
  background: var(--hp-line);
}

.nav__login {
  border: 0;
  background: none;
  cursor: pointer;
  color: var(--hp-muted);
  font-size: 12px;
  transition: color .15s;
}

.nav__login:hover {
  color: var(--hp-text);
}

.nav__cta {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--hp-green);
  background: var(--hp-green);
  cursor: pointer;
  color: var(--hp-ink-on-accent);
  font-size: 12px;
  font-weight: 600;
  transition: background .15s, border-color .15s;
}

.nav__cta:hover {
  border-color: var(--hp-bright);
  background: var(--hp-bright);
}

.nav__toggle {
  display: none;
  padding: 8px;
  border: 0;
  background: none;
  cursor: pointer;
}

.nav__toggle i {
  display: block;
  width: 20px;
  height: 2px;
  margin: 4px 0;
  background: var(--hp-text);
}

/* —— 英雄区。 —— */
.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100svh;
  min-height: 700px;
  max-height: 980px;
}

.hero__content {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 64px;
  align-items: center;
  padding: 128px 0 96px;
}

.hero__lead {
  max-width: 480px;
  margin: 22px 0 30px;
  color: var(--hp-muted);
  font-size: 14px;
  line-height: 1.9;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  margin: 34px 0 0;
  color: var(--hp-faint);
  font: 10px 'DM Mono', monospace;
  letter-spacing: .08em;
}

.hero__foot {
  position: absolute;
  right: 0;
  bottom: 20px;
  left: 0;
  display: flex;
  justify-content: space-between;
  color: var(--hp-faint);
  font: 9px 'DM Mono', monospace;
  letter-spacing: .1em;
}

.hero__foot b {
  color: var(--hp-bright);
}

/* —— 皮肤预览面板。 —— */
.stage {
  border: 1px solid var(--hp-line);
  background: var(--hp-panel);
}

.stage__head {
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--hp-line);
  color: var(--hp-faint);
  font: 10px 'DM Mono', monospace;
  letter-spacing: .1em;
}

.stage__head b {
  color: var(--hp-bright);
  font-weight: 400;
}

.stage__body {
  position: relative;
  display: grid;
  height: 400px;
  place-items: center;
  overflow: hidden;
  background-image:
      linear-gradient(rgba(255, 159, 77, .06) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 159, 77, .06) 1px, transparent 1px);
  background-size: 18px 18px;
}

.stage__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-top: 1px solid var(--hp-line);
}

.stage__meta b {
  display: block;
  color: var(--hp-text);
  font-size: 14px;
  font-weight: 600;
}

.stage__meta small {
  display: block;
  margin-top: 3px;
  color: var(--hp-faint);
  font: 10px 'DM Mono', monospace;
}

.stage__switch {
  display: flex;
  gap: 6px;
}

.stage__swatch {
  display: grid;
  width: 22px;
  height: 22px;
  padding: 4px;
  border: 1px solid var(--hp-line);
  background: var(--hp-raised);
  cursor: pointer;
}

.stage__swatch.active {
  border-color: var(--hp-bright);
}

.stage__swatch span {
  display: block;
  width: 100%;
  height: 100%;
}

/* —— 像素小人（纯 CSS 拼装）。 —— */
.character {
  position: relative;
  width: 180px;
  height: 380px;
  margin-top: 24px;
}

.character i {
  position: absolute;
  display: block;
}

.character__shadow {
  bottom: 0;
  left: 12px;
  width: 156px;
  height: 12px;
  background: rgba(0, 0, 0, .45);
  border-radius: 50%;
  filter: blur(5px);
}

.character__head {
  top: 0;
  left: 42px;
  width: 96px;
  height: 96px;
  background: #c68c64;
  box-shadow: inset -12px 0 rgba(8, 12, 10, .16);
}

/* 狐狸耳朵：外廓取角色主色，内耳奶油色。 */
.character__ear {
  top: -22px;
  z-index: 1;
  width: 0;
  height: 0;
  border: 17px solid transparent;
  border-bottom: 32px solid var(--accent);
}

.character__ear::after {
  content: '';
  position: absolute;
  top: 12px;
  left: -8px;
  width: 0;
  height: 0;
  border: 8px solid transparent;
  border-bottom: 16px solid var(--hp-cream);
}

.character__ear--l {
  left: 34px;
  transform: rotate(-16deg);
}

.character__ear--r {
  right: 34px;
  transform: rotate(16deg);
}

.character__head::before {
  content: '';
  position: absolute;
  inset: 0 0 56px;
  background: var(--hair);
}

.character__head::after {
  content: '';
  position: absolute;
  top: 60px;
  left: 20px;
  width: 8px;
  height: 8px;
  background: #e8ece5;
  box-shadow:
      10px 0 0 #2a1710,
      40px 0 0 #e8ece5,
      50px 0 0 #2a1710;
}

.character__neck {
  top: 96px;
  left: 74px;
  width: 32px;
  height: 18px;
  background: #b97f5a;
}

.character__torso {
  top: 114px;
  left: 24px;
  width: 132px;
  height: 150px;
  background: var(--jacket);
  box-shadow:
      inset 18px 0 rgba(232, 236, 229, .08),
      inset -20px 0 rgba(8, 12, 10, .3);
}

.character__torso::after {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 28px;
  height: 100%;
  transform: translateX(-50%);
  background: var(--shirt);
  opacity: .92;
}

.character__arm {
  top: 118px;
  width: 38px;
  height: 144px;
  background: var(--jacket);
  box-shadow:
      inset 10px 0 rgba(232, 236, 229, .08),
      inset -12px 0 rgba(8, 12, 10, .3);
}

.character__arm--l {
  left: 0;
}

.character__arm--r {
  right: 0;
}

.character__leg {
  top: 264px;
  width: 58px;
  height: 114px;
  background: var(--legs);
  box-shadow:
      inset 12px 0 rgba(232, 236, 229, .07),
      inset -14px 0 rgba(8, 12, 10, .32);
}

.character__leg--l {
  left: 26px;
}

.character__leg--r {
  right: 26px;
}

/* —— 联合认证区（MUA 风格：居中标题 + 三栏大图标）。 —— */
.union {
  padding: 92px 0 8px;
}

.union__head {
  max-width: 640px;
  margin: 0 auto;
  text-align: center;
}

.union__head .kicker {
  justify-content: center;
}

.union__lead {
  margin: 16px auto 0;
  color: var(--hp-muted);
  font-size: 13.5px;
  line-height: 1.9;
}

.union__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 56px;
}

.union__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36px 26px;
  border: 1px solid var(--hp-edge);
  background: var(--hp-panel);
  text-align: center;
  transition: border-color .15s, transform .15s;
}

.union__cell:hover {
  border-color: var(--hp-green);
  transform: translateY(-3px);
}

.union__icon {
  --px: 7px;
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
}

.union__icon i {
  display: block;
  width: var(--px);
  height: var(--px);
}

.union__cell h3 {
  margin: 20px 0 6px;
  color: var(--hp-bright);
  font: 600 13px 'DM Mono', monospace;
  letter-spacing: .14em;
}

.union__cell b {
  color: var(--hp-text);
  font-size: 17px;
  font-weight: 600;
}

.union__cell p {
  margin: 12px 0 0;
  color: var(--hp-muted);
  font-size: 12.5px;
  line-height: 1.85;
}

/* —— 板块头。 —— */
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 40px;
}

.section-head__aside {
  max-width: 340px;
  margin: 0;
  color: var(--hp-faint);
  font-size: 12px;
  line-height: 1.85;
  text-align: right;
}

/* —— 皮肤库展示：真实接口数据。 —— */
.showcase {
  padding: 96px 0 0;
}

.showcase__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-top: 44px;
}

.showcase__card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--hp-edge);
  cursor: pointer;
  text-align: left;
  transition: border-color .15s, transform .15s;
}

.showcase__card:hover {
  border-color: var(--hp-bright);
  transform: translateY(-2px);
}

.showcase__thumb {
  display: grid;
  flex: none;
  width: 52px;
  height: 52px;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--hp-line);
  background: var(--hp-raised);
}

.showcase__thumb img {
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
  object-fit: cover;
  object-position: top;
}

.showcase__meta {
  min-width: 0;
}

.showcase__meta b {
  display: block;
  overflow: hidden;
  color: var(--hp-text);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.showcase__meta small {
  display: block;
  margin-top: 4px;
  color: var(--hp-faint);
  font: 9px 'DM Mono', monospace;
  letter-spacing: .08em;
}

.showcase__go {
  margin-left: auto;
  color: var(--hp-faint);
  transition: color .15s, transform .15s;
}

.showcase__card:hover .showcase__go {
  color: var(--hp-bright);
  transform: translateX(3px);
}

.showcase__fallback {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 44px;
  padding: 28px 26px;
  color: var(--hp-muted);
}

.showcase__fallback p {
  margin: 0;
}

.showcase__more {
  display: inline-block;
  margin-top: 22px;
  color: var(--hp-muted);
  font: 10px 'DM Mono', monospace;
  letter-spacing: .1em;
  text-decoration: none;
  transition: color .15s;
}

.showcase__more:hover {
  color: var(--hp-bright);
}

/* —— 接入服务器区。 —— */
.start {
  padding: 96px 0;
}

/* —— 接入步骤。 —— */
.start .panel + .panel {
  margin-top: 14px;
}

.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 44px;
}

.step {
  padding: 26px 24px;
  border-left: 1px solid var(--hp-line);
}

.step:first-child {
  border-left: 0;
}

.step__no {
  display: block;
  color: var(--hp-faint);
  font: 10px 'DM Mono', monospace;
  letter-spacing: .1em;
}

.step h3 {
  margin: 14px 0 9px;
  font-size: 15px;
  font-weight: 600;
}

.step p {
  margin: 0;
  color: var(--hp-muted);
  font-size: 12.5px;
  line-height: 1.85;
}

.api {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 20px;
}

.api__label {
  display: block;
  color: var(--hp-faint);
  font: 9px 'DM Mono', monospace;
  letter-spacing: .12em;
}

.api__url {
  display: block;
  margin-top: 7px;
  color: var(--hp-text);
  font: 12px 'DM Mono', monospace;
  word-break: break-all;
}

.api__copy {
  min-height: 38px;
  white-space: nowrap;
}

.api__copy--done {
  border-color: var(--hp-green);
  color: var(--hp-bright);
}

.api__copy--fail {
  border-color: var(--hp-red);
  color: var(--hp-red);
}

/* —— 深色 CTA 条（MUA 风格：整条深色 + 左文右按钮）。 —— */
.cta {
  margin-top: 40px;
  background: linear-gradient(180deg, rgba(10, 6, 3, .62), rgba(10, 6, 3, .62)), var(--hp-edge);
  border-top: 1px solid var(--hp-line);
  border-bottom: 1px solid var(--hp-line);
}

.cta__inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 54px 0;
}

.cta__copy {
  max-width: 620px;
}

.cta__text {
  margin: 16px 0 0;
  color: var(--hp-muted);
  font-size: 13.5px;
  line-height: 1.9;
}

.cta__status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 20px 0 0;
  color: var(--hp-faint);
  font: 10px 'DM Mono', monospace;
  letter-spacing: .06em;
}

.cta__action .btn {
  min-height: 50px;
  padding: 0 34px;
}

/* —— 页脚（MUA 风格：左版权 + ICP 备案，右 Powered by）。 —— */
.footer {
  border-top: 1px solid var(--hp-line);
  background: rgba(8, 5, 2, .5);
}

.footer__inner {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0 24px;
}

.footer__copy {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: var(--hp-faint);
  font-size: 12px;
}

.footer__sep {
  color: var(--hp-line);
}

.footer__icp {
  color: var(--hp-muted);
  text-decoration: none;
  transition: color .15s;
}

.footer__icp:hover {
  color: var(--hp-bright);
}

.footer__by {
  margin: 0;
  color: var(--hp-faint);
  font-size: 12px;
}

.footer__by a {
  color: var(--hp-muted);
  text-decoration: none;
  transition: color .15s;
}

.footer__by a:hover {
  color: var(--hp-bright);
}

/* —— 响应式。 —— */
@media (min-width: 761px) and (max-width: 1024px) {
  .hero__content {
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: 44px;
  }

  .showcase__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 900px) {
  .hero__content {
    display: block;
    padding: 116px 0 96px;
  }

  .stage {
    max-width: 420px;
    margin-top: 52px;
  }

  .stage__body {
    height: 320px;
  }

  .character {
    transform: scale(.82);
  }
}

@media (max-width: 760px) {
  .shell {
    width: calc(100% - 40px);
  }

  .nav__toggle {
    display: block;
  }

  .nav__menu {
    position: absolute;
    top: 64px;
    right: 0;
    left: 0;
    display: none;
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    padding: 6px 20px 18px;
    border-bottom: 1px solid var(--hp-line);
    background: rgba(8, 12, 10, .98);
  }

  .nav__menu--open {
    display: flex;
  }

  .nav__menu a,
  .nav__login {
    padding: 13px 0;
    border-bottom: 1px solid var(--hp-line);
  }

  .nav__menu a.active::after {
    display: none;
  }

  .nav__divider {
    display: none;
  }

  .nav__cta {
    margin-top: 14px;
  }

  .hero__content {
    padding: 108px 0 88px;
  }

  .hero h1 {
    font-size: clamp(32px, 9vw, 44px);
  }

  .hero__foot span:last-child {
    display: none;
  }

  .union__grid {
    grid-template-columns: 1fr;
    margin-top: 32px;
  }

  .showcase,
  .union,
  .start {
    padding: 68px 0;
  }

  .showcase {
    padding-top: 68px;
  }

  .section-head {
    display: block;
  }

  .section-head__aside {
    margin-top: 18px;
    text-align: left;
  }

  .showcase__grid {
    grid-template-columns: 1fr;
    margin-top: 32px;
  }

  .steps {
    grid-template-columns: 1fr;
    margin-top: 32px;
  }

  .step {
    border-left: 0;
    border-top: 1px solid var(--hp-line);
  }

  .step:first-child {
    border-top: 0;
  }

  .cta__inner {
    padding: 40px 0;
  }

  .footer__inner {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-page *,
  .home-page *::before,
  .home-page *::after {
    transition-duration: .01ms !important;
  }

  html {
    scroll-behavior: auto;
  }
}
</style>
