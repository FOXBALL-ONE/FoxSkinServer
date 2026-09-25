<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

const { t, setLocale } = useI18n()
const auth = useAuthStore()
const preferences = usePreferencesStore()
const route = useRoute()
const router = useRouter()

type Feedback = { tone: 'ok' | 'error'; text: string }

const notice = ref<Feedback | null>(null)
const passwordNotice = ref<Feedback | null>(null)
const loadingProfile = ref(true)
const uploadingAvatar = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)

const form = reactive({ nickname: '' })
const passwordForm = reactive({ current: '', next: '', confirm: '' })

const LOCALE_OPTIONS = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en', label: 'English' },
]

const profile = computed(() => auth.profile)
const initial = computed(() => (profile.value?.nickname || profile.value?.username || '?').slice(0, 1).toUpperCase())

const roleLabel = computed(() => {
  const permission = profile.value?.permission ?? 0
  if (permission >= 2) return t('account.role.superAdmin')
  if (permission >= 1) return t('account.role.admin')
  if (permission < 0) return t('account.role.banned')
  return t('account.role.normal')
})

/** 后端按 ISO-8601 返回，这里只做展示裁剪，避免引入时区换算。 */
function formatDate(value?: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function applyProfile() {
  form.nickname = profile.value?.nickname ?? ''
}

async function saveProfile() {
  notice.value = null
  const nickname = form.nickname.trim()
  if (!nickname) {
    notice.value = { tone: 'error', text: t('account.nicknameRequired') }
    return
  }
  const ok = await auth.updateProfile({ nickname })
  notice.value = ok
      ? { tone: 'ok', text: t('account.saved') }
      : { tone: 'error', text: auth.lastError || t('account.saveFailed') }
}

function pickAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) void uploadAvatar(file)
}

async function uploadAvatar(file: File) {
  notice.value = null
  uploadingAvatar.value = true
  try {
    await auth.uploadAvatar(file)
    notice.value = { tone: 'ok', text: t('account.avatarUploaded') }
  } catch (error: unknown) {
    notice.value = { tone: 'error', text: describe(error, t('account.avatarUploadFailed')) }
  } finally {
    uploadingAvatar.value = false
  }
}

async function removeAvatar() {
  notice.value = null
  uploadingAvatar.value = true
  try {
    await auth.removeAvatar()
    notice.value = { tone: 'ok', text: t('account.avatarRemoved') }
  } catch (error: unknown) {
    notice.value = { tone: 'error', text: describe(error, t('account.avatarRemoveFailed')) }
  } finally {
    uploadingAvatar.value = false
  }
}

function describe(error: unknown, fallback: string) {
  const value = error as { data?: { message?: string }; statusMessage?: string; message?: string }
  return value.data?.message || value.statusMessage || value.message || fallback
}

async function submitPassword() {
  passwordNotice.value = null
  if (passwordForm.next.length < 8) {
    passwordNotice.value = { tone: 'error', text: t('account.passwordTooShort') }
    return
  }
  if (passwordForm.next !== passwordForm.confirm) {
    passwordNotice.value = { tone: 'error', text: t('account.passwordMismatch') }
    return
  }
  if (passwordForm.next === passwordForm.current) {
    passwordNotice.value = { tone: 'error', text: t('account.passwordSame') }
    return
  }
  const ok = await auth.changePassword(passwordForm.current, passwordForm.next)
  if (!ok) {
    passwordNotice.value = { tone: 'error', text: auth.lastError || t('account.passwordFailed') }
    return
  }
  // 后端改密后会吊销该用户所有 token，本地会话必须一并清掉并重新登录。
  passwordNotice.value = { tone: 'ok', text: t('account.passwordUpdated') }
  await auth.logout()
  await router.replace({ path: '/login', query: { reason: 'password_changed' } })
}

async function changeLocale(value: string) {
  preferences.setLocale(value)
  // vue-i18n 才是真正切换文案的一方，store 只负责持久化选择。
  await setLocale(value)
}

