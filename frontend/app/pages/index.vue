<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'

const router = useRouter()
const menuOpen = ref(false)
const scrolled = ref(false)
const activeSkin = ref(0)
const activeFilter = ref('全部')

const skins = [
  {
    name: 'Mossbound',
    cn: '苔原漫游者',
    creator: 'Lina Park',
    accent: '#92BE4B',
    jacket: '#5F7B4A',
    shirt: '#D4C9A2',
    hair: '#2A302B'
  },
  {
    name: 'Emberline',
    cn: '余烬航线',
    creator: 'Kaito',
    accent: '#D46647',
    jacket: '#8D463B',
    shirt: '#D5A07C',
    hair: '#362621'
  },
  {
    name: 'Night Shift',
    cn: '深夜值班',
    creator: 'mira.exe',
    accent: '#68A6A0',
    jacket: '#385A62',
    shirt: '#A8C8C2',
    hair: '#20272C'
  },
]

const library = [
  {name: 'Cedar Scout', creator: 'mochi', category: '自然系', tone: 'cedar', model: 'classic'},
  {name: 'Solaris', creator: 'akari', category: '冒险系', tone: 'solar', model: 'slim'},
  {name: 'Deep Current', creator: 'rune', category: '极简系', tone: 'current', model: 'classic'},
  {name: 'Field Notes', creator: 'yun', category: '自然系', tone: 'field', model: 'slim'},
  {name: 'Redstone Club', creator: 'sora', category: '冒险系', tone: 'redstone', model: 'classic'},
  {name: 'Cloud Archive', creator: 'niko', category: '极简系', tone: 'cloud', model: 'slim'},
]

const filters = ['全部', '自然系', '冒险系', '极简系']
const filteredLibrary = computed(() => activeFilter.value === '全部' ? library : library.filter(item => item.category === activeFilter.value))
const selectedSkin = computed(() => skins[activeSkin.value])

function updateScroll() {
  scrolled.value = window.scrollY > 24
}

function goLogin() {
  menuOpen.value = false;
  router.push('/login')
}

function goRegister() {
  menuOpen.value = false;
  router.push('/login')
}

onMounted(() => {
  updateScroll();
  window.addEventListener('scroll', updateScroll, {passive: true})
})
onUnmounted(() => window.removeEventListener('scroll', updateScroll))
</script>

