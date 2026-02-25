export type ShellType = "WEBSHELL" | "REVERSE" | "BIND";
export type ShellStatus = "CONNECTED" | "DISCONNECTED" | "ERROR";
export type ShellLanguage = "java" | "nodejs";

export interface ShellConnection {
  id: number;
  url: string;
  language: ShellLanguage;
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

  // Normalized system info
  basicInfo?: Record<string, string>;
}

export interface ShellConnectionSearchParams {
  status?: ShellStatus | string;
  projectId?: number;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}
