import {
  createLoader,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
} from "nuqs/server";
import type { CreateProfileRequest, Profile } from "@/types/profile";
import type { PaginatedResponse } from "./api-client";
import { apiClient } from "./api-client";

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
): Promise<PaginatedResponse<Profile>> {
  return await apiClient.getPaginated<Profile>(baseUrl, filters);
}

export async function getProfileById(id: string): Promise<Profile | null> {
  return (await apiClient.get<Profile>(`${baseUrl}/${id}`)).data;
}

export async function createProfile(
  payload: CreateProfileRequest,
): Promise<Profile> {
  return (await apiClient.post<Profile>(baseUrl, payload)).data;
}

export async function updateProfile(
  id: string,
  payload: Partial<CreateProfileRequest>,
): Promise<Profile | null> {
  return (await apiClient.put<Profile>(`${baseUrl}/${id}`, payload)).data;
}

export async function deleteProfile(id: string): Promise<boolean> {
  await apiClient.delete(`${baseUrl}/${id}`);
  return true;
}

export async function getAllProfiles(): Promise<Profile[]> {
  return (
    await apiClient.getPaginated<Profile>(baseUrl, { page: 1, perPage: 1000 })
  ).content;
}