<template>
  <main class="home-page">
    <section id="top" class="hero">
      <div class="hero__image"/>
      <div class="hero__veil"/>
      <header :class="{ 'nav--solid': scrolled || menuOpen }" class="nav">
        <div class="nav__inner">
          <a aria-label="FoxSkin 首页" class="wordmark" href="#top" @click="menuOpen = false"><span
              class="wordmark__box">F</span><span>FOX<span class="wordmark__thin">SKIN</span></span></a>
          <button :aria-expanded="menuOpen" aria-controls="nav-menu" aria-label="切换菜单" class="nav__toggle"
                  type="button" @click="menuOpen = !menuOpen"><i/><i/></button>
          <nav id="nav-menu" :class="{ 'nav__menu--open': menuOpen }" aria-label="主导航" class="nav__menu">
            <a href="#library" @click="menuOpen = false">探索皮肤</a>
            <a href="#how-it-works" @click="menuOpen = false">如何使用</a>
            <a href="#about" @click="menuOpen = false">关于 FoxSkin</a>
            <span aria-hidden="true" class="nav__divider"/>
            <button class="nav__login" type="button" @click="goLogin">登录</button>
            <button class="nav__join" type="button" @click="goRegister">加入社区 <span aria-hidden="true">↗</span>
            </button>
          </nav>
        </div>
      </header>

      <div class="hero__content shell">
        <div class="hero__copy">
          <p class="overline"><span class="status-dot"/> FOXSKIN / CHARACTER ARCHIVE</p>
          <h1>今天想成为<br><em>{{ selectedSkin.cn }}。</em></h1>
          <p class="hero__lead">收好每一张皮肤，换上它，再出发。<br>FoxSkin 让你的角色始终有自己的样子。</p>
          <div class="hero__actions">
            <button class="btn btn--lime" type="button" @click="goRegister">打开我的衣柜 <span
                aria-hidden="true">↗</span></button>
            <a class="btn btn--line" href="#library">查看社区档案 <span aria-hidden="true">↓</span></a>
          </div>
        </div>

        <div aria-label="皮肤预览" class="character-stage">
          <div class="character-stage__label"><span>LIVE PREVIEW</span><b>{{ String(activeSkin + 1).padStart(2, '0') }}
            / 03</b></div>
          <div
              :style="{ '--accent': selectedSkin.accent, '--jacket': selectedSkin.jacket, '--shirt': selectedSkin.shirt, '--hair': selectedSkin.hair }"
              class="character">
            <div class="character__shadow"/>
            <div class="character__head"/>
            <div class="character__neck"/>
            <div class="character__torso"/>
            <div class="character__arm character__arm--left"/>
            <div class="character__arm character__arm--right"/>
            <div class="character__leg character__leg--left"/>
            <div class="character__leg character__leg--right"/>
          </div>
          <div class="character-stage__caption"><span>{{ selectedSkin.name }}</span><small>by {{
              selectedSkin.creator
            }}</small></div>
          <div class="skin-switcher">
            <button v-for="(skin, index) in skins" :key="skin.name" :aria-label="`选择 ${skin.name}`"
                    :class="{ active: activeSkin === index }" class="skin-switcher__item" type="button"
                    @click="activeSkin = index"><span :style="{ background: skin.accent }"/></button>
          </div>
        </div>
      </div>
      <div class="hero__foot shell"><span>JAVA · BEDROCK · YGGDRASIL</span><span>SCROLL TO BROWSE <b>↓</b></span></div>
    </section>

    <section id="how-it-works" class="intro shell">
      <div class="intro__title">
        <div><p class="kicker">THE FOXSKIN METHOD</p>
          <h2>皮肤不是装饰，<br><span>是你的入场方式。</span></h2></div>
        <p class="intro__text">
          把喜欢的样子留在一个地方。上传、预览、切换，然后带着它进入服务器。没有多余步骤，只有更像你的角色。</p></div>
      <div class="method-grid">
        <article><b>01</b><span class="method-icon">▦</span>
          <h3>收进衣柜</h3>
          <p>上传皮肤与披风，随时查看完整档案。</p></article>
        <article><b>02</b><span class="method-icon">◒</span>
          <h3>换上新样子</h3>
          <p>在进入游戏前预览每个细节，选择今天的状态。</p></article>
        <article><b>03</b><span class="method-icon">↗</span>
          <h3>走进服务器</h3>
          <p>连接 Yggdrasil，让你的外观在旅途中保持一致。</p></article>
      </div>
    </section>

    <section id="library" class="library">
      <div class="shell">
        <div class="library__heading">
          <div><p class="kicker">COMMUNITY ARCHIVE / 06</p>
            <h2>最近有人<br><span>这样出发。</span></h2></div>
          <p class="library__aside">每天都有新的角色加入。<br>挑一张，给它一个名字。</p></div>
        <div aria-label="皮肤分类" class="filters" role="tablist">
          <button v-for="filter in filters" :key="filter" :aria-selected="activeFilter === filter"
                  :class="{ active: activeFilter === filter }"
                  role="tab" type="button"
                  @click="activeFilter = filter">{{ filter }}
          </button>
        </div>
        <div class="archive-grid">
          <article v-for="item in filteredLibrary" :key="item.name" class="archive-card">
            <div :class="`archive-card__visual--${item.tone}`" class="archive-card__visual"><span
                class="archive-card__index">{{ String(library.indexOf(item) + 1).padStart(2, '0') }}</span>
              <div :class="`mini-character--${item.model}`" class="mini-character"><i class="mini-character__head"/><i
                  class="mini-character__body"/><i class="mini-character__leg mini-character__leg--l"/><i
                  class="mini-character__leg mini-character__leg--r"/></div>
              <span class="archive-card__view">查看档案 ↗</span></div>
            <div class="archive-card__meta">
              <div><h3>{{ item.name }}</h3>
                <p>by {{ item.creator }} <span>·</span> {{ item.category }}</p></div>
              <span class="archive-card__arrow">↗</span></div>
          </article>
        </div>
        <a class="archive-more" href="#top">浏览完整档案 <span>06 / 12480</span><b>↗</b></a>
      </div>
    </section>

    <section id="about" class="about shell">
      <div class="about__stamp">F</div>
      <p class="kicker">YOUR SERVER, YOUR LOOK</p>
      <h2>给角色一个<br><span>值得记住的样子。</span></h2>
      <p>开源、轻量、为 Minecraft 玩家准备。<br>从这一张皮肤开始，建立你的专属衣柜。</p>
      <button class="btn btn--dark" type="button" @click="goRegister">创建 FoxSkin 账号 <span>↗</span></button>
      <div class="about__status"><span><i/>服务运行正常</span><span>powered by Yggdrasil</span></div>
    </section>
    <footer class="footer shell"><span>© {{
        new Date().getFullYear()
      }} FOXSKIN</span><span>OPEN SOURCE SKIN SERVER</span><a
        href="https://github.com/bs-community/blessing-skin-server" rel="noreferrer" target="_blank">GITHUB ↗</a>
    </footer>
  </main>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Archivo+Black&family=IBM+Plex+Mono:wght@400;500&family=Noto+Sans+SC:wght@400;500;600;700&display=swap');

