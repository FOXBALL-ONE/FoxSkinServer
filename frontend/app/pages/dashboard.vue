<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'

type Skin = {
  id: number
  name: string
  model: 'Classic' | 'Slim'
  updated: string
  colors: string[]
}

const auth = useAuthStore()
const route = useRoute()
const activeView = ref('概览')
const activeSkinId = ref(1)
const copied = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const uploadNotice = ref('')

// 侧边栏各项都指向真实路由：概览留在本页，衣柜/披风/角色档案去用户中心对应页面。
const navigation = [
  { label: '概览', to: '/dashboard' },
  { label: '衣柜', to: '/closet' },
  { label: '披风', to: '/closet?type=cape' },
  { label: '角色档案', to: '/characters' }
]
const skins = ref<Skin[]>([
  {
    id: 1,
    name: 'Mossbound',
    model: 'Classic',
    updated: '刚刚使用',
    colors: ['#303a31', '#b77b61', '#668049', '#d8d1ae', '#354d43']
  },
  {
    id: 2,
    name: 'Emberline',
    model: 'Slim',
    updated: '8 月 26 日',
    colors: ['#3a2926', '#c8866c', '#91473d', '#d8a17d', '#3b4249']
  },
  {
    id: 3,
    name: 'Night Shift',
    model: 'Classic',
    updated: '8 月 18 日',
    colors: ['#272c31', '#c9876e', '#3f6268', '#aecbc5', '#313b47']
  },
  {
    id: 4,
    name: 'Cedar Scout',
    model: 'Slim',
    updated: '8 月 03 日',
    colors: ['#2d3428', '#c28b6e', '#7b7951', '#d1c598', '#465246']
  },
])

const activeSkin = computed(() => skins.value.find((skin) => skin.id === activeSkinId.value) ?? skins.value[0])
const displayName = computed(() => auth.user?.nickname || auth.user?.username || 'Alex')
const accountName = computed(() => auth.user?.username || 'alex.fox')

onMounted(() => auth.hydrate())

function selectSkin(id: number) {
  activeSkinId.value = id
  uploadNotice.value = ''
}

function openUpload() {
  fileInput.value?.click()
}

function importSkin(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const name = file.name.replace(/\.[^/.]+$/, '') || '未命名皮肤'
  const nextId = Math.max(...skins.value.map((skin) => skin.id)) + 1
  skins.value.unshift({
    id: nextId,
    name,
    model: 'Classic',
    updated: '刚刚导入',
    colors: ['#263130', '#c88870', '#5e8b7b', '#d6c9a8', '#36434c']
  })
  activeSkinId.value = nextId
  uploadNotice.value = `已添加 ${name}`
  input.value = ''
}

async function copyServerAddress() {
  try {
    await navigator.clipboard.writeText('https://skin.foxskin.local/api/yggdrasil')
    copied.value = true
    window.setTimeout(() => {
      copied.value = false
    }, 1800)
  } catch {
    copied.value = false
  }
}

async function signOut() {
  await auth.logout()
  await navigateTo('/')
}
</script>

