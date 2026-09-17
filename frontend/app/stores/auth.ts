import {computed, ref} from "vue";

interface AuthTokens {
    access_token: string;
    refresh_token?: string;
    expires_in: number;
}

export interface AuthUser {
    user_id: number;
    email: string;
    username: string;
    nickname: string;
    permission: number;
    verified: boolean;
}

/** 个人中心展示用的完整资料，来自 GET /api/users/me。 */
export interface AccountProfile {
    user_id: number;
    email: string;
    username: string;
    nickname: string;
    locale: string | null;
    /** 头像引用的文件主键，为空表示用首字母头像。 */
    avatar_file_id: string | null;
    /** 公开头像端点 /avatar/{uid}，为 null 时前端回退到首字母。 */
    avatar_url: string | null;
    score: number;
    permission: number;
    verified: boolean;
    is_dark_mode: boolean;
    ip: string;
    last_sign_at: string;
    register_at: string;
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
    const profile = ref<AccountProfile | null>(null);
    const loading = ref(false);
    const hydrated = ref(false);
    const lastError = ref<string | null>(null);
    const http = useHttp();

    const isAuthenticated = computed(() => Boolean(accessToken.value));
    const isAdmin = computed(() => (user.value?.permission ?? 0) >= 1);

    async function login(username: string, password: string) {
        loading.value = true;
        lastError.value = null;
        try {
            const tokens = await http.post<AuthTokens, { username: string; password: string }>("/auth/login", {username, password});
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
            profile.value = null;
            hydrated.value = true;
        }
    }

    /** 读取当前用户的完整资料。 */
    async function loadProfile() {
        if (!accessToken.value) {
            profile.value = null;
            return null;
        }
        try {
            profile.value = await http.get<AccountProfile>("/users/me");
            return profile.value;
        } catch (error: unknown) {
            lastError.value = errorMessage(error);
            return null;
        }
    }

    /** 更新资料；成功后同步 user 上的昵称，让顶栏展示立刻跟上。 */
    async function updateProfile(input: { nickname?: string; locale?: string; is_dark_mode?: boolean }) {
        loading.value = true;
        lastError.value = null;
        try {
            const params: Record<string, string> = {};
            if (input.nickname !== undefined) params.nickname = input.nickname;
            if (input.locale !== undefined) params.locale = input.locale;
            if (input.is_dark_mode !== undefined) params.is_dark_mode = String(input.is_dark_mode);
            profile.value = await http.patch<AccountProfile>("/users/me", params);
            if (user.value) user.value = {...user.value, nickname: profile.value.nickname};
            return true;
        } catch (error: unknown) {
            lastError.value = errorMessage(error);
            return false;
        } finally {
            loading.value = false;
        }
    }

    /** 修改密码。成功后后端会吊销该用户全部 token，调用方需要清理本地会话并重新登录。 */
    async function changePassword(currentPassword: string, newPassword: string) {
        loading.value = true;
        lastError.value = null;
        try {
            await http.post("/users/me/password", {
                current_password: currentPassword,
                new_password: newPassword,
            });
            return true;
        } catch (error: unknown) {
            lastError.value = errorMessage(error);
            return false;
        } finally {
            loading.value = false;
        }
    }

    /** 上传并替换头像；后端沿用文件那套存储，头像字段存的是 file_metadata 主键。 */
    async function uploadAvatar(file: File) {
        const body = new FormData();
        body.append("file", file);
        const result = await http.post<{ avatar_file_id: string; avatar_url: string }>("/users/me/avatar", body);
        if (profile.value) {
            profile.value = {...profile.value, avatar_file_id: result.avatar_file_id, avatar_url: result.avatar_url};
        }
        return result;
    }

    /** 移除头像，回到首字母头像。 */
    async function removeAvatar() {
        await http.delete("/users/me/avatar");
        if (profile.value) {
            profile.value = {...profile.value, avatar_file_id: null, avatar_url: null};
        }
    }

    function clearError() {
        lastError.value = null;
    }

    return {
        accessToken,
        user,
        profile,
        loading,
        hydrated,
        lastError,
        isAuthenticated,
        isAdmin,
        login,
        loadCurrentUser,
        loadProfile,
        updateProfile,
        changePassword,
        uploadAvatar,
        removeAvatar,
        hydrate,
        logout,
        clearError,
    };
});
