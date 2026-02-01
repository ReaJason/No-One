import type {
  MainConfig,
  MemShellGenerateRequest,
  MemShellGenerateResponse,
  PackerConfig,
  ServerConfig,
} from "@/types/memshell";
import { type ApiResponse, apiClient } from "./api-client";

const baseUrl = "/memshell";

export async function getServers(): Promise<ServerConfig> {
  return (await apiClient.get<ServerConfig>(`${baseUrl}/config/servers`)).data;
}

export async function getPackers(): Promise<PackerConfig> {
  return (await apiClient.get<PackerConfig>(`${baseUrl}/config/packers`)).data;
}

export async function getMainConfig(): Promise<MainConfig> {
  return (await apiClient.get<MainConfig>(`${baseUrl}/config`)).data;
}

export async function generate(
  body: MemShellGenerateRequest,
): Promise<ApiResponse<MemShellGenerateResponse>> {
  return await apiClient.post<MemShellGenerateResponse>(
    `${baseUrl}/generate`,
    body,
  );
}
