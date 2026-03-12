import { randomUUID } from "node:crypto";
import type { User } from "@/types/admin";

export interface AuthVaultEntry {
  sessionId: string;
  accessToken: string;
  refreshToken: string | null;
  user: User;
  updatedAt: number;
}

const authVault = new Map<string, AuthVaultEntry>();

export function createAuthVaultEntry(input: {
  accessToken: string;
  refreshToken: string | null;
  user: User;
}): AuthVaultEntry {
  const entry: AuthVaultEntry = {
    sessionId: randomUUID(),
    accessToken: input.accessToken,
    refreshToken: input.refreshToken,
    user: input.user,
    updatedAt: Date.now(),
  };
  authVault.set(entry.sessionId, entry);
  return entry;
}

export function getAuthVaultEntry(sessionId: string | null | undefined): AuthVaultEntry | null {
  if (!sessionId) {
    return null;
  }

  return authVault.get(sessionId) ?? null;
}

export function updateAuthVaultEntry(
  sessionId: string,
  input: {
    accessToken: string;
    refreshToken: string | null;
    user: User;
  },
): AuthVaultEntry {
  const entry: AuthVaultEntry = {
    sessionId,
    accessToken: input.accessToken,
    refreshToken: input.refreshToken,
    user: input.user,
    updatedAt: Date.now(),
  };
  authVault.set(sessionId, entry);
  return entry;
}

export function deleteAuthVaultEntry(sessionId: string | null | undefined): void {
  if (!sessionId) {
    return;
  }

  authVault.delete(sessionId);
}
