import type { AuthFetch } from "@/api/api.server";
import type { LoginLog, User, UserSession, UserStatus } from "@/types/admin";
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

export async function getUserLoginLogs(id: number, authFetch: AuthFetch): Promise<LoginLog[]> {
  return await authFetch<LoginLog[]>(`${baseUrl}/${id}/login-logs`);
}

export async function getUserSessions(id: number, authFetch: AuthFetch): Promise<UserSession[]> {
  return await authFetch<UserSession[]>(`${baseUrl}/${id}/sessions`);
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
