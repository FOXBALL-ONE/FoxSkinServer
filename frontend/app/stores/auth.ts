import {computed, ref} from "vue";

interface AuthTokens {
    access_token: string;
    refresh_token?: string;
    expires_in: number;
}

export interface AuthUser {
    user_id: number;
    email: string;
    nickname: string;
    permission: number;
    verified: boolean;
}

function errorMessage(error: unknown): string {
    const value = error as {
        statusMessage?: string;
        data?: { message?: string; statusMessage?: string };
        message?: string;
    };
    return value.data?.message || value.data?.statusMessage || value.statusMessage || value.message || "登录失败，请稍后再试";
}

export const useAuthStore = defineStore("auth", () => {
    const accessToken = useCookie<string | null>("foxskin_access_token", {
        maxAge: 60 * 60 * 24 * 7,
        sameSite: "lax",
        path: "/",
    });
    const user = ref<AuthUser | null>(null);
    const loading = ref(false);
    const hydrated = ref(false);
    const lastError = ref<string | null>(null);
    const http = useHttp();

    const isAuthenticated = computed(() => Boolean(accessToken.value));
    const isAdmin = computed(() => (user.value?.permission ?? 0) >= 1);

    async function login(email: string, password: string) {
        loading.value = true;
        lastError.value = null;
        try {
            const tokens = await http.post<AuthTokens, { email: string; password: string }>("/auth/login", {email, password});
            accessToken.value = tokens.access_token;
            await loadCurrentUser();
            return true;
        } catch (error: unknown) {
            lastError.value = errorMessage(error);
            return false;
        } finally {
            loading.value = false;
        }
    }

    async function loadCurrentUser() {
        if (!accessToken.value) {
            user.value = null;
            return null;
        }
        user.value = await http.get<AuthUser>("/auth/me");
        return user.value;
    }

    async function hydrate() {
        if (hydrated.value) return user.value;
        hydrated.value = true;
        if (!accessToken.value) return null;
        try {
            return await loadCurrentUser();
        } catch {
            user.value = null;
            return null;
        }
    }

    async function logout() {
        try {
            await http.post("/auth/logout");
        } catch {
            // The local session must still be cleared if the server is unavailable.
        } finally {
            accessToken.value = null;
            user.value = null;
            hydrated.value = true;
        }
    }

    function clearError() {
        lastError.value = null;
    }

    return {
        accessToken,
        user,
        loading,
        hydrated,
        lastError,
        isAuthenticated,
        isAdmin,
        login,
        loadCurrentUser,
        hydrate,
        logout,
        clearError,
    };
});
