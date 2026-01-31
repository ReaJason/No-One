export type ShellType = "WEBSHELL" | "REVERSE" | "BIND";
export type ShellStatus = "CONNECTED" | "DISCONNECTED" | "ERROR";

export interface ShellConnection {
  id: number;
  url: string;
  shellType: ShellType;
  status: ShellStatus;
  group?: string;
  projectId?: number | null;
  createTime: string;
  connectTime?: string;
  updateTime: string;

  // Profile related fields
  profileId: number;
  profileName?: string;

  // Connection configuration
  proxyUrl?: string;
  customHeaders?: Record<string, string>;
  connectTimeoutMs?: number;
  readTimeoutMs?: number;
  skipSslVerify?: boolean;
  maxRetries?: number;
  retryDelayMs?: number;
}

export interface ShellConnectionSearchParams {
  group?: string;
  status?: ShellStatus | string;
  projectId?: number;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}
