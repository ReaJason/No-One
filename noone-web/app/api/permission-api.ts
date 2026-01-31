import {createLoader, parseAsInteger, parseAsString, parseAsStringEnum,} from "nuqs/server";
import type {Permission} from "@/types/admin";
import {apiClient, type PaginatedResponse} from "./api-client";

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
): Promise<PaginatedResponse<Permission>> {
  return await apiClient.getPaginated<Permission>(baseUrl, filters);
}

export async function getAllPermissions(): Promise<Permission[]> {
  return (
    await apiClient.getPaginated<Permission>(baseUrl, {
      page: 1,
      pageSize: 1000,
    })
  ).content;
}

export async function getPermissionById(
  id: number,
): Promise<Permission | null> {
  return (await apiClient.get<Permission>(`${baseUrl}/${id}`)).data;
}

export async function createPermission(
  permissionData: Omit<Permission, "id" | "createdAt" | "updatedAt">,
): Promise<Permission> {
  return (await apiClient.post<Permission>(baseUrl, permissionData)).data;
}

export async function updatePermission(
  id: number,
  permissionData: Partial<Permission>,
): Promise<Permission | null> {
  return (await apiClient.put<Permission>(`${baseUrl}/${id}`, permissionData))
    .data;
}

export async function deletePermission(id: number): Promise<boolean> {
  await apiClient.delete(`${baseUrl}/${id}`);
  return true;
}
