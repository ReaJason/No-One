import type { AuthFetch } from "@/api/api.server";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { CreateProfileRequest, Profile } from "@/types/profile";

import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";

import { mapPaginatedResponse } from "@/api/server-api-utils";

const baseUrl = "/profiles";

export interface ProfileSearchParams {
  name?: string | null;
  protocolType?: string | null;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export const loadProfileSearchParams = createLoader({
  name: parseAsString,
  protocolType: parseAsString,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export async function getProfiles(
  filters: ProfileSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<Profile>> {
  const response = await authFetch<ServerPaginatedResponse<Profile>>(baseUrl, {
    query: { ...filters, page: (filters.page ?? 1) - 1, pageSize: filters.perPage },
  });
  return mapPaginatedResponse(response);
}

export async function getProfileById(id: string, authFetch: AuthFetch): Promise<Profile> {
  return await authFetch<Profile>(`${baseUrl}/${id}`);
}

export async function createProfile(
  payload: CreateProfileRequest,
  authFetch: AuthFetch,
): Promise<Profile> {
  return await authFetch<Profile>(baseUrl, {
    method: "POST",
    body: payload,
  });
}

export async function updateProfile(
  id: string,
  payload: Partial<CreateProfileRequest>,
  authFetch: AuthFetch,
): Promise<Profile | null> {
  return await authFetch<Profile>(`${baseUrl}/${id}`, {
    method: "PUT",
    body: payload,
  });
}

export async function deleteProfile(id: string, authFetch: AuthFetch): Promise<boolean> {
  await authFetch(`${baseUrl}/${id}`, { method: "DELETE" });
  return true;
}

export async function getAllProfiles(authFetch: AuthFetch): Promise<Profile[]> {
  const response = await authFetch<ServerPaginatedResponse<Profile>>(baseUrl, {
    query: { page: 0, pageSize: 1000 },
  });
  return response.content;
}