<template>
  <main class="dashboard-page">
    <aside class="sidebar">
      <NuxtLink aria-label="返回 FoxSkin 首页" class="brand" to="/">
        <span class="brand__mark">F</span><span>FOX<span>SKIN</span></span>
      </NuxtLink>

      <nav aria-label="仪表盘导航" class="sidebar__nav">
        <NuxtLink v-for="item in navigation" :key="item.to" :class="{ active: route.fullPath === item.to }" :to="item.to">
          <span aria-hidden="true" class="nav-mark"/>{{ item.label }}
        </NuxtLink>
      </nav>

      <div class="sidebar__bottom">
        <NuxtLink class="settings-link" to="/account">
          <span aria-hidden="true" class="nav-mark"/>账户设置
        </NuxtLink>
        <div class="server-pill"><span/> YGGDRASIL 已连接</div>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumb"><span>FoxSkin</span><b>/</b><strong>{{ activeView }}</strong></div>
        <div class="account-area">
          <span class="server-status"><i/>服务正常</span>
          <button class="account" title="退出账号" type="button" @click="signOut">
            <span class="account__avatar">{{ displayName.slice(0, 1).toUpperCase() }}</span>
            <span><b>{{ displayName }}</b><small>@{{ accountName }}</small></span>
            <em aria-hidden="true">/</em>
          </button>
        </div>
      </header>

      <div class="content">
        <section class="welcome-row">
          <div>
            <p class="eyebrow">CHARACTER DESK / 01</p>
            <h1>你的角色，<br><em>准备出发。</em></h1>
          </div>
          <button class="primary-button" type="button" @click="openUpload">上传皮肤 <span aria-hidden="true">+</span>
          </button>
          <input ref="fileInput" accept="image/png" class="visually-hidden" type="file" @change="importSkin">
        </section>

        <p v-if="uploadNotice" class="upload-notice" role="status">{{ uploadNotice }}</p>

        <section aria-label="当前皮肤" class="hero-grid">
          <article class="character-panel">
            <div class="panel-line"><span>ACTIVE APPEARANCE</span>
              <time>同步于今天 09:41</time>
            </div>
            <div class="character-scene">
              <div class="scene-grid"/>
              <div class="scene-light"/>
              <div
                  :style="{ '--hair': activeSkin.colors[0], '--skin': activeSkin.colors[1], '--jacket': activeSkin.colors[2], '--shirt': activeSkin.colors[3], '--trousers': activeSkin.colors[4] }"
                  aria-label="当前皮肤像素预览" class="player-model">
                <i class="model-head"/><i class="model-neck"/><i class="model-body"/><i
                  class="model-arm model-arm--left"/><i class="model-arm model-arm--right"/><i
                  class="model-leg model-leg--left"/><i class="model-leg model-leg--right"/>
              </div>
              <div class="model-shadow"/>
              <div class="scene-caption"><b>{{ activeSkin.name }}</b><span>{{ activeSkin.model }} 模型</span></div>
              <div class="coordinate">X 18 / Y 64 / Z -03</div>
            </div>
            <footer class="character-panel__footer"><span><i/>已应用到 Minecraft 角色</span>
              <button type="button">查看角色档案 <b aria-hidden="true">-></b></button>
            </footer>
          </article>

          <aside class="connection-panel">
            <div class="connection-panel__top"><p class="eyebrow">GAME CONNECTION</p><span>在线</span></div>
            <h2>让游戏认出<br>现在的你。</h2>
            <p>将认证服务器地址加入启动器，皮肤与披风会自动同步到已连接的服务器。</p>
            <div class="address-box"><span>AUTH SERVER</span><code>skin.foxskin.local</code></div>
            <button class="copy-button" type="button" @click="copyServerAddress">
              <span>{{ copied ? '地址已复制' : '复制认证地址' }}</span><b aria-hidden="true">[]</b></button>
            <div class="connection-panel__bottom"><span>JAVA EDITION</span><span>BEDROCK 需插件</span></div>
          </aside>
        </section>

        <section class="closet-section">
          <div class="section-heading">
            <div><p class="eyebrow">MY WARDROBE / {{ String(skins.length).padStart(2, '0') }}</p>
              <h2>皮肤衣柜</h2></div>
            <button type="button" @click="openUpload">导入新皮肤 <b aria-hidden="true">+</b></button>
          </div>
          <div class="skin-grid">
            <button v-for="skin in skins" :key="skin.id" :class="{ active: activeSkinId === skin.id }" class="skin-card"
                    type="button" @click="selectSkin(skin.id)">
              <div
                  :style="{ '--hair': skin.colors[0], '--skin': skin.colors[1], '--jacket': skin.colors[2], '--shirt': skin.colors[3], '--trousers': skin.colors[4] }"
                  class="skin-card__visual">
                <div class="skin-card__model"><i/><i/><i/><i/></div>
                <span v-if="activeSkinId === skin.id">当前使用</span>
              </div>
              <span class="skin-card__meta"><b>{{ skin.name }}</b><small>{{ skin.model }} / {{
                  skin.updated
                }}</small></span>
            </button>
            <button class="skin-card skin-card--add" type="button" @click="openUpload">
              <span>+</span><b>添加皮肤</b><small>PNG, 64 x 64 或 64 x 32</small></button>
          </div>
        </section>

        <section class="lower-grid">
          <article class="activity-panel">
            <div class="section-heading">
              <div><p class="eyebrow">RECENT ACTIVITY</p>
                <h2>最近动态</h2></div>
              <button type="button">查看全部</button>
            </div>
            <ol>
              <li><span class="activity-dot activity-dot--green"/>
                <div><b>已切换至 {{ activeSkin.name }}</b>
                  <time>今天 09:41</time>
                </div>
              </li>
              <li><span class="activity-dot activity-dot--blue"/>
                <div><b>认证令牌已刷新</b>
                  <time>昨天 22:18</time>
                </div>
              </li>
              <li><span class="activity-dot activity-dot--orange"/>
                <div><b>披风 “Aurora” 已保存</b>
                  <time>8 月 27 日</time>
                </div>
              </li>
            </ol>
          </article>
          <article class="profile-panel"><p class="eyebrow">PLAYER PROFILE</p>
            <div class="profile-panel__identity"><span>{{ displayName.slice(0, 1).toUpperCase() }}</span>
              <div><h2>{{ displayName }}</h2>
                <p>@{{ accountName }}</p></div>
            </div>
            <dl>
              <div>
                <dt>账号状态</dt>
                <dd>已验证</dd>
              </div>
              <div>
                <dt>角色档案</dt>
                <dd>01 已创建</dd>
              </div>
            </dl>
            <button type="button" @click="activeView = '角色档案'">编辑公开档案 <b aria-hidden="true">-></b></button>
          </article>
        </section>
      </div>
    </section>
  </main>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');