const bindNotice = ref<Feedback | null>(null)
const bindingBusy = ref(false)
const bindingsEnabled = computed(() => auth.oauthProviders.length > 0)

function connectionOf(providerId: string) {
  return auth.connections.find(item => item.provider === providerId) ?? null
}

/** 绑定：拿授权地址整页跳走，提供商回跳后经 /oauth/callback 带着结果回来。 */
async function bind(providerId: string) {
  bindNotice.value = null
  bindingBusy.value = true
  try {
    await auth.startOAuth(providerId, 'bind')
  } catch (error: unknown) {
    const value = error as { data?: { message?: string }; statusMessage?: string; message?: string }
    bindNotice.value = { tone: 'error', text: value.data?.message || value.statusMessage || value.message || t('account.bindFailed') }
  } finally {
    bindingBusy.value = false
  }
}

async function unbind(providerId: string) {
  bindNotice.value = null
  bindingBusy.value = true
  try {
    await auth.unbind(providerId)
    bindNotice.value = { tone: 'ok', text: t('account.unbindOk') }
  } catch (error: unknown) {
    const value = error as { data?: { message?: string }; statusMessage?: string; message?: string }
    bindNotice.value = { tone: 'error', text: value.data?.message || value.statusMessage || value.message || t('account.unbindFailed') }
  } finally {
    bindingBusy.value = false
  }
}

onMounted(async () => {
  await auth.hydrate()
  if (!auth.isAuthenticated || !auth.user) {
    await router.replace({ path: '/login', query: { redirect: '/account' } })
    return
  }
  await auth.loadProfile()
  applyProfile()
  loadingProfile.value = false
  // 绑定卡片：提供商列表与当前绑定并行取；绑定成功的回跳会带 ?bound=provider。
  await auth.loadOauthProviders()
  if (auth.oauthProviders.length) {
    await auth.loadConnections()
    const bound = route.query.bound
    if (typeof bound === 'string' && bound) {
      const name = auth.oauthProviders.find(item => item.id === bound)?.display_name ?? bound
      bindNotice.value = { tone: 'ok', text: t('account.bindOk', { provider: name }) }
      await router.replace({ path: '/account' })
    }
  }
})
</script>

