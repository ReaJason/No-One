import { type ApiResponse, apiClient } from "./api-client";

const baseUrl = "/webshell";

export interface WebShellGenerateRequest {
  profileId: string;
  format: "JSP" | "JSPX";
}

export interface WebShellGenerateResponse {
  content: string;
  format: string;
  fileName: string;
}

export async function generateWebShell(
  body: WebShellGenerateRequest,
): Promise<ApiResponse<WebShellGenerateResponse>> {
  return await apiClient.post<WebShellGenerateResponse>(`${baseUrl}/generate`, body);
}
