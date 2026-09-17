<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 当前高亮的导航项，对应下面 items 的 key。 */
  active: string
  /** 顶栏面包屑右侧的当前页名。 */
  crumb: string
}>()

const { t } = useI18n()
const auth = useAuthStore()
const preferences = usePreferencesStore()

const items = [
  { key: 'overview', labelKey: 'shell.nav.overview', to: '/dashboard' },
  { key: 'closet', labelKey: 'shell.nav.closet', to: '/closet' },
  { key: 'capes', labelKey: 'shell.nav.capes', to: '/closet?type=cape' },
  { key: 'characters', labelKey: 'shell.nav.characters', to: '/characters' },
]

const displayName = computed(() => auth.user?.nickname || auth.user?.username || 'Alex')
const accountName = computed(() => auth.user?.username || 'alex.fox')
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())
const avatarUrl = computed(() => auth.profile?.avatar_url ?? null)

// 顶栏头像是每个页面都要显示的信息，这里兜底加载一次完整资料。
onMounted(() => {
  if (auth.isAuthenticated && !auth.profile) void auth.loadProfile()
})

async function signOut() {
  await auth.logout()
  await navigateTo('/')
}
</script>

<template>
  <main class="dashboard-page">
    <aside class="sidebar">
      <NuxtLink :aria-label="t('shell.backHome')" class="brand" to="/">
        <span class="brand__mark">F</span><span>FOX<span>SKIN</span></span>
      </NuxtLink>

      <nav :aria-label="t('shell.navLabel')" class="sidebar__nav">
        <NuxtLink v-for="item in items" :key="item.key" :class="{ active: props.active === item.key }" :to="item.to">
          <span aria-hidden="true" class="nav-mark"/>{{ t(item.labelKey) }}
        </NuxtLink>
      </nav>

      <div class="sidebar__bottom">
        <NuxtLink class="settings-link" :class="{ active: props.active === 'account' }" to="/account">
          <span aria-hidden="true" class="nav-mark"/>{{ t('shell.account') }}
        </NuxtLink>
        <div class="server-pill"><span/> {{ t('shell.yggdrasilConnected') }}</div>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumb"><span>FoxSkin</span><b>/</b><strong>{{ crumb }}</strong></div>
        <div class="account-area">
          <span class="server-status"><i/>{{ t('shell.serviceOk') }}</span>
          <NuxtLink class="account" :title="t('shell.account')" to="/account">
            <span class="account__avatar">
              <img v-if="avatarUrl" :src="avatarUrl" alt="">
              <template v-else>{{ avatarInitial }}</template>
            </span>
            <span><b>{{ displayName }}</b><small>@{{ accountName }}</small></span>
            <em aria-hidden="true">/</em>
          </NuxtLink>
          <button class="theme-toggle" :aria-label="t('preferences.theme')" type="button"
                  @click="preferences.toggleColorMode()">
            {{ preferences.isDark ? '☾' : '☀' }}
          </button>
          <button class="signout" type="button" @click="signOut">{{ t('shell.signOut') }}</button>
        </div>
      </header>

      <div class="content">
        <slot/>
      </div>
    </section>
  </main>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');

:global(*) { box-sizing: border-box; }
:global(body) { margin: 0; background: var(--surface-page); color: var(--text); font-family: 'DM Sans', sans-serif; }
button { font: inherit; cursor: pointer; }

