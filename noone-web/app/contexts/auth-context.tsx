import {useLoaderData} from "react-router";
import type {AuthState} from "@/lib/auth-context";
import type {User} from "@/types/admin";

// 权限验证Hook - 基于loader数据
export function useAuth(): AuthState {
  const loaderData = useLoaderData() as { auth: AuthState } | undefined;
  return loaderData?.auth ?? { user: null, isAuthenticated: false };
}

// 便捷的Hook用于获取用户信息
export function useUser(): User | null {
  const { user } = useAuth();
  return user;
}

// 便捷的Hook用于检查认证状态
export function useIsAuthenticated(): boolean {
  const { isAuthenticated } = useAuth();
  return isAuthenticated;
}

// 登出功能
export function useLogout() {
  const logout = async () => {
    try {
      // 在SSR模式下，登出需要重定向到登出页面
      window.location.href = "/auth/logout";
    } catch (error) {
      console.error("Logout failed:", error);
    }
  };

  return logout;
}
