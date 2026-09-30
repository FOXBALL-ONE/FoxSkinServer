<script setup lang="ts">
import { computed, ref } from 'vue'
const { t } = useI18n()
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const username = ref("");
const password = ref("");
const showPassword = ref(false);
const localError = ref("");

const loginProviders = computed(() => auth.oauthProviders.filter((item) => item.id !== 'microsoft'))
const redirectPath = computed(() => {
    const value = route.query.redirect;
    return typeof value === "string" && value.startsWith("/") && !value.startsWith("//") ? value : "/dashboard";
});

/** 改密后所有 token 都已被吊销，这里给用户一句明确的解释。 */
const passwordChangedNotice = computed(() => route.query.reason === "password_changed");

onMounted(async () => {
    // 提供商列表是公开接口，无论是否已登录都先取到，第三方登录按钮才能渲染。
    await auth.loadOauthProviders();
    await auth.hydrate();
    // Pinia 会把 setup store 上的 ref 解包，这里不能再写 .value
    if (auth.isAuthenticated && auth.user) await router.replace(redirectPath.value);
});

async function submit() {
    localError.value = "";
    if (!username.value.trim() || !password.value) {
        localError.value = t("login.emptyFields");
        return;
    }
    const success = await auth.login(username.value.trim(), password.value);
    if (success) {
        await router.replace(redirectPath.value);
    } else {
        localError.value = auth.lastError || t("login.failed");
    }
}

/** 整页跳转到提供商授权页；回调由 /oauth/callback 接手并回到 redirectPath。 */
async function oauth(providerId: string) {
    localError.value = "";
    try {
        await auth.startOAuth(providerId, "login", redirectPath.value);
    } catch (error: unknown) {
        const value = error as { data?: { message?: string }; statusMessage?: string; message?: string };
        localError.value = value.data?.message || value.statusMessage || value.message || t("login.oauthFailed");
    }
}
</script>

<template>
  <main class="login-page">
    <section aria-label="FoxSkin" class="login-visual">
      <div class="login-visual__image" />
      <div class="login-visual__shade" />
      <div class="login-visual__content">
        <NuxtLink :aria-label="t('shell.backHome')" class="brand" to="/">
          <span class="brand__mark">F</span><span>{{ t('login.brand') }}</span>
        </NuxtLink>
        <div class="login-visual__copy">
          <p class="eyebrow"><span /> {{ t('login.eyebrow') }}</p>
          <h1>{{ t('login.headlineLine1') }}<br><em>{{ t('login.headlineLine2') }}</em></h1>
          <p>{{ t('login.lead') }}</p>
        </div>
        <p class="login-visual__footer">{{ t('login.footer') }}</p>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-panel__inner">
        <NuxtLink :aria-label="t('shell.backHome')" class="mobile-brand" to="/">
          <span class="brand__mark">F</span><span>{{ t('login.brand') }}</span>
        </NuxtLink>
        <p class="section-kicker">{{ t('login.kicker') }}</p>
        <h2>{{ t('login.title') }}</h2>
        <p class="intro">{{ t('login.intro') }}</p>

        <p v-if="passwordChangedNotice" class="form-notice" role="status">{{ t('login.passwordChanged') }}</p>

        <form class="login-form" @submit.prevent="submit">
          <label for="username">{{ t('login.usernameLabel') }}</label>
          <input id="username" v-model="username" autocomplete="username" :placeholder="t('login.usernamePlaceholder')"
                 required type="text">

          <div class="field-heading">
            <label for="password">{{ t('login.passwordLabel') }}</label>
            <button type="button" @click="showPassword = !showPassword">
              {{ showPassword ? t('login.hidePassword') : t('login.showPassword') }}
            </button>
          </div>
          <input id="password" v-model="password" :type="showPassword ? 'text' : 'password'"
                 autocomplete="current-password" :placeholder="t('login.passwordPlaceholder')" required>

          <p v-if="localError" class="form-error" role="alert">{{ localError }}</p>
          <button class="submit-button" type="submit" :disabled="auth.loading">
            <span>{{ auth.loading ? t('login.submitting') : t('login.submit') }}</span><span aria-hidden="true">↗</span>
          </button>
        </form>

        <div v-if="loginProviders.length" class="oauth">
          <p class="oauth__divider"><span>{{ t('login.oauthDivider') }}</span></p>
          <div class="oauth__buttons">
            <button v-for="item in loginProviders" :key="item.id" class="oauth__button" type="button"
                    @click="oauth(item.id)">
              <span class="oauth__mark" aria-hidden="true">{{ item.display_name.slice(0, 1) }}</span>
              <span>{{ t('login.oauthButton', { provider: item.display_name }) }}</span>
            </button>
          </div>
        </div>

        <p class="register-hint">{{ t('login.noAccount') }}
          <NuxtLink class="register-hint__link" to="/register">{{ t('login.goRegister') }}</NuxtLink>
        </p>
        <NuxtLink class="back-link" to="/">{{ t('login.backHome') }}</NuxtLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');
