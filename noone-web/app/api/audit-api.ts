import type { AuthFetch } from "@/api/api.server";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { AuditLog } from "@/types/audit";

import {
  createLoader,
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
} from "nuqs/server";

import { mapPaginatedResponse } from "@/api/server-api-utils";

const baseUrl = "/audit-logs";

export interface AuditSearchParams {
  module?: string | null;
  action?: string | null;
  username?: string | null;
  success?: boolean | null;
  page: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export const loadAuditSearchParams = createLoader({
  module: parseAsString,
  action: parseAsString,
  username: parseAsString,
  success: parseAsBoolean,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export async function getAuditLogs(
  filters: AuditSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<AuditLog>> {
  const response = await authFetch<ServerPaginatedResponse<AuditLog>>(baseUrl, {
    query: { ...filters, page: (filters.page ?? 1) - 1, pageSize: filters.perPage },
  });
  return mapPaginatedResponse(response);
}
