import { apiClient, type PaginatedResponse } from "@/api/api-client";
import type {
  ShellConnection,
  ShellConnectionSearchParams,
} from "@/types/shell-connection";

const baseUrl = "/shells";

export interface CreateShellConnectionRequest {
  url: string;
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

export async function getShellConnections(
  filters: ShellConnectionSearchParams = {},
): Promise<PaginatedResponse<ShellConnection>> {
  const {
    group,
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

  if (group) params.group = group;
  if (status) params.status = String(status).toUpperCase();
  if (typeof projectId === "number" && Number.isFinite(projectId)) {
    params.projectId = projectId;
  }

  return await apiClient.getPaginated<ShellConnection>(baseUrl, params);
}

export async function getShellConnectionById(
  id: number | string,
): Promise<ShellConnection> {
  return (await apiClient.get<ShellConnection>(`${baseUrl}/${id}`)).data;
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
  return (await apiClient.put<ShellConnection>(`${baseUrl}/${id}`, payload))
    .data;
}

export async function deleteShellConnection(
  id: number | string,
): Promise<void> {
  await apiClient.delete(`${baseUrl}/${id}`);
}

export interface TestShellConfigRequest {
  url: string;
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
}

export async function testShellConfig(
  payload: TestShellConfigRequest,
): Promise<TestShellConfigResponse> {
  return (
    await apiClient.post<TestShellConfigResponse>(
      `${baseUrl}/test-config`,
      payload,
    )
  ).data;
}

export async function testShellConnection(
  id: number | string,
): Promise<TestShellConfigResponse> {
  return (
    await apiClient.post<TestShellConfigResponse>(`${baseUrl}/${id}/test`)
  ).data;
}
