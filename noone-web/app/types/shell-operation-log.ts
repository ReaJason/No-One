export interface ShellOperationLog {
  id: number;
  shellId: number;
  username: string;
  operation: "TEST" | "DISPATCH";
  pluginId: string | null;
  action: string | null;
  args: Record<string, unknown> | null;
  result: Record<string, unknown> | null;
  success: boolean;
  errorMessage: string | null;
  durationMs: number;
  createdAt: string;
}

export interface ShellOperationLogQuery {
  pluginId?: string;
  operation?: string;
  success?: boolean;
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}
