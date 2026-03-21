import type { AuthFetch } from "@/api/api.server";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { ShellOperationLog, ShellOperationLogQuery } from "@/types/shell-operation-log";

import {
  createLoader,
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
} from "nuqs/server";

import { mapPaginatedResponse } from "@/api/server-api-utils";

export interface ShellOperationSearchParams {
  shellId?: string | null;
  pluginId?: string | null;
  operation?: string | null;
  success?: boolean | null;
  page: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export const loadShellOperationSearchParams = createLoader({
  shellId: parseAsString,
  pluginId: parseAsString,
  operation: parseAsString,
  success: parseAsBoolean,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export async function getAllShellOperationLogs(
  filters: ShellOperationSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<ShellOperationLog>> {
  const response = await authFetch<ServerPaginatedResponse<ShellOperationLog>>(
    "/shell-operations",
    {
      query: {
        shellId: filters.shellId,
        page: (filters.page ?? 1) - 1,
        pageSize: filters.perPage,
        sortBy: filters.sortBy,
        sortOrder: filters.sortOrder,
        pluginId: filters.pluginId,
        operation: filters.operation,
        success: filters.success,
      },
    },
  );
  return mapPaginatedResponse(response);
}

export async function getShellOperationLogs(
  shellId: number,
  filters: ShellOperationLogQuery = {},
  authFetch: AuthFetch,
): Promise<PaginatedResponse<ShellOperationLog>> {
  const response = await authFetch<ServerPaginatedResponse<ShellOperationLog>>(
    `/shells/${shellId}/operations`,
    {
      query: {
        page: (filters.page ?? 1) - 1,
        pageSize: filters.pageSize,
        sortBy: filters.sortBy,
        sortOrder: filters.sortOrder,
        pluginId: filters.pluginId,
        operation: filters.operation,
        success: filters.success,
      },
    },
  );
  return mapPaginatedResponse(response);
}

export async function getLatestShellOperation(
  shellId: number,
  pluginId: string,
  authFetch: AuthFetch,
): Promise<ShellOperationLog | null> {
  return await authFetch<ShellOperationLog | null>(`/shells/${shellId}/operations/latest`, {
    query: {
      pluginId,
    },
  });
}
