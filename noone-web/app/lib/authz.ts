import type { User } from "@/types/admin";

export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  authorities: string[];
  isAdmin: boolean;
}

export const EMPTY_AUTH_STATE: AuthState = {
  user: null,
  isAuthenticated: false,
  authorities: [],
  isAdmin: false,
};

const ADMIN_AUTHORITIES = ["ROLE_ADMIN"] as const;

export function extractAuthoritiesFromUser(user: User | null | undefined): string[] {
  if (!user) {
    return [];
  }

  return user.authorities ?? [];
}

export function hasAuthority(
  authorities: readonly string[] | null | undefined,
  code: string,
): boolean {
  return Boolean(authorities?.includes(code));
}

export function hasAnyAuthority(
  authorities: readonly string[] | null | undefined,
  codes: readonly string[],
): boolean {
  return codes.some((code) => hasAuthority(authorities, code));
}

export function hasAllAuthorities(
  authorities: readonly string[] | null | undefined,
  codes: readonly string[],
): boolean {
  return codes.every((code) => hasAuthority(authorities, code));
}

export function isAdmin(authorities: readonly string[] | null | undefined): boolean {
  return hasAnyAuthority(authorities, ADMIN_AUTHORITIES);
}

export function buildAuthState(user: User | null): AuthState {
  const authorities = extractAuthoritiesFromUser(user);
  return {
    user,
    isAuthenticated: Boolean(user),
    authorities,
    isAdmin: isAdmin(authorities),
  };
}
