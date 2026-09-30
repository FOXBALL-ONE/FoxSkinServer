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
    name: 'Mossbound',
    cn: '苔原漫游者',
    creator: 'Lina Park',
    accent: '#a9d36a',
    jacket: '#5f7b4a',
    shirt: '#d4c9a2',
    hair: '#2a302b',
    legs: '#33483e',
  },
  {
    name: 'Emberline',
    cn: '余烬航线',
    creator: 'Kaito',
    accent: '#d46647',
    jacket: '#8d463b',
    shirt: '#d5a07c',
    hair: '#362621',
    legs: '#3b3f4a',
  },
  {
    name: 'Night Shift',
    cn: '深夜值班',
    creator: 'mira.exe',
    accent: '#68a6a0',
    jacket: '#385a62',
    shirt: '#a8c8c2',
    hair: '#20272c',
    legs: '#2c3440',
  },
]
const selected = computed(() => characters[activeSkin.value]!)
const paddedIndex = computed(() => String(activeSkin.value + 1).padStart(2, '0'))

const navLinks = [
  {id: 'features', label: '功能'},
  {id: 'start', label: '接入服务器'},
  {id: 'about', label: '关于'},
]

const stats = [
  {label: '模型支持', value: 'Classic · Slim'},
  {label: '材质类型', value: '皮肤 · 披风'},
  {label: '认证协议', value: 'Yggdrasil'},
  {label: '部署方式', value: '开源 · 自托管'},
]

type PixelIcon = { palette: Record<string, string>; rows: string[] }

/** 8x8 像素图标：每行一个字符串，字符映射到调色板颜色，'.' 为透明。 */
const pixelIcons: Record<string, PixelIcon> = {
  chest: {
    palette: {a: '#8a5a34', b: '#4f3722', c: '#2a1d12', d: '#d5a936'},
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
  grass: {
    palette: {g: '#769d4c', l: '#a9d36a', d: '#6b4b2e', e: '#4f3722'},
    rows: [
      'gglggggg',
      'glggglgg',
      'gggggglg',
      'dddddddd',
      'dedddded',
      'dddedddd',
      'dedddded',
      'dddddddd',
    ],
  },
  steve: {
    palette: {h: '#3a2a1c', s: '#c68c64', w: '#e8ece5', b: '#73b5e8', n: '#a9714e', m: '#8a5a3c'},
    rows: [
      'hhhhhhhh',
      'hhhhhhhh',
      'ssssssss',
      'ssssssss',
      'swbsswbs',
      'ssssssss',
      'sssnnsss',
      'ssmmmmss',
    ],
  },
  beacon: {
    palette: {g: '#4f7a33', l: '#a9d36a', d: '#d5a936'},
    rows: [
      '...l....',
      '..glg...',
      '.glllg..',
      'gllddllg',
      '.glllg..',
      '..glg...',
      '...l....',
      '........',
    ],
  },
}

/** 把像素图转成一份 box-shadow：每格一格阴影，格距由 CSS 变量 --px 控制。 */
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
  }, 1800)
}

function go(path: string) {
  menuOpen.value = false
  router.push(path)
}

function closeMenu() {
  menuOpen.value = false
}

