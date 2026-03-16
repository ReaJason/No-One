import type { AuthFetch } from "@/api/api.server";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { ShellOperationLog, ShellOperationLogQuery } from "@/types/shell-operation-log";

import { mapPaginatedResponse } from "@/api/server-api-utils";

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
