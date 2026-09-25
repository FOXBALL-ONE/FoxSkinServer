<script setup lang="ts">
const { t } = useI18n()
const router = useRouter();
const auth = useAuthStore();

const error = ref("");
const working = ref(true);

/** redirect 参数只接受站内相对路径，防开放重定向。 */
function sanitizePath(value: string | null): string {
    return value && value.startsWith("/") && !value.startsWith("//") ? value : "/dashboard";
}

onMounted(async () => {
    // 后端 302 回来时令牌放在 URL fragment 里（不会进服务端日志），读取后立刻清掉，刷新页面不会重放。
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ""));
    history.replaceState(null, "", window.location.pathname);

    const failure = hash.get("error");
    if (failure) {
        error.value = failure;
        working.value = false;
        return;
    }

    if (hash.get("bind") === "ok") {
        await router.replace({ path: "/account", query: { bound: hash.get("provider") ?? "" } });
        return;
    }

    const token = hash.get("token");
    if (token) {
        try {
            await auth.applyOAuthToken(token);
            await router.replace(sanitizePath(hash.get("redirect")));
            return;
        } catch {
            error.value = t("oauth.failed");
        }
    } else {
        error.value = t("oauth.missingPayload");
    }
    working.value = false;
});
</script>

<template>
  <main class="callback-page">
    <NuxtLink aria-label="FoxSkin" class="brand" to="/"><span class="brand__mark">F</span><span>FoxSkin</span></NuxtLink>

    <section class="card" role="status">
      <template v-if="working">
        <span class="spinner" aria-hidden="true"/>
        <h1>{{ t('oauth.working') }}</h1>
        <p>{{ t('oauth.workingHint') }}</p>
      </template>
      <template v-else>
        <h1>{{ t('oauth.errorTitle') }}</h1>
        <p class="error">{{ error }}</p>
        <div class="actions">
          <NuxtLink class="button" to="/login">{{ t('oauth.backLogin') }}</NuxtLink>
          <NuxtLink class="button button--ghost" to="/">{{ t('oauth.backHome') }}</NuxtLink>
        </div>
      </template>
    </section>
  </main>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');
:global(*) { box-sizing: border-box; }
:global(body) { margin: 0; background: var(--surface-page); color: var(--text); font-family: 'DM Sans', sans-serif; }
.callback-page { min-height: 100svh; display: grid; place-content: center; gap: 34px; padding: 32px 24px; }
.brand { display: inline-flex; align-items: center; gap: 10px; color: var(--text); text-decoration: none; font: 700 21px 'Space Grotesk', sans-serif; letter-spacing: -.04em; justify-content: center; }
.brand__mark { display: grid; width: 31px; height: 31px; place-items: center; background: var(--accent); color: var(--accent-ink); font: italic 19px 'Space Grotesk', sans-serif; transform: rotate(-7deg); }
.card { width: min(100%, 390px); padding: 42px 34px; border: 1px solid var(--border); background: var(--surface-panel); text-align: center; }
.card h1 { margin: 0; font: 600 24px 'Space Grotesk', sans-serif; letter-spacing: -.04em; }
.card p { margin: 14px 0 0; color: var(--text-muted); font-size: 13px; line-height: 1.7; }
.card .error { color: var(--danger); }
.spinner { display: block; width: 26px; height: 26px; margin: 0 auto 18px; border: 2px solid var(--border-strong); border-top-color: var(--accent); border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.actions { display: flex; gap: 10px; justify-content: center; margin-top: 26px; }
.button { display: inline-flex; align-items: center; min-height: 42px; padding: 0 16px; border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 12px; font-weight: 700; text-decoration: none; transition: background .2s; }
.button:hover { background: var(--accent-hover); }
.button--ghost { border-color: var(--border-strong); background: transparent; color: var(--text-muted); }
.button--ghost:hover { background: transparent; color: var(--text-strong); }
@media (prefers-reduced-motion: reduce) { .spinner { animation-duration: .01ms; } }
</style>
