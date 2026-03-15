export type ShellType = string;
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
  staging?: boolean;
  language: ShellLanguage;
  shellType?: ShellType | null;
  status: ShellStatus;
  projectId?: number | null;
  createdAt: string;
  lastOnlineAt?: string;
  updatedAt: string;

  // Profile related fields
  profileId: number;
  loaderProfileId?: number | null;
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