:root {
  --ink: #17191b;
  --pine: #23463b;
  --moss: #92be4b;
  --cloud: #edf0ea;
  --paper: #f7f8f4;
  --muted: #69736e;
  --line: #d6ddd4;
  --rust: #d46647
}

* {
  box-sizing: border-box
}

html {
  scroll-behavior: smooth
}

body {
  margin: 0;
  background: var(--paper);
  color: var(--ink);
  font-family: 'Noto Sans SC', sans-serif
}

.home-page {
  min-width: 320px;
  overflow: hidden
}

button, a {
  font: inherit
}

.shell {
  width: min(1180px, calc(100% - 72px));
  margin: 0 auto
}

.hero {
  position: relative;
  min-height: 760px;
  height: 100svh;
  max-height: 980px;
  isolation: isolate;
  color: #f8faf4;
  background: var(--pine)
}

.hero__image {
  position: absolute;
  inset: 0;
  z-index: -3;
  background: url('/blessing-bg.webp') 58% 48%/cover no-repeat;
  filter: saturate(.82) contrast(1.04)
}

.hero__veil {
  position: absolute;
  inset: 0;
  z-index: -2;
  background: linear-gradient(90deg, rgba(17, 27, 23, .94) 0%, rgba(22, 47, 38, .73) 42%, rgba(25, 57, 46, .22) 100%), linear-gradient(0deg, rgba(17, 27, 23, .68), transparent 48%)
}

.nav {
  position: fixed;
  z-index: 20;
  top: 0;
  width: 100%;
  transition: background .25s, box-shadow .25s, color .25s
}

.nav--solid {
  color: var(--ink);
  background: rgba(247, 248, 244, .95);
  box-shadow: 0 1px 0 rgba(23, 25, 27, .1);
  backdrop-filter: blur(14px)
}

.nav__inner {
  height: 78px;
  width: min(1180px, calc(100% - 72px));
  margin: auto;
  display: flex;
  align-items: center;
  justify-content: space-between
}

.wordmark {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-decoration: none;
  font: 14px 'Archivo Black', sans-serif;
  letter-spacing: .03em
}

.wordmark__box {
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  color: var(--ink);
  background: var(--moss);
  font: 17px 'Archivo Black', sans-serif;
  transform: rotate(-6deg)
}

.wordmark__thin {
  font-family: 'IBM Plex Mono', monospace;
  font-weight: 400
}

.nav__menu {
  display: flex;
  align-items: center;
  gap: 27px;
  font-size: 12px
}

.nav__menu a, .nav__login {
  color: inherit;
  text-decoration: none;
  opacity: .8
}

.nav__menu a:hover, .nav__login:hover {
  opacity: 1
}

.nav__divider {
  width: 1px;
  height: 18px;
  background: currentColor;
  opacity: .25
}

.nav__login, .nav__join {
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  font-size: 12px
}

.nav__join {
  padding: 11px 15px;
  color: var(--ink);
  background: var(--moss)
}

.nav__join span {
  margin-left: 13px
}

.nav__toggle {
  display: none;
  border: 0;
  padding: 8px;
  background: none;
  color: inherit
}

.nav__toggle i {
  display: block;
  width: 21px;
  height: 1px;
  margin: 5px;
  background: currentColor
}

.hero__content {
  display: grid;
  grid-template-columns:1fr 420px;
  align-items: center;
  gap: 90px;
  height: calc(100% - 95px);
  padding-top: 62px
}

