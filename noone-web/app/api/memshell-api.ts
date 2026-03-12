import type { AuthFetch } from "@/api.server";
import type {
  MainConfig,
  MemShellGenerateRequest,
  MemShellGenerateResponse,
  PackerConfig,
  ServerConfig,
} from "@/types/memshell";

const baseUrl = "/memshell";

export async function getServers(authFetch: AuthFetch): Promise<ServerConfig> {
  return await authFetch<ServerConfig>(`${baseUrl}/config/servers`);
}

export async function getPackers(authFetch: AuthFetch): Promise<PackerConfig> {
  return await authFetch<PackerConfig>(`${baseUrl}/config/packers`);
}

export async function getMainConfig(authFetch: AuthFetch): Promise<MainConfig> {
  return await authFetch<MainConfig>(`${baseUrl}/config`);
}

export async function generate(
  body: MemShellGenerateRequest,
  authFetch: AuthFetch,
): Promise<MemShellGenerateResponse> {
  return await authFetch<MemShellGenerateResponse>(`${baseUrl}/generate`, {
    method: "POST",
    body,
  });
}
