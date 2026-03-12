export interface Permission {
  id: number;
  name: string;
  code: string;
  category?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Role {
  id: number;
  name: string;
  permissions: Permission[];
  createdAt: string;
  updatedAt: string;
}

export type UserStatus = "ENABLED" | "DISABLED" | "LOCKED" | "UNACTIVATED";

export interface User {
  id: number;
  username: string;
  email: string;
  password?: string;
  roles?: Role[];
  authorities?: string[];
  roleIds?: number[];
  status: UserStatus;
  mfaEnabled: boolean;
  mustChangePassword?: boolean;
  createdAt: string;
  updatedAt?: string;
  lastLogin?: string | null;
  lastLoginIp?: string | null;
  passwordChangedAt?: string | null;
  mfaBoundAt?: string | null;
}

export interface LoginLog {
  id: number;
  userId: number;
  username: string;
  sessionId?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
  deviceInfo?: string | null;
  browser?: string | null;
  os?: string | null;
  status: string;
  failReason?: string | null;
  loginTime: string;
}

export interface UserSession {
  id: number;
  sessionId: string;
  ipAddress?: string | null;
  userAgent?: string | null;
  deviceInfo?: string | null;
  createdAt: string;
  updatedAt?: string | null;
  lastSeenAt?: string | null;
  accessExpiresAt?: string | null;
  refreshExpiresAt?: string | null;
  revoked: boolean;
  revokedAt?: string | null;
  revokeReason?: string | null;
}
