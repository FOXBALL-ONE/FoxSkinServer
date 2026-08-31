<script setup lang="ts">
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const email = ref("");
const password = ref("");
const showPassword = ref(false);
const localError = ref("");

const redirectPath = computed(() => {
    const value = route.query.redirect;
    return typeof value === "string" && value.startsWith("/") && !value.startsWith("//") ? value : "/";
});

onMounted(async () => {
    await auth.hydrate();
    if (auth.isAuthenticated.value && auth.user.value) await router.replace(redirectPath.value);
});

async function submit() {
    localError.value = "";
    if (!email.value.trim() || !password.value) {
        localError.value = "请输入邮箱和密码";
        return;
    }
    const success = await auth.login(email.value.trim(), password.value);
    if (success) {
        await router.replace(redirectPath.value);
    } else {
        localError.value = auth.lastError.value || "登录失败，请检查账号信息";
    }
}
</script>

<template>
  <main class="login-page">
    <section class="login-visual" aria-label="FoxSkin">
      <div class="login-visual__image" />
      <div class="login-visual__shade" />
      <div class="login-visual__content">
        <NuxtLink class="brand" to="/" aria-label="返回 FoxSkin 首页"><span class="brand__mark">F</span><span>FoxSkin</span></NuxtLink>
        <div class="login-visual__copy">
          <p class="eyebrow"><span /> YOUR SKIN. YOUR STORY.</p>
          <h1>穿上属于你的<br><em>下一段冒险。</em></h1>
          <p>登录 FoxSkin，管理你的皮肤、披风与每一次游戏身份。</p>
        </div>
        <p class="login-visual__footer">开放、自由，为每一个热爱创造的人。</p>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-panel__inner">
        <NuxtLink class="mobile-brand" to="/" aria-label="返回首页"><span class="brand__mark">F</span><span>FoxSkin</span></NuxtLink>
        <p class="section-kicker">WELCOME BACK</p>
        <h2>登录你的账号。</h2>
        <p class="intro">继续管理你的衣柜，准备下一场冒险。</p>

        <form class="login-form" @submit.prevent="submit">
          <label for="email">邮箱地址</label>
          <input id="email" v-model="email" type="email" autocomplete="email" placeholder="you@example.com" required>

          <div class="field-heading"><label for="password">密码</label><button type="button" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></div>
          <input id="password" v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" required>

          <p v-if="localError" class="form-error" role="alert">{{ localError }}</p>
          <button class="submit-button" type="submit" :disabled="auth.loading">
            <span>{{ auth.loading ? '登录中...' : '登录 FoxSkin' }}</span><span aria-hidden="true">↗</span>
          </button>
        </form>

        <p class="register-hint">还没有账号？<button type="button" @click="navigateTo('/')">返回首页注册</button></p>
        <NuxtLink class="back-link" to="/">← 返回首页</NuxtLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');
