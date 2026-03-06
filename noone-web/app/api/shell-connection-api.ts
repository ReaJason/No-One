import { apiClient, type PaginatedResponse } from "@/api/api-client";
import type {
  ShellConnection,
  ShellLanguage,
  ShellConnectionSearchParams,
} from "@/types/shell-connection";

const baseUrl = "/shells";

export interface CreateShellConnectionRequest {
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
}

export async function getShellConnections(
  filters: ShellConnectionSearchParams = {},
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

  return await apiClient.getPaginated<ShellConnection>(baseUrl, params);
}

export async function getShellConnectionById(
  id: number | string,
  options: ShellConnectionRequestOptions = {},
): Promise<ShellConnection> {
  return (
    await apiClient.get<ShellConnection>(`${baseUrl}/${id}`, undefined, {
      signal: options.signal,
    })
  ).data;
}

export async function createShellConnection(
  payload: CreateShellConnectionRequest,
): Promise<ShellConnection> {
  return (await apiClient.post<ShellConnection>(baseUrl, payload)).data;
}

export async function updateShellConnection(
  id: number | string,
  payload: UpdateShellConnectionRequest,
): Promise<ShellConnection> {
  return (await apiClient.put<ShellConnection>(`${baseUrl}/${id}`, payload)).data;
}

export async function deleteShellConnection(id: number | string): Promise<void> {
  await apiClient.delete(`${baseUrl}/${id}`);
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
): Promise<TestShellConfigResponse> {
  const response = await apiClient.post<TestShellConfigResponse>(`${baseUrl}/test-config`, payload);

  if (!response.success) {
    throw new Error(resolveErrorMessage(response.data, "Connection test failed"));
  }
  if (!isTestShellConfigResponse(response.data)) {
    throw new Error(resolveErrorMessage(response.data, "Connection test failed"));
  }
  return response.data;
}

export async function testShellConnection(
  id: number | string,
  options: ShellConnectionRequestOptions = {},
): Promise<TestShellConnectionResponse> {
  const response = await apiClient.post<TestShellConnectionResponse>(
    `${baseUrl}/${id}/test`,
    undefined,
    {
      signal: options.signal,
    },
  );

  if (!response.success) {
    throw new Error(resolveErrorMessage(response.data, "Connection test failed"));
  }
  if (!isTestShellConnectionResponse(response.data)) {
    throw new Error(resolveErrorMessage(response.data, "Connection test failed"));
  }
  return response.data;
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
