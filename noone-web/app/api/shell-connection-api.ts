import type { FetchOptions } from "ofetch";
import type { AuthFetch } from "@/api.server";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type {
  ShellConnection,
  ShellConnectionSearchParams,
  ShellLanguage,
} from "@/types/shell-connection";

const baseUrl = "/shells";

export interface CreateShellConnectionRequest {
  name: string;
  url: string;
  language: ShellLanguage;
  group?: string;
  projectId?: number;
  profileId: number;
  proxyUrl?: string;
  customHeaders?: Record<string, string>;
  connectTimeoutMs?: number;
  readTimeoutMs?: number;
  skipSslVerify?: boolean;
  maxRetries?: number;
  retryDelayMs?: number;
}

export interface UpdateShellConnectionRequest {
  name?: string;
  url?: string;
  language: ShellLanguage;
  group?: string;
  projectId?: number | null;
  profileId: number;
  proxyUrl?: string;
  customHeaders?: Record<string, string>;
  connectTimeoutMs?: number;
  readTimeoutMs?: number;
  skipSslVerify?: boolean;
  maxRetries?: number;
  retryDelayMs?: number;
}

interface ShellConnectionRequestOptions {
  signal?: AbortSignal;
  options?: FetchOptions<"json", any>;
}

export async function getShellConnections(
  filters: ShellConnectionSearchParams = {},
  authFetch: AuthFetch,
): Promise<PaginatedResponse<ShellConnection>> {
  const {
    url,
    language,
    status,
    projectId,
    page = 1,
    perPage = 10,
    sortBy = "createTime",
    sortOrder = "desc",
  } = filters;

  const params: Record<string, any> = {
    page,
    perPage,
    sortBy,
    sortOrder,
  };

  if (url) params.url = url;
  if (language) params.language = String(language).toLowerCase();
  if (status) params.status = String(status).toUpperCase();
  if (typeof projectId === "number" && Number.isFinite(projectId)) {
    params.projectId = projectId;
  }

  const response = await authFetch<ServerPaginatedResponse<ShellConnection>>(baseUrl, {
    query: { ...params, page: (params.page ?? 1) - 1, pageSize: params.perPage },
  });

  return mapPaginatedResponse(response);
}

export async function getShellConnectionById(
  id: number | string,
  authFetch: AuthFetch,
  requestOptions: ShellConnectionRequestOptions = {},
): Promise<ShellConnection> {
  const { signal, options } = requestOptions;
  return await authFetch<ShellConnection>(`${baseUrl}/${id}`, {
    ...options,
    signal,
  });
}

export async function createShellConnection(
  payload: CreateShellConnectionRequest,
  authFetch: AuthFetch,
): Promise<ShellConnection> {
  console.log(payload);
  return await authFetch<ShellConnection>(baseUrl, {
    method: "POST",
    body: payload,
  });
}

export async function updateShellConnection(
  id: number | string,
  payload: UpdateShellConnectionRequest,
  authFetch: AuthFetch,
): Promise<ShellConnection> {
  return await authFetch<ShellConnection>(`${baseUrl}/${id}`, {
    method: "PUT",
    body: payload,
  });
}

export async function deleteShellConnection(
  id: number | string,
  authFetch: AuthFetch,
): Promise<void> {
  await authFetch(`${baseUrl}/${id}`, {
    method: "DELETE",
  });
}

export interface TestShellConfigRequest {
  url: string;
  language: ShellLanguage;
  profileId: number;
  proxyUrl?: string;
  customHeaders?: Record<string, string>;
  connectTimeoutMs?: number;
  readTimeoutMs?: number;
  skipSslVerify?: boolean;
  maxRetries?: number;
  retryDelayMs?: number;
}

export interface TestShellConfigResponse {
  connected: boolean;
  status: "CONNECTED" | "ERROR";
  error?: string;
  errorMessage?: string;
}

export interface TestShellConnectionResponse {
  connected: boolean;
  status: "CONNECTED" | "ERROR";
  error?: string;
  errorMessage?: string;
}

export async function testShellConfig(
  payload: TestShellConfigRequest,
  authFetch: AuthFetch,
): Promise<TestShellConfigResponse> {
  const response = await authFetch<unknown>(`${baseUrl}/test-config`, {
    method: "POST",
    body: payload,
  });

  if (!isTestShellConfigResponse(response)) {
    throw new Error(resolveErrorMessage(response, "Connection test failed"));
  }
  return response;
}

export async function testShellConnection(
  id: number | string,
  authFetch: AuthFetch,
  requestOptions: ShellConnectionRequestOptions = {},
): Promise<TestShellConnectionResponse> {
  const { signal, options } = requestOptions;
  const response = await authFetch<unknown>(`${baseUrl}/${id}/test`, {
    ...options,
    method: "POST",
    signal,
  });

  if (!isTestShellConnectionResponse(response)) {
    throw new Error(resolveErrorMessage(response, "Connection test failed"));
  }
  return response;
}

function mapPaginatedResponse<T>(response: ServerPaginatedResponse<T>): PaginatedResponse<T> {
  return {
    content: response.content,
    total: response.page.totalElements,
    page: response.page.number + 1,
    pageSize: response.page.size,
    totalPages: response.page.totalPages,
  };
}

function isTestShellConfigResponse(data: unknown): data is TestShellConfigResponse {
  if (!data || typeof data !== "object") {
    return false;
  }

  const candidate = data as Partial<TestShellConfigResponse>;
  return typeof candidate.connected === "boolean";
}

function isTestShellConnectionResponse(data: unknown): data is TestShellConnectionResponse {
  if (!data || typeof data !== "object") {
    return false;
  }

  const candidate = data as Partial<TestShellConnectionResponse>;
  return typeof candidate.connected === "boolean" && typeof candidate.status === "string";
}

function resolveErrorMessage(data: any, fallback: string): string {
  if (data && typeof data.error === "string" && data.error.trim()) {
    return data.error.trim();
  }
  if (data && typeof data.errorMessage === "string" && data.errorMessage.trim()) {
    return data.errorMessage.trim();
  }

  return fallback;
}