.overline, .kicker, .hero__foot, .character-stage__label, .character-stage__caption small, .archive-card__index, .archive-card__view, .footer, .about__status {
  font: 10px 'IBM Plex Mono', monospace;
  letter-spacing: .09em
}

.overline {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 25px;
  color: #bdd582
}

.status-dot {
  width: 6px;
  height: 6px;
  background: var(--moss);
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(146, 190, 75, .18)
}

.hero h1 {
  margin: 0;
  font: 400 clamp(52px, 6.7vw, 86px)/1.05 'Noto Sans SC', sans-serif;
  letter-spacing: -.08em
}

.hero h1 em {
  color: var(--moss);
  font-style: normal;
  font-weight: 700
}

.hero__lead {
  margin: 25px 0 32px;
  color: rgba(244, 248, 241, .75);
  font-size: 15px;
  line-height: 1.9
}

.hero__actions {
  display: flex;
  gap: 12px;
  align-items: center
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
  min-height: 48px;
  padding: 0 18px;
  border: 1px solid transparent;
  cursor: pointer;
  text-decoration: none;
  font-size: 12px;
  font-weight: 600;
  transition: transform .2s, background .2s
}

.btn:hover {
  transform: translateY(-2px)
}

.btn:focus-visible, .nav a:focus-visible, .nav button:focus-visible, .filters button:focus-visible, .archive-more:focus-visible {
  outline: 2px solid var(--moss);
  outline-offset: 4px
}

.btn--lime {
  color: var(--ink);
  background: var(--moss)
}

.btn--lime:hover {
  background: #a8ce62
}

.btn--line {
  border-color: rgba(243, 248, 241, .42);
  color: #f3f8f1
}

.btn--line:hover {
  background: rgba(243, 248, 241, .12)
}

.character-stage {
  position: relative;
  align-self: center;
  height: 495px;
  border-left: 1px solid rgba(225, 239, 222, .24);
  border-bottom: 1px solid rgba(225, 239, 222, .24)
}

.character-stage__label {
  position: absolute;
  top: 0;
  left: 17px;
  right: 0;
  display: flex;
  justify-content: space-between;
  color: #d5e4d0
}

.character-stage__label b {
  font-weight: 400;
  color: var(--moss)
}

.character-stage__caption {
  position: absolute;
  right: 0;
  bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  text-align: right
}

.character-stage__caption span {
  font-size: 18px;
  font-weight: 600
}

.character-stage__caption small {
  color: #bdd2b9;
  letter-spacing: .02em
}

.character {
  position: absolute;
  left: 50%;
  top: 47%;
  width: 186px;
  height: 390px;
  transform: translate(-43%, -50%);
  filter: drop-shadow(25px 28px 15px rgba(8, 15, 11, .35));
  image-rendering: pixelated
}

.character__shadow {
  position: absolute;
  bottom: 1px;
  left: 13px;
  width: 165px;
  height: 15px;
  border-radius: 50%;
  background: rgba(11, 20, 14, .6);
  filter: blur(6px)
}

.character__head, .character__neck, .character__torso, .character__arm, .character__leg {
  position: absolute;
  background: var(--shirt);
  box-shadow: inset -13px 0 rgba(12, 35, 27, .18)
}

.character__head {
  top: 0;
  left: 45px;
  width: 98px;
  height: 98px;
  background: #dcad8b;
  box-shadow: inset 14px 0 #bf8069, inset -15px -8px rgba(81, 50, 38, .18)
}

.character__head:before {
  content: '';
  position: absolute;
  inset: 0 0 57px;
  background: var(--hair)
}

.character__head:after {
  content: '▪  ▪';
  position: absolute;
  left: 26px;
  top: 58px;
  color: #30332b;
  font-size: 10px;
  letter-spacing: 22px
}

.character__neck {
  top: 98px;
  left: 76px;
  width: 37px;
  height: 20px;
  background: #c68c70
}

.character__torso {
  top: 118px;
  left: 24px;
  width: 140px;
  height: 148px;
  background: var(--jacket);
  box-shadow: inset 21px 0 rgba(230, 239, 211, .1), inset -24px 0 rgba(9, 28, 22, .25)
}

.character__torso:after {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 28px;
  height: 148px;
  transform: translateX(-50%);
  background: var(--shirt);
  opacity: .9
}

