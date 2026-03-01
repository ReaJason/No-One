import { apiClient, isAbortError as isApiAbortError, type ApiResponse, type RequestConfig } from "./api-client";
import type { ShellPluginDispatchRequest } from "@/types/shell";

// ==================== API Functions ====================

export interface ErrorResponse {
  error: string;
}

export type DispatchPluginOptions = Omit<RequestConfig, "method" | "body">;

export function isAbortError(error: unknown): boolean {
  return isApiAbortError(error);
}

export async function dispatchPlugin(
  dispatch: ShellPluginDispatchRequest,
  options: DispatchPluginOptions = {},
) {
  const response = await apiClient.post(`/shells/${dispatch.id}/dispatch`, dispatch, options);
  ensureSuccess(response, "Dispatch plugin failed");
  return response.data;
}

function ensureSuccess(response: ApiResponse<any>, fallback: string): void {
  if (response.success) {
    return;
  }
  throw new Error(resolveErrorMessage(response, fallback));
}

function resolveErrorMessage(response: ApiResponse<any>, fallback: string): string {
  if (typeof response.message === "string" && response.message.trim()) {
    return response.message.trim();
  }

  const data = response.data;
  if (data && typeof data.error === "string" && data.error.trim()) {
    return data.error.trim();
  }
  if (data && typeof data.errorMessage === "string" && data.errorMessage.trim()) {
    return data.errorMessage.trim();
  }

  return fallback;
}
