import { type ApiResponse, apiClient } from "./api-client";

const baseUrl = "/webshell";

export type WebShellLanguage = "java" | "dotnet";
export type WebShellFormat = "JSP" | "JSPX" | "ASPX" | "ASHX" | "ASMX" | "SOAP";

export interface WebShellGenerateRequest {
  profileId: string;
  language: WebShellLanguage;
  format: WebShellFormat;
  servletModule?: "JAVAX" | "JAKARTA";
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
