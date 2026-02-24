import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";
import type { Plugin } from "@/types/plugin";
import { apiClient, type PaginatedResponse } from "./api-client";

const baseUrl = "/plugins";

export interface PluginSearchParams {
  name?: string | null;
  language?: string | null;
  type?: string | null;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export const loadPluginSearchParams = createLoader({
  name: parseAsString,
  language: parseAsString,
  type: parseAsString,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export async function getPlugins(filters: PluginSearchParams): Promise<PaginatedResponse<Plugin>> {
  return await apiClient.getPaginated<Plugin>(baseUrl, filters);
}

export async function createPlugin(data: Record<string, unknown>): Promise<Plugin> {
  const response = await apiClient.post<Plugin>(baseUrl, data);
  return response.data;
}
