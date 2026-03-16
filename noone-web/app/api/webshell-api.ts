import type { AuthFetch } from "@/api/api.server";

const baseUrl = "/webshell";

export type WebShellLanguage = "java" | "dotnet" | "nodejs";
export type WebShellFormat = "JSP" | "JSPX" | "ASPX" | "ASHX" | "ASMX" | "SOAP" | "MJS";

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
  authFetch: AuthFetch,
): Promise<WebShellGenerateResponse> {
  return await authFetch<WebShellGenerateResponse>(`${baseUrl}/generate`, {
    method: "POST",
    body,
  });
}
