import { apiClient } from "./api-client";

// ==================== Types ====================

export interface ShellConnection {
  id: number;
  url: string;
  shellType: "WEBSHELL" | "REVERSE" | "BIND";
  status: "CONNECTED" | "DISCONNECTED" | "ERROR";
  group?: string;
  projectId?: number;
  createTime: string;
  connectTime?: string;
  updateTime: string;
}

export interface FileItem {
  name: string;
  type: "file" | "directory";
  size: string;
  modified: string;
  permissions: string;
}

export interface CommandHistoryItem {
  command: string;
  output: string;
  timestamp: string;
  exitCode?: number;
}

// ==================== API Functions ====================

/**
 * Get system information for a shell
 */
export async function getSystemInfo(shellId: number) {
  const response = await apiClient.get(`/shells/${shellId}/system-info`);
  return response.data;
}

/**
 * List files in a directory
 */
export async function listFiles(
  shellId: number,
  path: string,
  recursive = false,
  maxDepth = 1,
) {
  const response = await apiClient.post(`/shells/${shellId}/files/list`, {
    path,
    recursive,
    maxDepth,
  });
  return response.data;
}

/**
 * Read file content
 */
export async function readFile(
  shellId: number,
  path: string,
  encoding = "UTF-8",
) {
  const response = await apiClient.post(`/shells/${shellId}/files/read`, {
    path,
    encoding,
  });
  return response.data;
}

/**
 * Write file content
 */
export async function writeFile(
  shellId: number,
  path: string,
  content: string,
  encoding = "UTF-8",
) {
  const response = await apiClient.post(`/shells/${shellId}/files/write`, {
    path,
    content,
    encoding,
  });
  return response.data;
}

/**
 * Upload file
 */
export async function uploadFile(
  shellId: number,
  path: string,
  content: string,
) {
  const response = await apiClient.post(`/shells/${shellId}/files/upload`, {
    path,
    content,
  });
  return response.data;
}

/**
 * Download file
 */
export async function downloadFile(shellId: number, path: string) {
  const response = await apiClient.post(`/shells/${shellId}/files/download`, {
    path,
  });
  return response.data;
}

/**
 * Delete file or directory
 */
export async function deleteFile(
  shellId: number,
  path: string,
  recursive = false,
) {
  const response = await apiClient.delete(`/shells/${shellId}/files`, {
    params: { path, recursive },
  });
  return response.data;
}

/**
 * Execute command
 */
export async function executeCommand(shellId: number, command: string) {
  const response = await apiClient.post(`/shells/${shellId}/commands/execute`, {
    command,
  });
  return response.data;
}

/**
 * Test shell connection
 */
export async function testConnection(shellId: number) {
  const response = await apiClient.post(`/shells/${shellId}/test`);
  return response.data;
}