:global(*) {
  box-sizing: border-box;
}

:global(body) {
  margin: 0;
  background: #eff1ed;
  color: #202724;
  font-family: 'DM Sans', sans-serif;
}

button {
  font: inherit;
  cursor: pointer;
}

.dashboard-page {
  display: grid;
  grid-template-columns: 228px minmax(0, 1fr);
  min-height: 100svh;
  background: #eff1ed;
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  width: 228px;
  padding: 27px 18px 21px;
  color: #e8eee5;
  background: #202b28;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 0 8px;
  color: inherit;
  font: 700 14px 'Space Grotesk', sans-serif;
  letter-spacing: .04em;
  text-decoration: none;
}

.brand > span:last-child > span {
  color: #9fad9e;
  font-family: 'DM Mono', monospace;
  font-weight: 400;
}

.brand__mark {
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  color: #1d2624;
  background: #d2e56c;
  font: 700 17px 'Space Grotesk', sans-serif;
  transform: rotate(-7deg);
}

.sidebar__nav {
  display: grid;
  gap: 4px;
  margin-top: 74px;
}

.sidebar__nav a, .settings-link {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 42px;
  padding: 0 11px;
  border: 0;
  color: #aeb9b0;
  background: transparent;
  text-align: left;
  text-decoration: none;
  font-size: 13px;
  transition: color .2s, background .2s;
}

.sidebar__nav a:hover, .settings-link:hover {
  color: #fff;
}

.sidebar__nav a.active {
  color: #f5f8f2;
  background: #34423c;
}

.nav-mark {
  width: 10px;
  height: 10px;
  border: 1px solid currentColor;
  opacity: .8;
}

.sidebar__nav a.active .nav-mark {
  background: #d2e56c;
  border-color: #d2e56c;
}

.sidebar__bottom {
  margin-top: auto;
}

.server-pill {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 21px 8px 0;
  color: #94a59a;
  font: 9px 'DM Mono', monospace;
}

.server-pill span, .server-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #75c590;
  box-shadow: 0 0 0 4px rgba(117, 197, 144, .1);
}

.workspace {
  min-width: 0;
  grid-column: 2;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 77px;
  padding: 0 clamp(28px, 5vw, 76px);
  border-bottom: 1px solid #d7ddd5;
}

.breadcrumb {
  display: flex;
  gap: 10px;
  color: #8a968d;
  font: 10px 'DM Mono', monospace;
}

.breadcrumb b {
  color: #bdc6bd;
  font-weight: 400;
}

.breadcrumb strong {
  color: #29332e;
  font-weight: 500;
}

.account-area, .account {
  display: flex;
  align-items: center;
}

.account-area {
  gap: 23px;
}

.server-status {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #718078;
  font: 10px 'DM Mono', monospace;
}

.server-status i {
  display: block;
  width: 5px;
  height: 5px;
  box-shadow: none;
}