.character__arm {
  top: 121px;
  width: 38px;
  height: 141px;
  background: var(--jacket)
}

.character__arm--left {
  left: 0
}

.character__arm--right {
  right: 0
}

.character__leg {
  top: 266px;
  width: 59px;
  height: 122px;
  background: #33483e;
  box-shadow: inset 16px 0 rgba(220, 239, 214, .09)
}

.character__leg--left {
  left: 24px
}

.character__leg--right {
  right: 24px
}

.hero__foot {
  position: absolute;
  right: 0;
  bottom: 24px;
  left: 0;
  display: flex;
  justify-content: space-between;
  color: rgba(229, 240, 225, .57)
}

.hero__foot b {
  margin-left: 8px;
  color: var(--moss);
  font-size: 15px
}

.skin-switcher {
  position: absolute;
  bottom: 13px;
  left: 17px;
  display: flex;
  gap: 7px
}

.skin-switcher__item {
  width: 20px;
  height: 20px;
  padding: 3px;
  border: 1px solid rgba(220, 237, 214, .38);
  background: transparent
}

.skin-switcher__item.active {
  border-color: var(--moss)
}

.skin-switcher__item span {
  display: block;
  width: 100%;
  height: 100%
}

.intro {
  padding-top: 124px;
  padding-bottom: 125px
}

.intro__title, .library__heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 40px
}

.kicker {
  margin: 0 0 19px;
  color: #5f7d3d
}

.intro h2, .library h2, .about h2 {
  margin: 0;
  font-size: clamp(38px, 4.6vw, 61px);
  line-height: 1.12;
  letter-spacing: -.075em;
  font-weight: 600
}

.intro h2 span, .library h2 span, .about h2 span {
  color: #9ba69f
}

.intro__text {
  max-width: 330px;
  margin: 0 0 5px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.85
}

.method-grid {
  display: grid;
  grid-template-columns:repeat(3, 1fr);
  margin-top: 86px;
  border-top: 1px solid var(--line)
}

.method-grid article {
  position: relative;
  min-height: 223px;
  padding: 27px 40px 20px 0;
  border-right: 1px solid var(--line)
}

.method-grid article + article {
  padding-left: 40px
}

.method-grid article:last-child {
  border-right: 0
}

.method-grid b {
  position: absolute;
  right: 25px;
  top: 28px;
  color: #aab6ab;
  font: 10px 'IBM Plex Mono', monospace
}

.method-icon {
  display: block;
  margin-bottom: 34px;
  color: var(--moss);
  font-size: 36px;
  line-height: 1
}

.method-grid h3 {
  margin: 0 0 10px;
  font-size: 17px
}

.method-grid p {
  max-width: 230px;
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8
}

.library {
  padding: 108px 0 118px;
  background: var(--cloud)
}

.library__aside {
  margin: 0 0 5px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8
}

.filters {
  display: flex;
  gap: 23px;
  margin-top: 56px;
  border-bottom: 1px solid #ccd4ca
}

.filters button {
  position: relative;
  padding: 0 0 14px;
  border: 0;
  background: none;
  color: #89938d;
  font-size: 12px;
  cursor: pointer
}

.filters button.active {
  color: var(--ink);
  font-weight: 600
}

.filters button.active:after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--moss)
}

.archive-grid {
  display: grid;
  grid-template-columns:repeat(3, 1fr);
  gap: 39px 15px;
  margin-top: 28px
}

.archive-card__visual {
  position: relative;
  height: 278px;
  display: grid;
  place-items: center;
  overflow: hidden
}

.archive-card__visual:after {
  content: '';
  position: absolute;
  inset: auto 0 0;
  height: 50%;
  background: linear-gradient(0deg, rgba(20, 30, 22, .2), transparent)
}

.archive-card__visual--cedar {
  background: #c9d4bd
}

.archive-card__visual--solar {
  background: #e4c1a3
}

.archive-card__visual--current {
  background: #a7c8c3
}

.archive-card__visual--field {
  background: #d8d0af
}

.archive-card__visual--redstone {
  background: #d4a097
}

.archive-card__visual--cloud {
  background: #c4cad0
}

.archive-card__index {
  position: absolute;
  z-index: 1;
  top: 13px;
  left: 14px;
  color: rgba(22, 35, 27, .56)
}