<template>
  <AppShell active="account" :crumb="t('shell.account')">
    <section class="page-head">
      <div>
        <p class="eyebrow">{{ t('account.eyebrow') }}</p>
        <h1>{{ t('account.headlineLine1') }}<br><em>{{ t('account.headlineLine2') }}</em></h1>
      </div>
      <NuxtLink class="ghost-button" to="/dashboard">
        {{ t('common.backToOverview') }} <span aria-hidden="true">↗</span>
      </NuxtLink>
    </section>

    <p v-if="!loadingProfile && !profile" class="notice notice--error" role="alert">{{ t('account.loadFailed') }}</p>

    <section v-if="profile" class="account-grid">
      <article class="identity-card">
        <div class="identity-card__avatar">
          <img v-if="profile.avatar_url" :src="profile.avatar_url" alt="">
          <template v-else>{{ initial }}</template>
        </div>
        <div class="identity-card__avatar-actions">
          <button :disabled="uploadingAvatar" type="button" @click="avatarInput?.click()">
            {{ t('account.uploadAvatar') }}
          </button>
          <button v-if="profile.avatar_url" :disabled="uploadingAvatar" type="button" @click="removeAvatar">
            {{ t('account.removeAvatar') }}
          </button>
        </div>
        <input ref="avatarInput" accept="image/*" class="visually-hidden" type="file" @change="pickAvatar">
        <p class="identity-card__hint">{{ t('account.avatarHint') }}</p>

        <h2>{{ profile.nickname }}</h2>
        <p class="identity-card__handle">@{{ profile.username }}</p>
        <span :class="{ 'identity-card--admin': profile.permission >= 1 }" class="badge">{{ roleLabel }}</span>

        <dl class="identity-card__meta">
          <div><dt>{{ t('account.userId') }}</dt><dd>{{ profile.user_id }}</dd></div>
          <div><dt>{{ t('account.email') }}</dt><dd>{{ profile.email }}</dd></div>
          <div>
            <dt>{{ t('account.emailVerified') }}</dt>
            <dd>{{ profile.verified ? t('account.verified') : t('account.unverified') }}</dd>
          </div>
          <div><dt>{{ t('account.score') }}</dt><dd>{{ profile.score }}</dd></div>
          <div><dt>{{ t('account.registerAt') }}</dt><dd>{{ formatDate(profile.register_at) }}</dd></div>
          <div><dt>{{ t('account.lastSignAt') }}</dt><dd>{{ formatDate(profile.last_sign_at) }}</dd></div>
        </dl>
      </article>

      <article class="panel">
        <header class="panel__head">
          <p class="eyebrow">PROFILE</p>
          <h2>{{ t('account.profile') }}</h2>
        </header>

        <form class="form" @submit.prevent="saveProfile">
          <label for="account-nickname">{{ t('account.nickname') }}</label>
          <input id="account-nickname" v-model="form.nickname" maxlength="50"
                 :placeholder="t('account.nicknamePlaceholder')" type="text">

          <p v-if="notice" :class="`form-feedback form-feedback--${notice.tone}`" role="status">{{ notice.text }}</p>

          <button :disabled="auth.loading" class="primary-button" type="submit">
            <span>{{ auth.loading ? t('account.saving') : t('account.saveProfile') }}</span><span aria-hidden="true">↗</span>
          </button>
        </form>
      </article>

      <article class="panel">
        <header class="panel__head">
          <p class="eyebrow">APPEARANCE</p>
          <h2>{{ t('preferences.language') }}</h2>
        </header>

        <div class="form">
          <label for="account-locale">{{ t('preferences.language') }}</label>
          <select id="account-locale" :value="preferences.locale" @change="changeLocale(($event.target as HTMLSelectElement).value)">
            <option v-for="item in LOCALE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
          <p class="hint">{{ t('preferences.languageHint') }}</p>

          <label class="switch">
            <input :checked="preferences.isDark" type="checkbox" @change="preferences.toggleColorMode()">
            <span aria-hidden="true"/><b>{{ t('preferences.theme') }}</b>
          </label>
          <p class="hint">{{ t('preferences.themeHint') }}</p>
        </div>
      </article>

      <article class="panel">
        <header class="panel__head">
          <p class="eyebrow">{{ t('account.security') }}</p>
          <h2>{{ t('account.changePassword') }}</h2>
        </header>

        <form class="form" @submit.prevent="submitPassword">
          <label for="account-current">{{ t('account.currentPassword') }}</label>
          <input id="account-current" v-model="passwordForm.current" autocomplete="current-password"
                 :placeholder="t('account.currentPasswordPlaceholder')" type="password">

          <label for="account-next">{{ t('account.newPassword') }}</label>
          <input id="account-next" v-model="passwordForm.next" autocomplete="new-password"
                 :placeholder="t('account.newPasswordPlaceholder')" type="password">

          <label for="account-confirm">{{ t('account.confirmPassword') }}</label>
          <input id="account-confirm" v-model="passwordForm.confirm" autocomplete="new-password"
                 :placeholder="t('account.confirmPasswordPlaceholder')" type="password">

          <p v-if="passwordNotice" :class="`form-feedback form-feedback--${passwordNotice.tone}`" role="status">
            {{ passwordNotice.text }}
          </p>

          <button :disabled="auth.loading" class="primary-button" type="submit">
            <span>{{ auth.loading ? t('account.submitting') : t('account.updatePassword') }}</span><span aria-hidden="true">↗</span>
          </button>
        </form>

        <p class="hint">{{ t('account.identifierHint', { username: profile.username, email: profile.email }) }}</p>
      </article>

      <article class="panel">
        <header class="panel__head">
          <p class="eyebrow">CONNECTIONS</p>
          <h2>{{ t('account.bindings') }}</h2>
        </header>

        <p v-if="!bindingsEnabled" class="hint">{{ t('account.bindingsDisabled') }}</p>
        <template v-else>
          <ul class="bindings">
            <li v-for="item in auth.oauthProviders" :key="item.id" class="bindings__item">
              <div class="bindings__info">
                <b><span class="bindings__mark" aria-hidden="true">{{ item.display_name.slice(0, 1) }}</span>{{ item.display_name }}</b>
                <small v-if="connectionOf(item.id)">
                  {{ t('account.boundAt', { date: formatDate(connectionOf(item.id)?.created_at) }) }}
                </small>
                <small v-else>{{ t('account.notBound') }}</small>
              </div>
              <button v-if="connectionOf(item.id)" class="bindings__action bindings__action--danger" type="button"
                      :disabled="bindingBusy" @click="unbind(item.id)">
                {{ t('account.unbind') }}
              </button>
              <button v-else class="bindings__action" type="button" :disabled="bindingBusy" @click="bind(item.id)">
                {{ t('account.bind') }}
              </button>
            </li>
          </ul>
          <p v-if="bindNotice" :class="`form-feedback form-feedback--${bindNotice.tone}`" role="status">{{ bindNotice.text }}</p>
          <p class="hint">{{ t('account.bindingsHint') }}</p>
        </template>
      </article>
    </section>
  </AppShell>
