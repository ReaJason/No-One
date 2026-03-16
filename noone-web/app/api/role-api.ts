import type { AuthFetch } from "@/api/api.server";
import type { Role } from "@/types/admin";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";

import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";

import { mapPaginatedResponse } from "@/api/server-api-utils";

const baseUrl = "/roles";

export interface RoleSearchParams {
  name?: string | null;
  page: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export const loadRoleSearchParams = createLoader({
  name: parseAsString,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export async function getRoles(
  filters: RoleSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<Role>> {
  const response = await authFetch<ServerPaginatedResponse<Role>>(baseUrl, {
    query: { ...filters, page: (filters.page ?? 1) - 1, pageSize: filters.perPage },
  });
  return mapPaginatedResponse(response);
}

export async function getRoleById(id: number, authFetch: AuthFetch): Promise<Role | null> {
  return await authFetch<Role>(`${baseUrl}/${id}`);
}

export interface CreateRoleRequest {
  name: string;
  permissionIds: number[];
}

export async function createRole(roleData: CreateRoleRequest, authFetch: AuthFetch): Promise<Role> {
  return await authFetch<Role>(baseUrl, {
    method: "POST",
    body: roleData,
  });
}

export async function updateRole(
  id: number,
  roleData: Partial<Role> | { name?: string; permissionIds?: number[] },
  authFetch: AuthFetch,
): Promise<Role | null> {
  return await authFetch<Role>(`${baseUrl}/${id}`, {
    method: "PUT",
    body: roleData,
  });
}

export async function deleteRole(id: number, authFetch: AuthFetch): Promise<boolean> {
  await authFetch(`${baseUrl}/${id}`, { method: "DELETE" });
  return true;
}

export async function getAllRoles(authFetch: AuthFetch): Promise<Role[]> {
  const response = await authFetch<ServerPaginatedResponse<Role>>(baseUrl, {
    query: { page: 0, pageSize: 100 },
  });
  return response.content;
}
