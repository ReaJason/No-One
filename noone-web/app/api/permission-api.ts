import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";
import type { AuthFetch } from "@/api.server";
import { mapPaginatedResponse } from "@/api/server-api-utils";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { Permission } from "@/types/admin";

export interface PermissionSearchParams {
  name?: string | null;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

const baseUrl = "/permissions";

export const loadPermissionSearchParams = createLoader({
  name: parseAsString,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export async function getPermissions(
  filters: PermissionSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<Permission>> {
  const response = await authFetch<ServerPaginatedResponse<Permission>>(baseUrl, {
    query: { ...filters, page: (filters.page ?? 1) - 1, pageSize: filters.perPage },
  });
  return mapPaginatedResponse(response);
}

export async function getAllPermissions(authFetch: AuthFetch): Promise<Permission[]> {
  const response = await authFetch<ServerPaginatedResponse<Permission>>(baseUrl, {
    query: { page: 0, pageSize: 1000 },
  });
  return response.content;
}

export async function getPermissionById(
  id: number,
  authFetch: AuthFetch,
): Promise<Permission | null> {
  return await authFetch<Permission>(`${baseUrl}/${id}`);
}

export async function createPermission(
  permissionData: Omit<Permission, "id" | "createdAt" | "updatedAt">,
  authFetch: AuthFetch,
): Promise<Permission> {
  return await authFetch<Permission>(baseUrl, {
    method: "POST",
    body: permissionData,
  });
}

export async function updatePermission(
  id: number,
  permissionData: Partial<Permission>,
  authFetch: AuthFetch,
): Promise<Permission | null> {
  return await authFetch<Permission>(`${baseUrl}/${id}`, {
    method: "PUT",
    body: permissionData,
  });
}

export async function deletePermission(id: number, authFetch: AuthFetch): Promise<boolean> {
  await authFetch(`${baseUrl}/${id}`, { method: "DELETE" });
  return true;
}