.account {
  gap: 9px;
  padding: 4px;
  border: 0;
  background: transparent;
  color: #26302b;
  text-align: left;
}

.account:hover {
  background: #e2e6df;
}

.account__avatar {
  display: grid;
  width: 31px;
  height: 31px;
  place-items: center;
  background: #d6a47d;
  color: #43352c;
  font-size: 13px;
  font-weight: 700;
}

.account b, .account small {
  display: block;
}

.account b {
  font-size: 11px;
}

.account small {
  margin-top: 2px;
  color: #87928a;
  font: 9px 'DM Mono', monospace;
}

.account em {
  margin-left: 6px;
  color: #8b968e;
  font-style: normal;
  transform: rotate(90deg);
}

.content {
  width: min(1180px, calc(100% - 64px));
  margin: 0 auto;
  padding: 59px 0 72px;
}

.welcome-row, .section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.eyebrow {
  margin: 0 0 12px;
  color: #758c49;
  font: 10px 'DM Mono', monospace;
  letter-spacing: .06em;
}

.welcome-row h1 {
  margin: 0;
  font: 600 clamp(37px, 4.3vw, 58px)/1.01 'Space Grotesk', sans-serif;
  letter-spacing: 0;
}

.welcome-row h1 em {
  color: #78964d;
  font-style: normal;
}

.primary-button {
  display: inline-flex;
  align-items: center;
  gap: 30px;
  min-height: 46px;
  padding: 0 16px 0 19px;
  border: 1px solid #d2e56c;
  background: #d2e56c;
  color: #23302b;
  font-size: 12px;
  font-weight: 700;
  transition: transform .2s, background .2s;
}

.primary-button:hover {
  background: #dfed8d;
  transform: translateY(-2px);
}

.primary-button span {
  font-size: 21px;
  font-weight: 400;
}

.upload-notice {
  width: max-content;
  max-width: 100%;
  margin: 18px 0 -18px;
  padding: 7px 10px;
  color: #42644f;
  background: #d9eddf;
  font-size: 12px;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.58fr) minmax(255px, .67fr);
  gap: 19px;
  margin-top: 45px;
}

.character-panel, .connection-panel, .activity-panel, .profile-panel {
  border: 1px solid #d4dbd3;
  background: #f7f8f5;
}

.character-panel {
  overflow: hidden;
}

.panel-line {
  display: flex;
  justify-content: space-between;
  padding: 15px 18px;
  color: #7f8b83;
  border-bottom: 1px solid #d9dfd8;
  font: 9px 'DM Mono', monospace;
}

.panel-line time {
  color: #9ba69e;
}

.character-scene {
  position: relative;
  height: 344px;
  overflow: hidden;
  background: #dce5d9;
}

.scene-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(85, 109, 90, .1) 1px, transparent 1px), linear-gradient(90deg, rgba(85, 109, 90, .1) 1px, transparent 1px);
  background-size: 24px 24px;
}

.scene-light {
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, rgba(247, 248, 245, .75), transparent 52%), linear-gradient(0deg, rgba(67, 94, 76, .22), transparent 55%);
}

.player-model {
  position: absolute;
  z-index: 1;
  left: 50%;
  bottom: 25px;
  width: 132px;
  height: 265px;
  transform: translateX(-50%);
  filter: drop-shadow(13px 20px 10px rgba(34, 54, 43, .2));
}

.player-model i {
  position: absolute;
  display: block;
}

.model-head {
  top: 0;
  left: 34px;
  width: 64px;
  height: 64px;
  background: var(--skin);
  box-shadow: inset 8px 0 rgba(98, 48, 35, .2), inset -10px -6px rgba(87, 48, 36, .14);
}

.model-head::before {
  content: '';
  position: absolute;
  inset: 0 0 36px;
  background: var(--hair);
}

.model-head::after {
  content: '';
  position: absolute;
  top: 38px;
  left: 18px;
  width: 8px;
  height: 8px;
  background: #29322c;
  box-shadow: 20px 0 #29322c;
}

.model-neck {
  top: 64px;
  left: 58px;
  width: 17px;
  height: 14px;
  background: var(--skin);
}

