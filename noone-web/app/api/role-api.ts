import {createLoader, parseAsInteger, parseAsString, parseAsStringEnum,} from "nuqs/server";
import type {Role} from "@/types/admin";
import {apiClient, type PaginatedResponse} from "./api-client";

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
): Promise<PaginatedResponse<Role>> {
  return apiClient.getPaginated<Role>(baseUrl, filters);
}

export async function getRoleById(id: number): Promise<Role | null> {
  return (await apiClient.get<Role>(`${baseUrl}/${id}`)).data;
}

export interface CreateRoleRequest {
  name: string;
  permissionIds: number[];
}

export async function createRole(roleData: CreateRoleRequest): Promise<Role> {
  return (await apiClient.post<Role>(baseUrl, roleData)).data;
}

export async function updateRole(
  id: number,
  roleData: Partial<Role> | { name?: string; permissionIds?: number[] },
): Promise<Role | null> {
  return (await apiClient.put<Role>(`${baseUrl}/${id}`, roleData)).data;
}

export async function deleteRole(id: number): Promise<boolean> {
  await apiClient.delete(`${baseUrl}/${id}`);
  return true;
}

export async function getAllRoles(): Promise<Role[]> {
  return (
    await apiClient.getPaginated<Role>(baseUrl, { page: 1, perPage: 100 })
  ).content;
}
