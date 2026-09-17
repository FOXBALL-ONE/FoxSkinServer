import { computed } from "vue";

export type ColorMode = "light" | "dark";

/** 与 nuxt.config 里 i18n.detectBrowserLanguage.cookieKey 保持一致，共用一个 cookie。 */
const LOCALE_COOKIE = "foxskin_locale";
const COLOR_MODE_COOKIE = "foxskin_color_mode";

const SUPPORTED_LOCALES = ["zh-CN", "en"];
export const DEFAULT_LOCALE = "zh-CN";

const COOKIE_OPTIONS = {
    maxAge: 60 * 60 * 24 * 365,
    sameSite: "lax" as const,
    path: "/",
};

/**
 * 首次访问的语言：服务端读 Accept-Language 请求头，客户端退回 navigator.language。
 *
 * 正常情况下 nuxt-i18n 的 detectBrowserLanguage 已经把结果写进同一个 cookie，这里是兜底，
 * 保证 store 独自也能给出正确结果（例如 cookie 被禁用或检测被关掉时）。
 */
function detectLocaleFromRequest(): string {
    if (import.meta.server) {
        const header = useRequestHeaders(["accept-language"])["accept-language"];
        return matchLocale(header ?? "");
    }
    return matchLocale(import.meta.client ? navigator.language : "");
}

function matchLocale(raw: string): string {
    const first = raw.split(",")[0]?.trim().toLowerCase() ?? "";
    if (!first) return DEFAULT_LOCALE;
    if (first.startsWith("zh")) return "zh-CN";
    return SUPPORTED_LOCALES.find((code) => code.toLowerCase() === first)
        ?? SUPPORTED_LOCALES.find((code) => first.startsWith(code.split("-")[0]!))
        ?? "en";
}

/** 界面语言与主题偏好的持久化状态；两者都只存在于前端，不写回账号。 */
export const usePreferencesStore = defineStore("preferences", () => {
    const localeCookie = useCookie<string | null>(LOCALE_COOKIE, COOKIE_OPTIONS);
    const colorModeCookie = useCookie<ColorMode | null>(COLOR_MODE_COOKIE, COOKIE_OPTIONS);

    // 首次访问没有 cookie 时按请求头决定语言，并立刻写回，后续访问不再检测。
    if (!localeCookie.value) localeCookie.value = detectLocaleFromRequest();

    const locale = computed({
        get: () => localeCookie.value || DEFAULT_LOCALE,
        set: (value: string) => {
            localeCookie.value = SUPPORTED_LOCALES.includes(value) ? value : DEFAULT_LOCALE;
        },
    });

    const colorMode = computed<ColorMode>({
        get: () => (colorModeCookie.value === "dark" ? "dark" : "light"),
        set: (value: ColorMode) => {
            colorModeCookie.value = value === "dark" ? "dark" : "light";
        },
    });

    const isDark = computed(() => colorMode.value === "dark");

    function setLocale(value: string) {
        locale.value = value;
    }

    function toggleColorMode() {
        colorMode.value = isDark.value ? "light" : "dark";
    }

    return {
        locale,
        colorMode,
        isDark,
        availableLocales: SUPPORTED_LOCALES,
        setLocale,
        toggleColorMode,
    };
});
