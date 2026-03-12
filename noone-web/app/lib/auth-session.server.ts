import { createCookieSessionStorage } from "react-router";
import type { User } from "@/types/admin";
import {
  createAuthVaultEntry,
  deleteAuthVaultEntry,
  updateAuthVaultEntry,
} from "@/lib/auth-vault.server";

export const sessionStorage = createCookieSessionStorage({
  cookie: {
    name: "__session",
    httpOnly: true,
    maxAge: 60 * 60 * 24 * 7,
    path: "/",
    sameSite: "lax",
    secrets: ["default-secret-key-change-in-production"],
    secure: process.env.NODE_ENV === "production",
  },
});

export const SESSION_KEYS = {
  AUTH_SESSION_ID: "auth_session_id",
  USER_INFO: "user_info",
} as const;

export function getCookieValue(
  cookieHeader: string | null | undefined,
  name: string,
): string | null {
  if (!cookieHeader) {
    return null;
  }

  const match = cookieHeader
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`));
  if (!match) {
    return null;
  }

  return decodeURIComponent(match.slice(name.length + 1));
}

async function getSessionStringValue(
  cookieHeader: string | null | undefined,
  key: string,
): Promise<string | null> {
  if (!cookieHeader) {
    return null;
  }

  const session = await sessionStorage.getSession(cookieHeader);
  const value = session.get(key);
  return typeof value === "string" && value.trim() ? value : null;
}

export async function getSessionAuthSessionId(
  cookieHeader: string | null | undefined,
): Promise<string | null> {
  return getSessionStringValue(cookieHeader, SESSION_KEYS.AUTH_SESSION_ID);
}

export async function createAuthResponseHeaders(
  token: string,
  refreshToken: string | null,
  user: User,
  request?: Request,
): Promise<Headers> {
  const session = request
    ? await sessionStorage.getSession(request.headers.get("Cookie"))
    : await sessionStorage.getSession();
  const existingSessionId = session.get(SESSION_KEYS.AUTH_SESSION_ID);
  const authEntry =
    typeof existingSessionId === "string" && existingSessionId.trim()
      ? updateAuthVaultEntry(existingSessionId, {
          accessToken: token,
          refreshToken,
          user,
        })
      : createAuthVaultEntry({
          accessToken: token,
          refreshToken,
          user,
        });

  session.set(SESSION_KEYS.AUTH_SESSION_ID, authEntry.sessionId);
  session.set(SESSION_KEYS.USER_INFO, user);

  const headers = new Headers();
  headers.append("Set-Cookie", await sessionStorage.commitSession(session));
  return headers;
}

export async function createLogoutResponseHeaders(request?: Request): Promise<Headers> {
  const session = request
    ? await sessionStorage.getSession(request.headers.get("Cookie"))
    : await sessionStorage.getSession();
  const sessionId = session.get(SESSION_KEYS.AUTH_SESSION_ID);
  if (typeof sessionId === "string" && sessionId.trim()) {
    deleteAuthVaultEntry(sessionId);
  }

  session.unset(SESSION_KEYS.AUTH_SESSION_ID);
  session.unset(SESSION_KEYS.USER_INFO);

  const headers = new Headers();
  headers.append("Set-Cookie", await sessionStorage.destroySession(session));
  return headers;
}
