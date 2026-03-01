import { apiClient, type PaginatedResponse } from "./api-client";
import type { ShellOperationLog, ShellOperationLogQuery } from "@/types/shell-operation-log";

export async function getShellOperationLogs(
  shellId: number,
  filters: ShellOperationLogQuery = {},
): Promise<PaginatedResponse<ShellOperationLog>> {
  return apiClient.getPaginated<ShellOperationLog>(
    `/shells/${shellId}/operations`,
    {
      page: filters.page,
      perPage: filters.pageSize,
      sortBy: filters.sortBy,
      sortOrder: filters.sortOrder,
      pluginId: filters.pluginId,
      operation: filters.operation,
      success: filters.success,
    },
  );
}

export async function getLatestShellOperation(
  shellId: number,
  pluginId: string,
): Promise<ShellOperationLog | null> {
  const response = await apiClient.get<ShellOperationLog>(
    `/shells/${shellId}/operations/latest`,
    { pluginId },
  );
  if (!response.data) {
    return null;
  }
  return response.data;
}