onMounted(() => {
  void auth.hydrate()
  updateNav()
  window.addEventListener('scroll', updateNav, {passive: true})
  const base = config.public.apiBase || '/api'
  yggdrasilRoot.value = new URL(`${base}/yggdrasil`, window.location.origin).toString()
  void probeService()
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
          <h1>给角色一个<br><em>值得记住的样子。</em></h1>
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

    <section aria-label="能力概览" class="stats">
      <div class="shell stats__grid">
        <div v-for="item in stats" :key="item.label" class="stats__cell">
          <span class="stats__label">{{ item.label }}</span>
          <strong class="stats__value">{{ item.value }}</strong>
        </div>
      </div>
    </section>

    <section id="features" class="features shell">
      <div class="section-head">
        <div>
          <p class="kicker">FEATURES / 04</p>
          <h2>一个皮肤站需要的<br><em>都在这里。</em></h2>
        </div>
        <p class="section-head__aside">上传、收藏、绑定角色、接入服务器。<br>每个环节都是面板里的一步操作。</p>
      </div>
      <div class="feature-grid">
        <article class="panel feature">
          <span aria-hidden="true" class="feature__icon"><i :style="{boxShadow: iconShadows.chest}"/></span>
          <h3>衣柜管理</h3>
          <p>上传 64×64 皮肤与披风，随时更换、预览，所有形象都收进个人衣柜。</p>
        </article>
        <article class="panel feature">
          <span aria-hidden="true" class="feature__icon"><i :style="{boxShadow: iconShadows.grass}"/></span>
          <h3>皮肤库共享</h3>
          <p>把公开材质放进皮肤库，其他玩家可以浏览并一键收录进自己的衣柜。</p>
        </article>
        <article class="panel feature">
          <span aria-hidden="true" class="feature__icon"><i :style="{boxShadow: iconShadows.steve}"/></span>
          <h3>角色档案</h3>
          <p>为每个玩家档案绑定皮肤与披风，进入服务器时外观即刻生效。</p>
        </article>
        <article class="panel feature">
          <span aria-hidden="true" class="feature__icon"><i :style="{boxShadow: iconShadows.beacon}"/></span>
          <h3>Yggdrasil 认证</h3>
          <p>兼容 authlib-injector 的外置登录，服务器侧一行配置即可接入。</p>
        </article>
      </div>
    </section>

    <section id="start" class="start shell">
      <div class="section-head">
        <div>
          <p class="kicker">GET STARTED / 03</p>
          <h2>三步，<em>把皮肤带进服务器。</em></h2>
        </div>
        <p class="section-head__aside">注册、上传、接入。<br>不需要邀请码，也不需要复杂配置。</p>
      </div>
      <div class="panel steps">
        <article class="step">
          <b class="step__no">01</b>
          <h3>注册账号</h3>
          <p>邮箱加用户名即可完成开放注册，注册成功直接进入控制台。</p>
        </article>
        <article class="step">
          <b class="step__no">02</b>
          <h3>上传材质</h3>
          <p>从本地上传皮肤与披风，或在皮肤库里挑选；Classic 与 Slim 都支持。</p>
        </article>
        <article class="step">
          <b class="step__no">03</b>
          <h3>接入服务器</h3>
          <p>在服务器端加载 authlib-injector，并把 API 根地址填进启动配置。</p>
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

    <section id="about" class="about shell">
      <div class="panel about__panel">
        <p class="kicker">ABOUT</p>
        <h2>你的社区，<em>值得一个自己的皮肤站。</em></h2>
        <p class="about__text">开源、轻量、为 Minecraft 社区准备。部署一次，全服玩家的外观与外置登录都从这里出发。</p>
        <template v-if="auth.isAuthenticated">
          <button class="btn btn--primary" type="button" @click="go('/dashboard')">打开控制台</button>
        </template>
        <template v-else>
          <button class="btn btn--primary" type="button" @click="go('/register')">创建 FoxSkin 账号</button>
        </template>
        <p class="about__status"><span :class="`dot dot--${serviceStatus}`" aria-hidden="true"/>{{ statusText }} · powered by
          Yggdrasil</p>
      </div>
    </section>

    <footer class="footer shell">
      <span>© {{ new Date().getFullYear() }} FOXSKIN SERVER</span>
      <span>SELF-HOSTED SKIN & AUTH SERVER</span>
      <a :href="yggdrasilRoot || '#'" rel="noreferrer" target="_blank">YGGDRASIL API ↗</a>
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
  /* 美术规范 §3 的设计令牌。 */
  --hp-ink: #080c0a;
  --hp-panel: rgba(13, 18, 15, .94);
  --hp-raised: #141a16;
  --hp-edge: #050705;
  --hp-line: #283129;
  --hp-text: #e8ece5;
  --hp-muted: #aab4a8;
  --hp-faint: #748077;
  --hp-green: #769d4c;
  --hp-bright: #a9d36a;
  --hp-blue: #73b5e8;
  --hp-gold: #d5a936;
  --hp-red: #c55a4a;
  --hp-ink-on-accent: #0c120c;

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
  filter: saturate(.6) contrast(1.06) brightness(.5);
}

.home-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
      linear-gradient(90deg, rgba(8, 12, 10, .92) 0%, rgba(8, 12, 10, .72) 44%, rgba(8, 12, 10, .38) 100%),
      linear-gradient(0deg, rgba(8, 12, 10, .9) 8%, rgba(8, 12, 10, .34) 46%, rgba(8, 12, 10, .1) 100%);
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
  background: var(--hp-green);
  box-shadow: 0 0 0 3px rgba(118, 157, 76, .18);
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
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  color: var(--hp-ink-on-accent);
  background: var(--hp-bright);
  font: 700 16px 'Space Grotesk', sans-serif;
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
      linear-gradient(rgba(169, 211, 106, .05) 1px, transparent 1px),
      linear-gradient(90deg, rgba(169, 211, 106, .05) 1px, transparent 1px);
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
      10px 0 0 #5a8bb0,
      40px 0 0 #e8ece5,
      50px 0 0 #5a8bb0;
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

/* —— 能力概览条。 —— */
.stats {
  border-top: 1px solid var(--hp-line);
  border-bottom: 1px solid var(--hp-line);
  background: rgba(8, 12, 10, .72);
}

.stats__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}