.model-body {
  top: 77px;
  left: 20px;
  width: 92px;
  height: 100px;
  background: var(--jacket);
  box-shadow: inset 12px 0 rgba(255, 255, 255, .12), inset -14px 0 rgba(20, 40, 33, .18);
}

.model-body::after {
  content: '';
  position: absolute;
  top: 0;
  left: 37px;
  width: 18px;
  height: 100%;
  background: var(--shirt);
}

.model-arm {
  top: 79px;
  width: 20px;
  height: 94px;
  background: var(--jacket);
  box-shadow: inset 5px 0 rgba(255, 255, 255, .08);
}

.model-arm--left {
  left: 0;
}

.model-arm--right {
  right: 0;
}

.model-leg {
  top: 177px;
  width: 38px;
  height: 86px;
  background: var(--trousers);
  box-shadow: inset 8px 0 rgba(255, 255, 255, .1);
}

.model-leg--left {
  left: 20px;
}

.model-leg--right {
  right: 20px;
}

.model-shadow {
  position: absolute;
  z-index: 1;
  bottom: 19px;
  left: 50%;
  width: 140px;
  height: 15px;
  border-radius: 50%;
  background: rgba(52, 75, 60, .32);
  filter: blur(7px);
  transform: translateX(-50%);
}

.scene-caption {
  position: absolute;
  z-index: 2;
  bottom: 17px;
  left: 18px;
  display: grid;
  gap: 3px;
}

.scene-caption b {
  color: #25332b;
  font-size: 17px;
}

.scene-caption span, .coordinate {
  color: #627269;
  font: 9px 'DM Mono', monospace;
}

.coordinate {
  position: absolute;
  right: 16px;
  bottom: 16px;
}

.character-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 0 18px;
  color: #64736a;
  font-size: 11px;
}

.character-panel__footer > span {
  display: flex;
  align-items: center;
  gap: 7px;
}

.character-panel__footer i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #72bd8b;
}

.character-panel__footer button, .profile-panel > button {
  border: 0;
  background: transparent;
  color: #35483c;
  font-size: 11px;
  font-weight: 600;
}

.character-panel__footer b, .profile-panel b {
  margin-left: 6px;
  font-family: 'DM Mono', monospace;
  font-weight: 400;
}

.connection-panel {
  display: flex;
  flex-direction: column;
  padding: 23px;
  background: #283630;
  color: #edf1e9;
}

.connection-panel__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.connection-panel .eyebrow {
  color: #afc276;
}

.connection-panel__top > span {
  padding: 4px 7px;
  color: #a6d4b3;
  background: rgba(112, 188, 137, .14);
  font: 9px 'DM Mono', monospace;
}

.connection-panel h2 {
  margin: 36px 0 14px;
  font: 500 26px/1.1 'Space Grotesk', sans-serif;
  letter-spacing: 0;
}

.connection-panel > p {
  margin: 0;
  color: #b6c1b9;
  font-size: 12px;
  line-height: 1.75;
}

.address-box {
  display: grid;
  gap: 7px;
  margin-top: auto;
  padding: 12px;
  border: 1px solid #516158;
  background: #202b27;
}

.address-box span, .connection-panel__bottom {
  color: #91a298;
  font: 8px 'DM Mono', monospace;
}

.address-box code {
  color: #e0e9ad;
  font: 10px 'DM Mono', monospace;
  white-space: nowrap;
}

.copy-button {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  min-height: 40px;
  margin-top: 10px;
  padding: 0 12px;
  border: 1px solid #d2e56c;
  background: #d2e56c;
  color: #28352f;
  font-size: 11px;
  font-weight: 700;
}

.copy-button:hover {
  background: #dfed8d;
}

.copy-button b {
  font: 10px 'DM Mono', monospace;
}

.connection-panel__bottom {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  color: #8e9e95;
}

.closet-section {
  margin-top: 65px;
}

.section-heading {
  align-items: center;
}

.section-heading h2 {
  margin: 0;
  font: 600 25px 'Space Grotesk', sans-serif;
  letter-spacing: 0;
}

.section-heading > button {
  padding: 0;
  border: 0;
  color: #607169;
  background: transparent;
  font-size: 11px;
}

.section-heading > button:hover {
  color: #28362f;
  text-decoration: underline;
}

.section-heading b {
  margin-left: 8px;
  font-size: 16px;
  font-weight: 400;
}

