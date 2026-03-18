import type { AuthFetch } from "@/api/api.server";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { CatalogResponse, Plugin } from "@/types/plugin";

import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";

import { mapPaginatedResponse } from "@/api/server-api-utils";

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

export async function getPlugins(
  filters: PluginSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<Plugin>> {
  const response = await authFetch<ServerPaginatedResponse<Plugin>>(baseUrl, {
    query: { ...filters, page: (filters.page ?? 1) - 1, pageSize: filters.perPage },
  });
  return mapPaginatedResponse(response);
}

export async function createPlugin(
  data: Record<string, unknown>,
  authFetch: AuthFetch,
): Promise<Plugin> {
  return await authFetch<Plugin>(baseUrl, {
    method: "POST",
    body: data,
  });
}

export async function getPlugin(id: string, authFetch: AuthFetch): Promise<Plugin> {
  return await authFetch<Plugin>(`${baseUrl}/${id}`);
}

export async function updatePlugin(
  id: string,
  data: Record<string, unknown>,
  authFetch: AuthFetch,
): Promise<Plugin> {
  return await authFetch<Plugin>(`${baseUrl}/${id}`, {
    method: "PUT",
    body: data,
  });
}

export async function deletePlugin(id: string, authFetch: AuthFetch): Promise<void> {
  await authFetch(`${baseUrl}/${id}`, { method: "DELETE" });
}

export async function uploadPlugin(file: File, authFetch: AuthFetch): Promise<Plugin> {
  const formData = new FormData();
  formData.append("file", file);
  return await authFetch<Plugin>(`${baseUrl}/upload`, {
    method: "POST",
    body: formData,
  });
}

export async function getRegistryCatalog(authFetch: AuthFetch): Promise<CatalogResponse> {
  return await authFetch<CatalogResponse>("/plugin-registry/catalog");
}

export async function installFromRegistry(
  pluginId: string,
  language: string,
  authFetch: AuthFetch,
): Promise<Plugin> {
  return await authFetch<Plugin>("/plugin-registry/install", {
    method: "POST",
    body: { pluginId, language },
  });
}