.archive-card__view {
  position: absolute;
  z-index: 2;
  right: 14px;
  bottom: 12px;
  color: #fff;
  opacity: 0;
  transform: translateY(5px);
  transition: .2s
}

.archive-card:hover .archive-card__view {
  opacity: 1;
  transform: none
}

.mini-character {
  position: relative;
  width: 76px;
  height: 188px;
  filter: drop-shadow(13px 17px 8px rgba(26, 35, 25, .2))
}

.mini-character i {
  position: absolute;
  display: block
}

.mini-character__head {
  top: 0;
  left: 17px;
  width: 43px;
  height: 43px;
  background: #dcae8b;
  box-shadow: inset 8px 0 #bf8069
}

.mini-character__head:before {
  content: '';
  position: absolute;
  inset: 0 0 25px;
  background: #29312a
}

.mini-character__body {
  top: 43px;
  left: 9px;
  width: 58px;
  height: 77px;
  background: #537456;
  box-shadow: inset 9px 0 rgba(235, 245, 220, .12)
}

.archive-card__visual--solar .mini-character__body {
  background: #a0513f
}

.archive-card__visual--current .mini-character__body {
  background: #3f6b70
}

.archive-card__visual--field .mini-character__body {
  background: #7d7651
}

.archive-card__visual--redstone .mini-character__body {
  background: #814238
}

.archive-card__visual--cloud .mini-character__body {
  background: #5e6976
}

.mini-character__leg {
  top: 120px;
  width: 24px;
  height: 68px;
  background: #3e5446
}

.mini-character__leg--l {
  left: 9px
}

.mini-character__leg--r {
  right: 9px
}

.mini-character--slim .mini-character__head {
  left: 21px;
  width: 36px
}

.mini-character--slim .mini-character__body {
  left: 15px;
  width: 47px
}

.mini-character--slim .mini-character__leg--l {
  left: 15px
}

.mini-character--slim .mini-character__leg--r {
  right: 14px
}

.archive-card__meta {
  display: flex;
  justify-content: space-between;
  padding-top: 13px
}

.archive-card h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600
}

.archive-card p {
  margin: 0;
  color: #7b8780;
  font: 10px 'IBM Plex Mono', monospace
}

.archive-card p span {
  padding: 0 5px;
  color: #a4ada6
}

.archive-card__arrow {
  color: #7c8980;
  font-size: 18px
}

.archive-more {
  display: flex;
  align-items: center;
  gap: 19px;
  width: max-content;
  margin: 73px auto 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #8e9b90;
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none
}

.archive-more span {
  color: #89948d;
  font: 10px 'IBM Plex Mono', monospace;
  font-weight: 400
}

.archive-more b {
  font-size: 17px
}

.about {
  padding-top: 127px;
  padding-bottom: 111px;
  text-align: center
}

.about__stamp {
  display: grid;
  position: relative;
  z-index: 1;
  width: 47px;
  height: 47px;
  place-items: center;
  margin: 0 auto 26px;
  color: var(--ink);
  background: var(--moss);
  font: 24px 'Archivo Black', sans-serif;
  transform: rotate(-6deg)
}

.about {
  position: relative
}

.about:before {
  content: '';
  position: absolute;
  top: 59px;
  left: 50%;
  width: 370px;
  height: 370px;
  border: 1px solid #e0e5de;
  border-radius: 50%;
  transform: translateX(-50%)
}

.about .kicker, .about h2, .about > p:not(.kicker), .about .btn, .about__status {
  position: relative;
  z-index: 1
}

.about h2 {
  font-size: clamp(42px, 5.1vw, 70px)
}

.about > p:not(.kicker) {
  margin: 23px 0 30px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8
}

.btn--dark {
  color: #f0f4ed;
  background: var(--ink)
}

.btn--dark:hover {
  background: #30423b
}

.about__status {
  display: flex;
  justify-content: center;
  gap: 26px;
  margin-top: 53px;
  color: #849087;
  font-size: 10px
}

.about__status i {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 7px;
  border-radius: 50%;
  background: #72a843
}

.footer {
  display: flex;
  justify-content: space-between;
  padding: 20px 0;
  color: #8a948c;
  border-top: 1px solid var(--line);
  font-size: 9px
}

.footer a {
  color: inherit;
  text-decoration: none
}

.footer a:hover {
  color: var(--ink)
}