.dashboard-page { display: grid; grid-template-columns: 228px minmax(0, 1fr); min-height: 100svh; background: var(--surface-page); }
.sidebar { position: fixed; inset: 0 auto 0 0; z-index: 3; display: flex; flex-direction: column; width: 228px; padding: 27px 18px 21px; color: var(--sidebar-text-strong); background: var(--sidebar); }
.brand { display: inline-flex; align-items: center; gap: 9px; padding: 0 8px; color: inherit; font: 700 14px 'Space Grotesk', sans-serif; letter-spacing: .04em; text-decoration: none; }
.brand > span:last-child > span { color: var(--sidebar-text); font-family: 'DM Mono', monospace; font-weight: 400; }
.brand__mark { display: grid; width: 27px; height: 27px; place-items: center; color: var(--accent-ink); background: var(--sidebar-mark); font: 700 17px 'Space Grotesk', sans-serif; transform: rotate(-7deg); }
.sidebar__nav { display: grid; gap: 4px; margin-top: 74px; }
.sidebar__nav a, .settings-link { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 42px; padding: 0 11px; border: 0; color: var(--sidebar-text); background: transparent; text-align: left; font-size: 13px; text-decoration: none; transition: color .2s, background .2s; }
.sidebar__nav a:hover, .settings-link:hover { color: #fff; }
.sidebar__nav a.active, .settings-link.active { color: var(--sidebar-text-strong); background: var(--sidebar-active); }
.nav-mark { width: 10px; height: 10px; border: 1px solid currentColor; opacity: .8; }
.sidebar__nav a.active .nav-mark, .settings-link.active .nav-mark { background: var(--sidebar-mark); border-color: var(--sidebar-mark); }
.sidebar__bottom { margin-top: auto; }
.server-pill { display: flex; align-items: center; gap: 7px; margin: 21px 8px 0; color: var(--sidebar-text); font: 9px 'DM Mono', monospace; }
.server-pill span, .server-status i { width: 6px; height: 6px; border-radius: 50%; background: #75c590; box-shadow: 0 0 0 4px rgba(117, 197, 144, .1); }
.workspace { min-width: 0; grid-column: 2; }
.topbar { display: flex; align-items: center; justify-content: space-between; height: 77px; padding: 0 clamp(28px, 5vw, 76px); border-bottom: 1px solid var(--border); }
.breadcrumb { display: flex; gap: 10px; color: var(--text-faint); font: 10px 'DM Mono', monospace; }
.breadcrumb b { color: var(--border-strong); font-weight: 400; }
.breadcrumb strong { color: var(--text-strong); font-weight: 500; }
.account-area, .account { display: flex; align-items: center; }
.account-area { gap: 14px; }
.server-status { display: flex; align-items: center; gap: 7px; color: var(--text-muted); font: 10px 'DM Mono', monospace; }
.server-status i { display: block; width: 5px; height: 5px; box-shadow: none; }
.account { gap: 9px; padding: 4px; color: var(--text); text-decoration: none; }
.account:hover { background: var(--surface-muted); }
.account__avatar { display: grid; width: 31px; height: 31px; place-items: center; overflow: hidden; background: #d6a47d; color: #43352c; font-size: 13px; font-weight: 700; }
.account__avatar img { width: 100%; height: 100%; object-fit: cover; }
.account b, .account small { display: block; }
.account b { font-size: 11px; }
.account small { margin-top: 2px; color: var(--text-faint); font: 9px 'DM Mono', monospace; }
.account em { margin-left: 6px; color: var(--text-faint); font-style: normal; transform: rotate(90deg); }
.theme-toggle { width: 30px; height: 30px; border: 1px solid var(--border-strong); background: transparent; color: var(--text-muted); font-size: 13px; line-height: 1; transition: color .2s, border-color .2s; }
.theme-toggle:hover { color: var(--text-strong); border-color: var(--accent-strong); }
.signout { padding: 6px 11px; border: 1px solid var(--border-strong); background: transparent; color: var(--text-muted); font: 10px 'DM Mono', monospace; transition: color .2s, border-color .2s; }
.signout:hover { color: var(--danger); border-color: var(--danger-border); }
.content { width: min(1180px, calc(100% - 64px)); margin: 0 auto; padding: 59px 0 72px; }
.eyebrow { margin: 0 0 12px; color: var(--accent-strong); font: 10px 'DM Mono', monospace; letter-spacing: .06em; }

@media (max-width: 950px) {
  .dashboard-page { grid-template-columns: 72px minmax(0, 1fr); }
  .sidebar { width: 72px; padding: 25px 10px 19px; }
  .brand { justify-content: center; padding: 0; }
  .brand > span:last-child { font-size: 0; }
  .sidebar__nav a, .settings-link { justify-content: center; padding: 0; font-size: 0; }
  .sidebar__nav { margin-top: 62px; }
  .sidebar__bottom { display: grid; justify-items: center; }
  .server-pill { font-size: 0; }
  .workspace { grid-column: 2; }
}

@media (max-width: 620px) {
  .dashboard-page { display: block; }
  .sidebar { position: sticky; top: 0; right: 0; bottom: auto; z-index: 5; flex-direction: row; width: 100%; height: 60px; padding: 0 15px; align-items: center; }
  .brand { flex: none; }
  .sidebar__nav { display: flex; flex: 1; gap: 0; margin: 0 0 0 19px; }
  .sidebar__nav a { min-height: 36px; }
  .sidebar__bottom { display: none; }
  .workspace { display: block; }
  .topbar { height: 59px; padding: 0 20px; }
  .breadcrumb, .server-status, .account span:not(.account__avatar) { display: none; }
  .account-area { margin-left: auto; }
  .content { width: calc(100% - 40px); padding: 38px 0 48px; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { transition-duration: .01ms !important; }
}
</style>
