export type ShellType = "WEBSHELL" | "REVERSE" | "BIND";
export type ShellStatus = "CONNECTED" | "DISCONNECTED" | "ERROR";
export type ShellLanguage = "java" | "nodejs" | "dotnet";

export interface ShellBasicInfo {
  os?:
    | {
        name?: string;
        [key: string]: unknown;
      }
    | string;
  process?: {
    cwd?: string;
    [key: string]: unknown;
  };
  arch?: string;
  runtimeType?: string;
  runtimeVersion?: string;
  data?: Record<string, unknown>;
  result?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface ShellConnection {
  id: number;
  url: string;
  name: string;
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

  basicInfo?: any;
}

export interface ShellConnectionSearchParams {
  url?: string;
  language?: ShellLanguage | string;
  status?: ShellStatus | string;
  projectId?: number;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}