:global(*) { box-sizing: border-box; }
:global(body) { margin: 0; background: var(--surface-page); color: var(--text); font-family: 'DM Sans', sans-serif; }
:global(button), :global(input) { font: inherit; }
:global(button) { cursor: pointer; }
.login-page { min-height: 100svh; display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(390px, .85fr); }
.login-visual { position: relative; min-height: 100svh; color: #fff; overflow: hidden; }
.login-visual__image, .login-visual__shade { position: absolute; inset: 0; }
.login-visual__image { background: url('/blessing-bg.webp') center / cover no-repeat; }
.login-visual__shade { background: linear-gradient(135deg, rgba(10, 17, 14, .84), rgba(10, 17, 14, .34)); }
.login-visual__content { position: relative; z-index: 1; min-height: 100svh; padding: 42px clamp(32px, 6vw, 92px); display: flex; flex-direction: column; }
.brand, .mobile-brand { display: inline-flex; align-items: center; gap: 10px; color: inherit; text-decoration: none; font: 700 21px 'Space Grotesk', sans-serif; letter-spacing: -.04em; }
.brand__mark { display: grid; width: 31px; height: 31px; place-items: center; background: var(--accent); color: var(--accent-ink); font: italic 19px 'Space Grotesk', sans-serif; transform: rotate(-7deg); }
.login-visual__copy { margin: auto 0; max-width: 610px; }
.eyebrow, .section-kicker { color: var(--accent-strong); font: 500 11px 'DM Mono', monospace; letter-spacing: .13em; }
.eyebrow { display: flex; align-items: center; gap: 10px; margin: 0 0 28px; color: var(--accent); }
.eyebrow span { width: 28px; height: 1px; background: var(--accent); }
h1, h2 { font-family: 'Space Grotesk', sans-serif; letter-spacing: -.06em; }
h1 { margin: 0; font-size: clamp(48px, 6vw, 82px); line-height: .99; }
h1 em { color: var(--accent); font-style: normal; }
.login-visual__copy > p:last-child { max-width: 360px; margin: 28px 0 0; color: rgba(255,255,255,.72); font-size: 15px; line-height: 1.75; }
.login-visual__footer { margin: 0; color: rgba(255,255,255,.55); font: 10px 'DM Mono', monospace; letter-spacing: .05em; }
.login-panel { display: grid; place-items: center; padding: 48px 34px; background: var(--surface-page); }
.login-panel__inner { width: min(100%, 390px); }
.mobile-brand { display: none; color: var(--text); margin-bottom: 58px; }
.section-kicker { margin: 0 0 17px; }
h2 { margin: 0; font-size: clamp(36px, 4vw, 50px); line-height: 1.05; }
.intro { margin: 17px 0 39px; color: var(--text-muted); font-size: 14px; line-height: 1.6; }
.form-notice { margin: 0 0 22px; padding: 9px 12px; color: var(--ok); background: var(--ok-bg); font-size: 12px; }
.login-form label { display: block; margin-bottom: 8px; color: var(--text-muted); font: 10px 'DM Mono', monospace; letter-spacing: .08em; }
.login-form input { width: 100%; height: 48px; margin-bottom: 22px; padding: 0 13px; border: 1px solid var(--border); border-radius: 0; outline: 0; background: var(--surface-raised); color: var(--text); font-size: 14px; transition: border-color .2s, box-shadow .2s; }
.login-form input:focus { border-color: var(--accent-strong); box-shadow: 0 0 0 3px rgba(158,184,52,.15); }
.field-heading { display: flex; align-items: center; justify-content: space-between; }
.field-heading button { margin-bottom: 8px; padding: 0; border: 0; background: none; color: var(--text-faint); font: 10px 'DM Mono', monospace; text-decoration: underline; }
.form-error { margin: -4px 0 16px; color: var(--danger); font-size: 12px; }
.submit-button { width: 100%; height: 50px; display: flex; align-items: center; justify-content: space-between; padding: 0 17px 0 20px; border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 13px; font-weight: 700; transition: background .2s, transform .2s; }
.submit-button:hover:not(:disabled) { background: var(--accent-hover); transform: translateY(-2px); }
.submit-button:disabled { cursor: wait; opacity: .65; }
.register-hint { margin: 28px 0 0; color: var(--text-faint); text-align: center; font-size: 12px; }
.register-hint__link { color: var(--text); font-weight: 600; text-decoration: underline; }
.oauth { margin-top: 34px; }
.oauth__divider { display: flex; align-items: center; gap: 12px; margin: 0 0 16px; color: var(--text-faint); font: 10px 'DM Mono', monospace; letter-spacing: .08em; }
.oauth__divider::before, .oauth__divider::after { content: ""; flex: 1; height: 1px; background: var(--border); }
.oauth__buttons { display: grid; gap: 10px; }
.oauth__button { display: flex; align-items: center; gap: 11px; width: 100%; min-height: 44px; padding: 0 14px; border: 1px solid var(--border); background: var(--surface-raised); color: var(--text); font-size: 13px; transition: border-color .2s, background .2s; }
.oauth__button:hover { border-color: var(--accent-strong); }
.oauth__mark { display: grid; width: 22px; height: 22px; place-items: center; background: var(--accent); color: var(--accent-ink); font: 700 11px 'Space Grotesk', sans-serif; }
.back-link { display: block; margin-top: 54px; color: var(--text-faint); text-align: center; font: 10px 'DM Mono', monospace; letter-spacing: .05em; text-decoration: none; }
@media (max-width: 800px) { .login-page { display: block; }.login-visual { display: none; }.login-panel { min-height: 100svh; padding: 32px 24px; align-items: start; }.login-panel__inner { padding-top: 4vh; }.mobile-brand { display: inline-flex; }.login-panel .section-kicker { margin-top: 16px; } }
@media (min-width: 801px) and (max-height: 700px) { .login-visual__content { padding-top: 28px; padding-bottom: 28px; }.login-visual__copy { margin: 11vh 0 auto; }.login-panel { padding-top: 28px; padding-bottom: 28px; }.mobile-brand { margin-bottom: 32px; } }
</style>

<style scoped>
:global(html) { background: #080c0a; }
:global(body) { margin: 0; background: #080c0a; color: #e8ece5; font-family: 'DM Sans', sans-serif; }
:global(button), :global(input) { font: inherit; }
:global(button) { cursor: pointer; }

.login-page {
  min-height: 100svh;
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(390px, .82fr);
  background: #080c0a;
}
.login-visual { position: relative; min-height: 100svh; overflow: hidden; color: #e8ece5; border-right: 1px solid #283129; }
.login-visual__image, .login-visual__shade { position: absolute; inset: 0; }
.login-visual__image { background: url('/hero-gaming.jpg') 58% center / cover no-repeat; filter: saturate(.5) brightness(.58); }
.login-visual__shade { background: linear-gradient(90deg, rgba(8, 12, 10, .95) 0%, rgba(8, 12, 10, .72) 64%, rgba(8, 12, 10, .55) 100%), linear-gradient(0deg, rgba(8, 12, 10, .86), transparent 52%); }
.login-visual__content { position: relative; z-index: 1; min-height: 100svh; padding: 30px clamp(30px, 6vw, 88px); display: flex; flex-direction: column; }
.login-page .brand, .login-page .mobile-brand { display: inline-flex; align-items: center; gap: 10px; color: inherit; text-decoration: none; font: 700 17px 'Space Grotesk', sans-serif; letter-spacing: .05em; }
.login-page .brand__mark { display: grid; width: 30px; height: 30px; place-items: center; color: #080c0a; background: #a9d36a; font: 700 18px 'Space Grotesk', sans-serif; transform: none; }
.login-visual__copy { margin: auto 0; max-width: 600px; }
.login-page .eyebrow, .login-page .section-kicker { color: #a9d36a; font: 10px 'DM Mono', monospace; letter-spacing: .1em; }
.login-page .eyebrow { display: flex; align-items: center; gap: 10px; margin: 0 0 24px; }
.login-page .eyebrow span { width: 26px; height: 1px; background: #769d4c; }
.login-page h1, .login-page h2 { font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif; letter-spacing: -.03em; }
.login-page h1 { margin: 0; color: #e8ece5; font-size: clamp(46px, 6vw, 78px); font-weight: 600; line-height: 1.02; }
.login-page h1 em { color: #a9d36a; font-style: normal; }
.login-visual__copy > p:last-child { max-width: 390px; margin: 25px 0 0; color: #aab4a8; font-size: 14px; line-height: 1.8; }
.login-visual__footer { margin: 0; color: #748077; font: 9px 'DM Mono', monospace; letter-spacing: .06em; }
.login-panel { display: grid; place-items: center; padding: 44px 34px; background: #0d120f; }
.login-panel__inner { width: min(100%, 390px); }
.login-page .mobile-brand { display: none; color: #e8ece5; margin-bottom: 54px; }
.login-page .section-kicker { margin: 0 0 16px; }
.login-page h2 { margin: 0; color: #e8ece5; font-size: clamp(34px, 4vw, 48px); font-weight: 600; line-height: 1.06; }
.login-page .intro { margin: 17px 0 34px; color: #aab4a8; font-size: 13px; line-height: 1.7; }
.login-page .form-notice { margin: 0 0 20px; padding: 9px 12px; border-left: 2px solid #73b5e8; color: #73b5e8; background: rgba(115, 181, 232, .1); font-size: 12px; }
.login-page .login-form label { display: block; margin-bottom: 8px; color: #aab4a8; font: 10px 'DM Mono', monospace; letter-spacing: .08em; }
.login-page .login-form input { width: 100%; height: 48px; margin-bottom: 20px; padding: 0 13px; border: 1px solid #283129; border-radius: 0; outline: 0; background: #141a16; color: #e8ece5; font-size: 13px; }
.login-page .login-form input::placeholder { color: #748077; }
.login-page .login-form input:focus { border-color: #a9d36a; box-shadow: 0 0 0 2px rgba(169, 211, 106, .13); }
.login-page .field-heading { display: flex; align-items: center; justify-content: space-between; }
.login-page .field-heading button { margin-bottom: 8px; padding: 0; border: 0; background: none; color: #748077; font: 10px 'DM Mono', monospace; text-decoration: underline; }
.login-page .field-heading button:hover { color: #a9d36a; }
.login-page .form-error { margin: -2px 0 16px; color: #c55a4a; font-size: 12px; }
.login-page .submit-button { width: 100%; height: 50px; display: flex; align-items: center; justify-content: space-between; padding: 0 17px 0 20px; border: 1px solid #a9d36a; border-radius: 0; background: #a9d36a; color: #10180d; font-size: 12px; font-weight: 700; }
.login-page .submit-button:hover:not(:disabled) { background: #c2e983; }
.login-page .submit-button:disabled { cursor: wait; opacity: .62; }
.login-page .register-hint { margin: 24px 0 0; color: #748077; text-align: center; font-size: 12px; }
.login-page .register-hint__link { color: #a9d36a; font-weight: 600; text-decoration: underline; }
.login-page .oauth { margin-top: 30px; }
.login-page .oauth__divider { display: flex; align-items: center; gap: 12px; margin: 0 0 15px; color: #748077; font: 9px 'DM Mono', monospace; letter-spacing: .08em; }
.login-page .oauth__divider::before, .login-page .oauth__divider::after { content: ''; flex: 1; height: 1px; background: #283129; }
.login-page .oauth__buttons { display: grid; gap: 9px; }
.login-page .oauth__button { display: flex; align-items: center; gap: 11px; width: 100%; min-height: 44px; padding: 0 14px; border: 1px solid #283129; border-radius: 0; background: #141a16; color: #e8ece5; font-size: 12px; }
.login-page .oauth__button:hover { border-color: #769d4c; background: #18231c; }
.login-page .oauth__mark { display: grid; width: 22px; height: 22px; place-items: center; background: #769d4c; color: #10180d; font: 700 11px 'Space Grotesk', sans-serif; }
.login-page .back-link { display: block; margin-top: 44px; color: #748077; text-align: center; font: 9px 'DM Mono', monospace; letter-spacing: .06em; text-decoration: none; }
.login-page .back-link:hover { color: #a9d36a; }

@media (max-width: 800px) {
  .login-page { display: block; }
  .login-visual { display: none; }
  .login-panel { min-height: 100svh; padding: 32px 24px; align-items: start; background: #0d120f; }
  .login-panel__inner { padding-top: 4vh; }
  .login-page .mobile-brand { display: inline-flex; }
  .login-panel .section-kicker { margin-top: 16px; }
}
@media (prefers-reduced-motion: reduce) {
  .login-page *, .login-page *::before, .login-page *::after { transition-duration: .01ms !important; }
}
</style>
