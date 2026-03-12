import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";
import type { AuthFetch } from "@/api.server";
import { mapPaginatedResponse } from "@/api/server-api-utils";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { Plugin } from "@/types/plugin";

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
