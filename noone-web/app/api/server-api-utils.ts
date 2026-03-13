import type { ServerPaginatedResponse, PaginatedResponse } from "@/types/api";
import type { FetchOptions } from "ofetch";

export function mapPaginatedResponse<T>(
  response: ServerPaginatedResponse<T>,
): PaginatedResponse<T> {
  return {
    content: response.content,
    total: response.page.totalElements,
    page: response.page.number + 1,
    pageSize: response.page.size,
    totalPages: response.page.totalPages,
  };
}

export function withHeaders(
  options: FetchOptions<"json", any> = {},
  headers?: Record<string, string>,
): FetchOptions<"json", any> {
  return {
    ...options,
    headers: {
      ...(options.headers as Record<string, string> | undefined),
      ...headers,
    },
  };
}
