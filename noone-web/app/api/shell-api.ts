import { apiClient, type ApiResponse } from "./api-client";
import type { ShellPluginDispatchRequest } from "@/types/shell";

// ==================== API Functions ====================

export interface ErrorResponse {
  error: string;
}

export async function dispatchPlugin(dispatch: ShellPluginDispatchRequest) {
  const response = await apiClient.post(`/shells/${dispatch.id}/dispatch`, dispatch);
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
