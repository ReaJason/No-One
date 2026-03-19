import type { AuthFetch } from "@/api/api.server";
import type { LoginLog, LoginLogStatus, User, UserSession, UserStatus } from "@/types/admin";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";

import {
  createLoader,
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
} from "nuqs/server";

import { mapPaginatedResponse } from "@/api/server-api-utils";

const baseUrl = "/users";
const USER_STATUSES = ["ENABLED", "DISABLED", "LOCKED", "UNACTIVATED"] as const;
const LOGIN_LOG_STATUSES = [
  "SUCCESS",
  "INVALID_CREDENTIALS",
  "REQUIRE_2FA",
  "REQUIRE_SETUP",
  "REQUIRE_PASSWORD_CHANGE",
  "LOCKED",
  "DISABLED",
] as const satisfies readonly LoginLogStatus[];

export interface UserSearchParams {
  username?: string | null;
  status?: UserStatus | null;
  enabled?: boolean | null;
  roleId?: number | null;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export const loadUserSearchParams = createLoader({
  username: parseAsString,
  roles: parseAsInteger,
  roleId: parseAsInteger,
  status: parseAsStringEnum([...USER_STATUSES]),
  enabled: parseAsBoolean,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export interface UserLoginLogSearchParams {
  status?: LoginLogStatus | null;
  ipAddress?: string | null;
  sessionId?: string | null;
  loginTimeAfter?: string | null;
  loginTimeBefore?: string | null;
  page?: number;
  perPage?: number;
}

export const loadUserLoginLogSearchParams = createLoader({
  status: parseAsStringEnum([...LOGIN_LOG_STATUSES]),
  ipAddress: parseAsString,
  sessionId: parseAsString,
  loginTimeAfter: parseAsString,
  loginTimeBefore: parseAsString,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
});

export interface UserSessionSearchParams {
  revoked?: boolean | null;
  createdAfter?: string | null;
  createdBefore?: string | null;
  page?: number;
  perPage?: number;
}

export const loadUserSessionSearchParams = createLoader({
  revoked: parseAsBoolean,
  createdAfter: parseAsString,
  createdBefore: parseAsString,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
});

export async function getUsers(
  filters: UserSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<User>> {
  const response = await authFetch<ServerPaginatedResponse<User>>(baseUrl, {
    query: { ...filters, page: (filters.page ?? 1) - 1, pageSize: filters.perPage },
  });
  return mapPaginatedResponse(response);
}

export async function getUserById(id: number, authFetch: AuthFetch): Promise<User | null> {
  return await authFetch<User>(`${baseUrl}/${id}`);
}

export interface CreateUserRequest {
  username: string;
  password: string;
  email: string;
  status?: UserStatus;
  roleIds: number[];
}

export interface UpdateUserRequest {
  email?: string;
  status?: UserStatus;
}

export interface ResetUserPasswordRequest {
  newPassword: string;
  forceChangeOnNextLogin?: boolean;
}

export async function createUser(userData: CreateUserRequest, authFetch: AuthFetch): Promise<User> {
  return await authFetch<User>(baseUrl, {
    method: "POST",
    body: userData,
  });
}

export async function updateUser(
  id: number,
  userData: UpdateUserRequest,
  authFetch: AuthFetch,
): Promise<User | null> {
  return await authFetch<User>(`${baseUrl}/${id}`, {
    method: "PUT",
    body: userData,
  });
}

export async function deleteUser(id: number, authFetch: AuthFetch): Promise<boolean> {
  await authFetch(`${baseUrl}/${id}`, {
    method: "DELETE",
  });
  return true;
}

export async function syncUserRoles(
  id: number,
  roleIds: number[],
  authFetch: AuthFetch,
): Promise<User> {
  return await authFetch<User>(`${baseUrl}/${id}/roles`, {
    method: "PUT",
    body: roleIds,
  });
}

export async function resetUserPassword(
  id: number,
  payload: ResetUserPasswordRequest,
  authFetch: AuthFetch,
): Promise<boolean> {
  await authFetch(`${baseUrl}/${id}/reset-password`, {
    method: "PUT",
    body: payload,
  });
  return true;
}

function isAuthFetch(value: unknown): value is AuthFetch {
  return typeof value === "function";
}

function resolveAuthFetchAndFilters<TFilters>(
  arg2: TFilters | AuthFetch,
  arg3?: AuthFetch | TFilters,
): [TFilters, AuthFetch] {
  if (isAuthFetch(arg2)) {
    return [(arg3 ?? {}) as TFilters, arg2];
  }

  if (!isAuthFetch(arg3)) {
    throw new TypeError("authFetch must be provided");
  }

  return [arg2 ?? ({} as TFilters), arg3];
}

export async function getUserLoginLogs(
  id: number,
  filters: UserLoginLogSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<LoginLog>>;
export async function getUserLoginLogs(
  id: number,
  authFetch: AuthFetch,
  filters?: UserLoginLogSearchParams,
): Promise<PaginatedResponse<LoginLog>>;
export async function getUserLoginLogs(
  id: number,
  arg2: UserLoginLogSearchParams | AuthFetch,
  arg3?: AuthFetch | UserLoginLogSearchParams,
): Promise<PaginatedResponse<LoginLog>> {
  const [filters, authFetch] = resolveAuthFetchAndFilters<UserLoginLogSearchParams>(arg2, arg3);
  const query = {
    ...(filters.status !== undefined ? { status: filters.status } : {}),
    ...(filters.ipAddress !== undefined ? { ipAddress: filters.ipAddress } : {}),
    ...(filters.sessionId !== undefined ? { sessionId: filters.sessionId } : {}),
    ...(filters.loginTimeAfter !== undefined ? { loginTimeAfter: filters.loginTimeAfter } : {}),
    ...(filters.loginTimeBefore !== undefined ? { loginTimeBefore: filters.loginTimeBefore } : {}),
    page: (filters.page ?? 1) - 1,
    pageSize: filters.perPage,
  };
  const response = await authFetch<ServerPaginatedResponse<LoginLog>>(
    `${baseUrl}/${id}/login-logs`,
    {
      query,
    },
  );
  return mapPaginatedResponse(response);
}

export async function getUserSessions(
  id: number,
  filters: UserSessionSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<UserSession>>;
export async function getUserSessions(
  id: number,
  authFetch: AuthFetch,
  filters?: UserSessionSearchParams,
): Promise<PaginatedResponse<UserSession>>;
export async function getUserSessions(
  id: number,
  arg2: UserSessionSearchParams | AuthFetch,
  arg3?: AuthFetch | UserSessionSearchParams,
): Promise<PaginatedResponse<UserSession>> {
  const [filters, authFetch] = resolveAuthFetchAndFilters<UserSessionSearchParams>(arg2, arg3);
  const query = {
    ...(filters.revoked !== undefined ? { revoked: filters.revoked } : {}),
    ...(filters.createdAfter !== undefined ? { createdAfter: filters.createdAfter } : {}),
    ...(filters.createdBefore !== undefined ? { createdBefore: filters.createdBefore } : {}),
    page: (filters.page ?? 1) - 1,
    pageSize: filters.perPage,
  };
  const response = await authFetch<ServerPaginatedResponse<UserSession>>(
    `${baseUrl}/${id}/sessions`,
    {
      query,
    },
  );
  return mapPaginatedResponse(response);
}

export async function revokeAllUserSessions(id: number, authFetch: AuthFetch): Promise<void> {
  await authFetch(`${baseUrl}/${id}/sessions`, {
    method: "DELETE",
  });
}

export async function revokeUserSession(
  id: number,
  sessionId: string,
  authFetch: AuthFetch,
): Promise<void> {
  await authFetch(`${baseUrl}/${id}/sessions/${encodeURIComponent(sessionId)}`, {
    method: "DELETE",
  });
}

export async function getAllUsers(authFetch: AuthFetch): Promise<User[]> {
  const response = await authFetch<ServerPaginatedResponse<User>>(baseUrl, {
    query: { page: 0, pageSize: 1000 },
  });
  return response.content;
}
