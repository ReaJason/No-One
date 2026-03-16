export type AuditModule = "USER" | "ROLE" | "PROJECT" | "SHELL" | "PROFILE";

export type AuditAction =
  | "CREATE"
  | "UPDATE"
  | "DELETE"
  | "PASSWORD_CHANGE"
  | "PASSWORD_RESET"
  | "MFA_SETUP";

export interface AuditLog {
  id: number;
  userId: number;
  username: string;
  module: AuditModule;
  action: AuditAction;
  targetType: string;
  targetId: string;
  description: string;
  success: boolean;
  errorMessage: string | null;
  durationMs: number | null;
  ipAddress: string;
  userAgent: string;
  requestMethod: string;
  requestUri: string;
  details: Record<string, unknown> | null;
  createdAt: string;
}