</template>

<style scoped>
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.page-head h1 { margin: 0; font: 600 clamp(37px, 4.3vw, 58px)/1.01 'Space Grotesk', sans-serif; }
.page-head h1 em { color: var(--accent-strong); font-style: normal; }
.ghost-button { display: inline-flex; align-items: center; gap: 12px; min-height: 42px; padding: 0 16px; border: 1px solid var(--border-strong); color: var(--text-muted); text-decoration: none; font-size: 12px; font-weight: 600; transition: border-color .2s, color .2s; }
.ghost-button:hover { border-color: var(--accent-strong); color: var(--text-strong); }
.notice { margin: 24px 0 -14px; padding: 9px 12px; font-size: 12px; }
.notice--error { color: var(--danger); background: var(--danger-bg); }

.account-grid { display: grid; grid-template-columns: minmax(280px, .72fr) minmax(0, 1.28fr); gap: 19px; margin-top: 45px; align-items: start; }
.identity-card, .panel { border: 1px solid var(--border); background: var(--surface-panel); }
.identity-card { padding: 26px 24px; text-align: center; }
.identity-card__avatar { display: grid; width: 76px; height: 76px; margin: 0 auto; place-items: center; overflow: hidden; background: #d6a47d; color: #43352c; font: 700 32px 'Space Grotesk', sans-serif; }
.identity-card__avatar img { width: 100%; height: 100%; object-fit: cover; }
.identity-card__avatar-actions { display: flex; justify-content: center; gap: 8px; margin-top: 14px; }
.identity-card__avatar-actions button { padding: 6px 11px; border: 1px solid var(--border-strong); background: transparent; color: var(--text-muted); font-size: 11px; transition: border-color .2s, color .2s; }
.identity-card__avatar-actions button:hover:not(:disabled) { border-color: var(--accent-strong); color: var(--text-strong); }
.identity-card__avatar-actions button:disabled { cursor: wait; opacity: .6; }
.identity-card__hint { margin: 10px 0 0; color: var(--text-faint); font-size: 10px; line-height: 1.6; }
.identity-card h2 { margin: 18px 0 0; font: 600 22px 'Space Grotesk', sans-serif; word-break: break-all; }
.identity-card__handle { margin: 5px 0 0; color: var(--text-faint); font: 10px 'DM Mono', monospace; }
.badge { display: inline-block; margin-top: 14px; padding: 4px 9px; color: var(--ok); background: var(--ok-bg); font: 9px 'DM Mono', monospace; letter-spacing: .05em; }
.badge.identity-card--admin { color: var(--accent-tag-ink); background: var(--accent-tag-bg); }

.identity-card__meta { display: grid; gap: 13px; margin: 26px 0 0; padding-top: 22px; border-top: 1px solid var(--border-soft); text-align: left; }
.identity-card__meta div { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; }
.identity-card__meta dt { color: var(--text-faint); font: 9px 'DM Mono', monospace; }
.identity-card__meta dd { margin: 0; overflow: hidden; color: var(--text-body); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }

.panel { padding: 26px 24px; }
.panel__head h2 { margin: 0 0 22px; font: 600 22px 'Space Grotesk', sans-serif; }
.form label { display: block; margin-bottom: 8px; color: var(--text-muted); font: 10px 'DM Mono', monospace; letter-spacing: .08em; }
.form input[type="text"], .form input[type="password"], .form select { width: 100%; height: 44px; margin-bottom: 20px; padding: 0 13px; border: 1px solid var(--border); border-radius: 0; outline: 0; background: var(--surface-raised); color: var(--text); font-size: 13px; transition: border-color .2s, box-shadow .2s; }
.form input:focus, .form select:focus { border-color: var(--accent-strong); box-shadow: 0 0 0 3px rgba(158, 184, 52, .15); }
.switch { display: flex; align-items: center; gap: 10px; margin: 2px 0 6px; cursor: pointer; }
.switch input { width: 16px; height: 16px; margin: 0; accent-color: var(--accent-strong); }
.switch b { color: var(--text-body); font-size: 12px; font-weight: 600; }
.hint { margin: 14px 0 0; color: var(--text-faint); font-size: 11px; line-height: 1.7; }
.form-feedback { margin: 16px 0 0; padding: 8px 11px; font-size: 12px; }
.form-feedback--ok { color: var(--ok); background: var(--ok-bg); }
.form-feedback--error { color: var(--danger); background: var(--danger-bg); }
.primary-button { display: inline-flex; align-items: center; gap: 26px; min-height: 44px; margin-top: 20px; padding: 0 16px 0 19px; border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 12px; font-weight: 700; transition: transform .2s, background .2s; }
.primary-button:hover:not(:disabled) { background: var(--accent-hover); transform: translateY(-2px); }
.primary-button:disabled { cursor: wait; opacity: .65; }
.bindings { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.bindings__item { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 12px 13px; border: 1px solid var(--border); background: var(--surface-raised); }
.bindings__info b { display: flex; align-items: center; gap: 9px; font-size: 13px; }
.bindings__mark { display: grid; width: 22px; height: 22px; place-items: center; background: var(--accent); color: var(--accent-ink); font: 700 11px 'Space Grotesk', sans-serif; }
.bindings__info small { display: block; margin-top: 4px; color: var(--text-faint); font: 10px 'DM Mono', monospace; }
.bindings__action { min-height: 34px; padding: 0 13px; border: 1px solid var(--border-strong); background: transparent; color: var(--text-muted); font-size: 11px; font-weight: 600; transition: border-color .2s, color .2s; }
.bindings__action:hover:not(:disabled) { border-color: var(--accent-strong); color: var(--text-strong); }
.bindings__action--danger:hover:not(:disabled) { border-color: var(--danger); color: var(--danger); }
.bindings__action:disabled { cursor: wait; opacity: .6; }
.visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }

@media (max-width: 950px) {
  .account-grid { grid-template-columns: 1fr; }
}

@media (max-width: 620px) {
  .page-head { align-items: flex-start; flex-direction: column; }
  .page-head h1 { font-size: 34px; }
}
</style>
