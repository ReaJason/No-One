import {createLoader, parseAsBoolean, parseAsInteger, parseAsString, parseAsStringEnum,} from "nuqs/server";
import type {User} from "@/types/admin";
import {apiClient, type PaginatedResponse} from "./api-client";

const baseUrl = "/users";

export interface UserSearchParams {
  username?: string | null;
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
  enabled: parseAsBoolean,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export async function getUsers(
  filters: UserSearchParams,
): Promise<PaginatedResponse<User>> {
  return await apiClient.getPaginated<User>(baseUrl, filters);
}

export async function getUserById(id: number): Promise<User | null> {
  const response = await apiClient.get<User>(`${baseUrl}/${id}`);
  return response.data;
}

export async function createUser(
  userData: Omit<User, "id" | "createdAt">,
): Promise<User> {
  const response = await apiClient.post<User>(baseUrl, userData);
  console.log(`[UserApiService] Create user: ${response.data.username}`);
  return response.data;
}

export async function updateUser(
  id: number,
  userData: Partial<User>,
): Promise<User | null> {
  const response = await apiClient.put<User>(`${baseUrl}/${id}`, userData);
  return response.data;
}

export async function deleteUser(id: number): Promise<boolean> {
  await apiClient.delete(`${baseUrl}/${id}`);
  return true;
}

export async function resetUserPassword(id: number): Promise<boolean> {
  await apiClient.post(`${baseUrl}/${id}/reset-password`);
  return true;
}

export async function getAllUsers(): Promise<User[]> {
  return (
    await apiClient.getPaginated<User>(baseUrl, { page: 1, perPage: 1000 })
  ).content;
}