:global(*) { box-sizing: border-box; }
:global(body) { margin: 0; background: #f4f3ef; color: #20251f; font-family: 'DM Sans', sans-serif; }
:global(button), :global(input) { font: inherit; }
:global(button) { cursor: pointer; }
.login-page { min-height: 100svh; display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(390px, .85fr); }
.login-visual { position: relative; min-height: 100svh; color: #fff; overflow: hidden; }
.login-visual__image, .login-visual__shade { position: absolute; inset: 0; }
.login-visual__image { background: url('/blessing-bg.webp') center / cover no-repeat; }
.login-visual__shade { background: linear-gradient(135deg, rgba(10, 17, 14, .84), rgba(10, 17, 14, .34)); }
.login-visual__content { position: relative; z-index: 1; min-height: 100svh; padding: 42px clamp(32px, 6vw, 92px); display: flex; flex-direction: column; }
.brand, .mobile-brand { display: inline-flex; align-items: center; gap: 10px; color: inherit; text-decoration: none; font: 700 21px 'Space Grotesk', sans-serif; letter-spacing: -.04em; }
.brand__mark { display: grid; width: 31px; height: 31px; place-items: center; background: #d9f35d; color: #20251f; font: italic 19px 'Space Grotesk', sans-serif; transform: rotate(-7deg); }
.login-visual__copy { margin: auto 0; max-width: 610px; }
.eyebrow, .section-kicker { color: #9eb834; font: 500 11px 'DM Mono', monospace; letter-spacing: .13em; }
.eyebrow { display: flex; align-items: center; gap: 10px; margin: 0 0 28px; color: #d9f35d; }
.eyebrow span { width: 28px; height: 1px; background: #d9f35d; }
h1, h2 { font-family: 'Space Grotesk', sans-serif; letter-spacing: -.06em; }
h1 { margin: 0; font-size: clamp(48px, 6vw, 82px); line-height: .99; }
h1 em { color: #d9f35d; font-style: normal; }
.login-visual__copy > p:last-child { max-width: 360px; margin: 28px 0 0; color: rgba(255,255,255,.72); font-size: 15px; line-height: 1.75; }
.login-visual__footer { margin: 0; color: rgba(255,255,255,.55); font: 10px 'DM Mono', monospace; letter-spacing: .05em; }
.login-panel { display: grid; place-items: center; padding: 48px 34px; }
.login-panel__inner { width: min(100%, 390px); }
.mobile-brand { display: none; color: #20251f; margin-bottom: 58px; }
.section-kicker { margin: 0 0 17px; }
h2 { margin: 0; font-size: clamp(36px, 4vw, 50px); line-height: 1.05; }
.intro { margin: 17px 0 39px; color: #6c756e; font-size: 14px; line-height: 1.6; }
.login-form label { display: block; margin-bottom: 8px; color: #5d675f; font: 10px 'DM Mono', monospace; letter-spacing: .08em; }
.login-form input { width: 100%; height: 48px; margin-bottom: 22px; padding: 0 13px; border: 1px solid #d9dcd4; border-radius: 0; outline: 0; background: #fff; color: #20251f; font-size: 14px; transition: border-color .2s, box-shadow .2s; }
.login-form input:focus { border-color: #9eb834; box-shadow: 0 0 0 3px rgba(158,184,52,.15); }
.field-heading { display: flex; align-items: center; justify-content: space-between; }
.field-heading button { margin-bottom: 8px; padding: 0; border: 0; background: none; color: #7a857b; font: 10px 'DM Mono', monospace; text-decoration: underline; }
.form-error { margin: -4px 0 16px; color: #a64e43; font-size: 12px; }
.submit-button { width: 100%; height: 50px; display: flex; align-items: center; justify-content: space-between; padding: 0 17px 0 20px; border: 1px solid #d9f35d; background: #d9f35d; color: #20251f; font-size: 13px; font-weight: 700; transition: background .2s, transform .2s; }
.submit-button:hover:not(:disabled) { background: #e5fb77; transform: translateY(-2px); }
.submit-button:disabled { cursor: wait; opacity: .65; }
.register-hint { margin: 28px 0 0; color: #7a857b; text-align: center; font-size: 12px; }
.register-hint button { padding: 0; border: 0; background: transparent; color: #20251f; font-weight: 600; text-decoration: underline; }
.back-link { display: block; margin-top: 54px; color: #8a948b; text-align: center; font: 10px 'DM Mono', monospace; letter-spacing: .05em; text-decoration: none; }
@media (max-width: 800px) { .login-page { display: block; }.login-visual { display: none; }.login-panel { min-height: 100svh; padding: 32px 24px; align-items: start; }.login-panel__inner { padding-top: 4vh; }.mobile-brand { display: inline-flex; }.login-panel .section-kicker { margin-top: 16px; } }
@media (min-width: 801px) and (max-height: 700px) { .login-visual__content { padding-top: 28px; padding-bottom: 28px; }.login-visual__copy { margin: 11vh 0 auto; }.login-panel { padding-top: 28px; padding-bottom: 28px; }.mobile-brand { margin-bottom: 32px; } }
</style>