.skin-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 13px;
  margin-top: 21px;
}

.skin-card {
  min-width: 0;
  padding: 0;
  border: 1px solid #d4dbd3;
  background: #f7f8f5;
  text-align: left;
  transition: transform .2s, border-color .2s, box-shadow .2s;
}

.skin-card:hover {
  border-color: #9caf82;
  transform: translateY(-2px);
}

.skin-card.active {
  border-color: #78964d;
  box-shadow: 0 0 0 2px rgba(120, 150, 77, .15);
}

.skin-card__visual {
  position: relative;
  display: grid;
  height: 142px;
  place-items: end center;
  overflow: hidden;
  background: linear-gradient(135deg, #d2dfcc, #bfcfc7);
  background-image: linear-gradient(rgba(73, 98, 83, .11) 1px, transparent 1px), linear-gradient(90deg, rgba(73, 98, 83, .11) 1px, transparent 1px);
  background-size: 16px 16px;
}

.skin-card__model {
  position: relative;
  width: 54px;
  height: 119px;
  margin-bottom: 0;
  filter: drop-shadow(5px 7px 3px rgba(40, 55, 47, .16));
}

.skin-card__model i {
  position: absolute;
  display: block;
}

.skin-card__model i:nth-child(1) {
  top: 0;
  left: 14px;
  width: 27px;
  height: 27px;
  background: var(--skin);
}

.skin-card__model i:nth-child(1)::before {
  content: '';
  position: absolute;
  inset: 0 0 15px;
  background: var(--hair);
}

.skin-card__model i:nth-child(2) {
  top: 27px;
  left: 8px;
  width: 38px;
  height: 46px;
  background: var(--jacket);
  box-shadow: inset 13px 0 var(--shirt);
}

.skin-card__model i:nth-child(3), .skin-card__model i:nth-child(4) {
  top: 73px;
  width: 16px;
  height: 46px;
  background: var(--trousers);
}

.skin-card__model i:nth-child(3) {
  left: 8px;
}

.skin-card__model i:nth-child(4) {
  right: 8px;
}

.skin-card__visual > span {
  position: absolute;
  top: 9px;
  left: 9px;
  padding: 3px 5px;
  color: #3e5736;
  background: #d9ee91;
  font: 8px 'DM Mono', monospace;
}

.skin-card__meta {
  display: grid;
  gap: 4px;
  padding: 11px 10px;
}

.skin-card__meta b {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skin-card__meta small {
  overflow: hidden;
  color: #859188;
  font: 8px 'DM Mono', monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skin-card--add {
  display: grid;
  min-height: 206px;
  place-content: center;
  justify-items: center;
  gap: 7px;
  border-style: dashed;
  color: #75827a;
  background: transparent;
  text-align: center;
}

.skin-card--add > span {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: #607e49;
  border: 1px solid #a9ba9c;
  border-radius: 50%;
  font-size: 20px;
}

.skin-card--add b {
  margin-top: 4px;
  color: #4b5d52;
  font-size: 11px;
}

.skin-card--add small {
  max-width: 120px;
  color: #8d978f;
  font-size: 9px;
  line-height: 1.5;
}

.lower-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(260px, .75fr);
  gap: 19px;
  margin-top: 65px;
}

.activity-panel, .profile-panel {
  padding: 22px;
}

.activity-panel ol {
  display: grid;
  gap: 16px;
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
}

.activity-panel li {
  display: grid;
  grid-template-columns: 10px 1fr;
  gap: 10px;
  align-items: start;
}

.activity-dot {
  width: 7px;
  height: 7px;
  margin-top: 5px;
  border-radius: 50%;
}

.activity-dot--green {
  background: #77bd89;
}

.activity-dot--blue {
  background: #7aa5bd;
}

.activity-dot--orange {
  background: #d99969;
}

.activity-panel li b, .activity-panel li time {
  display: block;
}

.activity-panel li b {
  color: #3d4b43;
  font-size: 11px;
  font-weight: 500;
}

.activity-panel li time {
  margin-top: 3px;
  color: #8c978f;
  font: 9px 'DM Mono', monospace;
}

.profile-panel {
  background: #dfe8d8;
  border-color: #cbd7c2;
}

.profile-panel__identity {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 22px;
}

.profile-panel__identity > span {
  display: grid;
  width: 43px;
  height: 43px;
  place-items: center;
  color: #40392f;
  background: #d9a37b;
  font-weight: 700;
}

.profile-panel h2 {
  margin: 0;
  font: 600 20px 'Space Grotesk', sans-serif;
  letter-spacing: 0;
}

.profile-panel__identity p {
  margin: 3px 0 0;
  color: #78867b;
  font: 9px 'DM Mono', monospace;
}

.profile-panel dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin: 25px 0 17px;
}

.profile-panel dl div {
  padding-top: 10px;
  border-top: 1px solid #bfceba;
}

.profile-panel dt {
  color: #758578;
  font: 8px 'DM Mono', monospace;
}

.profile-panel dd {
  margin: 5px 0 0;
  color: #405448;
  font-size: 10px;
  font-weight: 600;
}

.profile-panel > button {
  padding: 0;
  color: #3d5c43;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.primary-button:focus-visible, .sidebar button:focus-visible, .account:focus-visible, .copy-button:focus-visible, .skin-card:focus-visible, .section-heading button:focus-visible {
  outline: 2px solid #7c9f48;
  outline-offset: 3px;
}

@media (max-width: 950px) {
  .dashboard-page {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .sidebar {
    width: 72px;
    padding: 25px 10px 19px;
  }

  .brand {
    justify-content: center;
    padding: 0;
  }

  .brand > span:last-child, .sidebar__nav a:not(.active)::after, .sidebar__nav a {
    font-size: 0;
  }

  .sidebar__nav a, .settings-link {
    justify-content: center;
    padding: 0;
  }

  .sidebar__nav {
    margin-top: 62px;
  }

  .sidebar__bottom {
    display: grid;
    justify-items: center;
  }

  .server-pill {
    font-size: 0;
  }

  .workspace {
    grid-column: 2;
  }

  .skin-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .hero-grid {
    grid-template-columns: 1fr;
  }

  .connection-panel {
    min-height: 290px;
  }

  .connection-panel h2 {
    margin-top: 21px;
  }

  .address-box {
    margin-top: 23px;
  }

  .connection-panel__bottom {
    margin-top: 14px;
  }
}

@media (max-width: 620px) {
  .dashboard-page {
    display: block;
  }

  .sidebar {
    position: sticky;
    top: 0;
    right: 0;
    bottom: auto;
    z-index: 5;
    flex-direction: row;
    width: 100%;
    height: 60px;
    padding: 0 15px;
    align-items: center;
  }

  .brand {
    flex: none;
  }

  .sidebar__nav {
    display: flex;
    flex: 1;
    gap: 0;
    margin: 0 0 0 19px;
  }

  .sidebar__nav a {
    min-height: 36px;
  }

  .sidebar__bottom {
    display: none;
  }

  .workspace {
    display: block;
  }

  .topbar {
    height: 59px;
    padding: 0 20px;
  }

  .breadcrumb {
    display: none;
  }

  .account-area {
    margin-left: auto;
  }

  .server-status {
    display: none;
  }

  .content {
    width: calc(100% - 40px);
    padding: 38px 0 48px;
  }

  .welcome-row {
    align-items: flex-start;
  }

  .welcome-row h1 {
    font-size: 36px;
  }

  .primary-button {
    flex: none;
    gap: 10px;
    min-height: 42px;
    padding: 0 12px;
    font-size: 0;
  }

  .primary-button span {
    font-size: 20px;
  }

  .hero-grid {
    margin-top: 31px;
  }

  .character-scene {
    height: 299px;
  }

  .character-panel__footer {
    padding: 0 12px;
  }

  .character-panel__footer button {
    font-size: 0;
  }

  .character-panel__footer b {
    margin: 0;
    font-size: 15px;
  }

  .closet-section, .lower-grid {
    margin-top: 48px;
  }

  .skin-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 11px;
  }

  .skin-card--add {
    min-height: 206px;
  }

  .lower-grid {
    grid-template-columns: 1fr;
  }

  .section-heading h2 {
    font-size: 23px;
  }

  .connection-panel {
    min-height: 272px;
  }

  .profile-panel {
    min-height: 224px;
  }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    scroll-behavior: auto !important;
    transition-duration: .01ms !important;
  }
}
</style>
