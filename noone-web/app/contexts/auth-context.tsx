import { useCallback } from "react";
import { useRouteLoaderData, useSubmit } from "react-router";

import { type AuthState, EMPTY_AUTH_STATE } from "@/lib/authz";

export interface AuthLayoutLoaderData {
  auth: AuthState;
}

export function useAuth(): AuthState {
  const loaderData = useRouteLoaderData("app-layout") as AuthLayoutLoaderData | undefined;
  return loaderData?.auth ?? EMPTY_AUTH_STATE;
}

export function useUser() {
  const { user } = useAuth();
  return user;
}

export function useIsAuthenticated(): boolean {
  const { isAuthenticated } = useAuth();
  return isAuthenticated;
}

export function useLogout() {
  const submit = useSubmit();

  return useCallback(async () => {
    await submit(null, {
      method: "post",
      action: "/auth/logout",
    });
  }, [submit]);
}
