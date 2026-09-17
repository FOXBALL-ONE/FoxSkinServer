<script setup lang="ts">
const { t } = useI18n()
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const username = ref("");
const password = ref("");
const showPassword = ref(false);
const localError = ref("");

const redirectPath = computed(() => {
    const value = route.query.redirect;
    return typeof value === "string" && value.startsWith("/") && !value.startsWith("//") ? value : "/dashboard";
});

/** 改密后所有 token 都已被吊销，这里给用户一句明确的解释。 */
const passwordChangedNotice = computed(() => route.query.reason === "password_changed");

onMounted(async () => {
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

        <p class="register-hint">{{ t('login.noAccount') }}
          <button type="button" @click="navigateTo('/')">{{ t('login.backHome').replace('← ', '') }}</button>
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
.register-hint button { padding: 0; border: 0; background: transparent; color: var(--text); font-weight: 600; text-decoration: underline; }
.back-link { display: block; margin-top: 54px; color: var(--text-faint); text-align: center; font: 10px 'DM Mono', monospace; letter-spacing: .05em; text-decoration: none; }
@media (max-width: 800px) { .login-page { display: block; }.login-visual { display: none; }.login-panel { min-height: 100svh; padding: 32px 24px; align-items: start; }.login-panel__inner { padding-top: 4vh; }.mobile-brand { display: inline-flex; }.login-panel .section-kicker { margin-top: 16px; } }
@media (min-width: 801px) and (max-height: 700px) { .login-visual__content { padding-top: 28px; padding-bottom: 28px; }.login-visual__copy { margin: 11vh 0 auto; }.login-panel { padding-top: 28px; padding-bottom: 28px; }.mobile-brand { margin-bottom: 32px; } }
</style>