.stats__cell {
  padding: 22px 24px;
  border-left: 1px solid var(--hp-line);
}

.stats__cell:first-child {
  border-left: 0;
  padding-left: 0;
}

.stats__label {
  display: block;
  color: var(--hp-faint);
  font: 9px 'DM Mono', monospace;
  letter-spacing: .12em;
}

.stats__value {
  display: block;
  margin-top: 8px;
  color: var(--hp-text);
  font-size: 14px;
  font-weight: 600;
}

/* —— 板块标题行。 —— */
.features,
.start {
  padding: 96px 0;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 40px;
}

.section-head__aside {
  margin: 0 0 4px;
  color: var(--hp-muted);
  font-size: 13px;
  line-height: 1.85;
}

/* —— 像素图标。 —— */
.feature__icon {
  --px: 3px;
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border: 1px solid var(--hp-line);
  background: var(--hp-raised);
}

.feature__icon i {
  display: block;
  width: var(--px);
  height: var(--px);
}

/* —— 功能面板。 —— */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-top: 44px;
}

.feature {
  padding: 24px 22px;
  transition: border-color .15s;
}

.feature:hover {
  border-color: var(--hp-line);
}

.feature__icon + h3 {
  margin: 18px 0 10px;
  font-size: 15px;
  font-weight: 600;
}

.feature p {
  margin: 0;
  color: var(--hp-muted);
  font-size: 12.5px;
  line-height: 1.85;
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
  color: var(--hp-gold);
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
  color: var(--hp-bright);
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

/* —— 关于 / CTA。 —— */
.about {
  padding: 0 0 96px;
}

.about__panel {
  padding: 56px 32px;
  text-align: center;
}

.about__panel .kicker {
  justify-content: center;
}

.about__text {
  max-width: 460px;
  margin: 18px auto 28px;
  color: var(--hp-muted);
  font-size: 13px;
  line-height: 1.9;
}

.about__status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 34px 0 0;
  color: var(--hp-faint);
  font: 10px 'DM Mono', monospace;
  letter-spacing: .06em;
}

/* —— 页脚。 —— */
.footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 26px;
  justify-content: space-between;
  padding: 22px 0 26px;
  border-top: 1px solid var(--hp-line);
  color: var(--hp-faint);
  font: 9px 'DM Mono', monospace;
  letter-spacing: .1em;
}

.footer a {
  color: inherit;
  text-decoration: none;
  transition: color .15s;
}

.footer a:hover {
  color: var(--hp-bright);
}

/* —— 响应式。 —— */
@media (min-width: 761px) and (max-width: 1024px) {
  .hero__content {
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: 44px;
  }

  .feature-grid {
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
    font-size: clamp(34px, 9vw, 46px);
  }

  .hero__foot span:last-child {
    display: none;
  }

  .stats__grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stats__cell {
    padding: 18px 20px;
  }

  .stats__cell:nth-child(odd) {
    border-left: 0;
    padding-left: 0;
  }

  .features,
  .start {
    padding: 68px 0;
  }

  .section-head {
    display: block;
  }

  .section-head__aside {
    margin-top: 18px;
  }

  .feature-grid {
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

  .about__panel {
    padding: 40px 22px;
  }

  .footer {
    font-size: 8px;
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
