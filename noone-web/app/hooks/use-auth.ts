import { useAuth as useAuthContext, useUser } from "@/contexts/auth-context";
import type { AuthState } from "@/lib/authz";

export interface AuthStateWithLoading extends AuthState {
  isLoading: boolean;
}

export function useAuth(): AuthStateWithLoading {
  const authState = useAuthContext();

  return {
    ...authState,
    isLoading: false,
  };
}

export function useIsAuthenticated(): boolean {
  const { isAuthenticated } = useAuth();
  return isAuthenticated;
}

export function useCurrentUser() {
  return useUser();
}

export function useAuthLoading(): boolean {
  const { isLoading } = useAuth();
  return isLoading;
}
