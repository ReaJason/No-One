// SSR 兼容的认证状态管理 hook
import { useEffect, useState } from "react";
import { useLoaderData } from "react-router";
import { authUtils } from "@/lib/auth";
import type { User } from "@/types/admin";

// 认证状态类型
export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

// 认证 hook
export function useAuth(): AuthState {
  const [authState, setAuthState] = useState<AuthState>({
    user: null,
    isAuthenticated: false,
    isLoading: true,
  });

  // 从 loader 数据获取初始认证状态
  const loaderData = useLoaderData() as { user?: User } | undefined;

  useEffect(() => {
    // 如果 loader 提供了用户数据，使用它
    if (loaderData?.user) {
      setAuthState({
        user: loaderData.user,
        isAuthenticated: true,
        isLoading: false,
      });
      return;
    }

    // 否则从 session 获取认证状态
    const loadAuthState = async () => {
      try {
        const user = await authUtils.getUser();
        const authenticated = await authUtils.isAuthenticated();

        setAuthState({
          user,
          isAuthenticated: authenticated,
          isLoading: false,
        });
      } catch (error) {
        console.error("Failed to load auth state:", error);
        setAuthState({
          user: null,
          isAuthenticated: false,
          isLoading: false,
        });
      }
    };

    loadAuthState();
  }, [loaderData]);

  return authState;
}

// 简化的认证检查 hook
export function useIsAuthenticated(): boolean {
  const { isAuthenticated } = useAuth();
  return isAuthenticated;
}

// 获取当前用户的 hook
export function useCurrentUser(): User | null {
  const { user } = useAuth();
  return user;
}

// 认证加载状态 hook
export function useAuthLoading(): boolean {
  const { isLoading } = useAuth();
  return isLoading;
}