@media (max-width: 760px) {
  .shell, .nav__inner {
    width: calc(100% - 40px)
  }

  .hero {
    min-height: 740px
  }

  .hero__image {
    background-position: 62% center
  }

  .nav__inner {
    height: 68px
  }

  .nav__toggle {
    display: block
  }

  .nav__menu {
    position: absolute;
    top: 68px;
    right: 12px;
    left: 12px;
    display: none;
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    padding: 8px 18px 17px;
    color: var(--ink);
    background: rgba(247, 248, 244, .98);
    box-shadow: 0 16px 35px rgba(13, 24, 18, .16)
  }

  .nav__menu--open {
    display: flex
  }

  .nav__menu a, .nav__login {
    padding: 13px 0;
    border-bottom: 1px solid var(--line)
  }

  .nav__divider {
    display: none
  }

  .nav__join {
    margin-top: 10px;
    text-align: center
  }

  .hero__content {
    display: block;
    height: auto;
    padding-top: 157px
  }

  .hero h1 {
    font-size: clamp(45px, 14vw, 70px)
  }

  .hero__lead {
    font-size: 13px
  }

  .hero__actions {
    flex-wrap: wrap
  }

  .character-stage {
    height: 345px;
    margin-top: 37px;
    border-left: 0
  }

  .character {
    top: 44%;
    transform: translate(-50%, -50%) scale(.74)
  }

  .character-stage__label {
    left: 0
  }

  .character-stage__caption {
    right: 0;
    bottom: 0
  }

  .skin-switcher {
    left: 0;
    bottom: 0
  }

  .hero__foot {
    right: 20px;
    bottom: 17px;
    left: 20px;
    font-size: 8px
  }

  .hero__foot span:last-child {
    display: none
  }

  .intro, .library, .about {
    padding-top: 80px;
    padding-bottom: 85px
  }

  .intro__title, .library__heading {
    display: block
  }

  .intro__text {
    margin-top: 22px
  }

  .method-grid {
    grid-template-columns:1fr;
    margin-top: 55px
  }

  .method-grid article, .method-grid article + article {
    min-height: 0;
    padding: 25px 35px 30px 0;
    border-right: 0;
    border-bottom: 1px solid var(--line)
  }

  .method-grid article:last-child {
    border-bottom: 0
  }

  .method-icon {
    margin-bottom: 22px
  }

  .filters {
    gap: 16px;
    margin-top: 42px;
    overflow: auto
  }

  .filters button {
    white-space: nowrap
  }

  .archive-grid {
    grid-template-columns:repeat(2, 1fr);
    gap: 28px 10px
  }

  .archive-card__visual {
    height: 210px
  }

  .mini-character {
    transform: scale(.8)
  }

  .archive-more {
    margin-top: 55px
  }

  .about:before {
    width: 285px;
    height: 285px
  }

  .footer {
    flex-wrap: wrap;
    gap: 10px;
    font-size: 8px
  }

  .footer span:nth-child(2) {
    order: 3;
    width: 100%
  }
}

.home-page * {
  letter-spacing: 0 !important
}

.hero h1 {
  font-size: 78px
}

.intro h2, .library h2 {
  font-size: 58px
}

.about h2 {
  font-size: 68px
}

@media (min-width: 761px) and (max-width: 1100px) {
  .hero h1 {
    font-size: 58px
  }

  .hero__content {
    grid-template-columns:1fr 360px;
    gap: 45px
  }

  .intro h2, .library h2 {
    font-size: 49px
  }
}

@media (max-width: 760px) {
  .hero {
    height: min(760px, 100svh);
    min-height: 700px
  }

  .hero__content {
    padding-top: 115px
  }

  .hero h1 {
    font-size: 48px
  }

  .hero__lead {
    margin: 16px 0 20px;
    font-size: 12px;
    line-height: 1.7
  }

  .character-stage {
    height: 220px;
    margin-top: 20px
  }

  .character {
    top: 43%;
    transform: translate(-50%, -50%) scale(.48)
  }

  .character-stage__caption {
    display: none
  }

  .intro {
    padding-top: 52px
  }

  .intro h2, .library h2 {
    font-size: 42px
  }

  .about h2 {
    font-size: 46px
  }
}

@media (prefers-reduced-motion: reduce) {
  *, *:before, *:after {
    scroll-behavior: auto !important;
    transition-duration: .01ms !important
  }
}
</style>
