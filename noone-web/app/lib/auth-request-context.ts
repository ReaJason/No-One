import { AsyncLocalStorage } from "node:async_hooks";
import type { User } from "@/types/admin";

interface AuthRequestStore {
  pendingHeaders: Headers;
  refreshPromise: Promise<unknown> | null;
  refreshedAccessToken: string | null;
  sessionId: string | null;
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
  retriedAfterRefresh: boolean;
}

const authRequestStorage = new AsyncLocalStorage<AuthRequestStore>();

function getSetCookieValues(headers: Headers): string[] {
  const candidate = headers as Headers & {
    getSetCookie?: () => string[];
  };
  if (typeof candidate.getSetCookie === "function") {
    return candidate.getSetCookie();
  }

  const fallback = headers.get("Set-Cookie");
  return fallback ? [fallback] : [];
}

function appendHeaders(target: Headers, source: Headers): void {
  for (const cookie of getSetCookieValues(source)) {
    target.append("Set-Cookie", cookie);
  }

  for (const [key, value] of source.entries()) {
    if (key.toLowerCase() === "set-cookie") {
      continue;
    }
    target.append(key, value);
  }
}

export async function runWithAuthRequestContext<T>(callback: () => Promise<T>): Promise<T> {
  return authRequestStorage.run(
    {
      pendingHeaders: new Headers(),
      refreshPromise: null,
      refreshedAccessToken: null,
      sessionId: null,
      accessToken: null,
      refreshToken: null,
      user: null,
      retriedAfterRefresh: false,
    },
    callback,
  );
}

export function appendAuthResponseHeaders(headers: Headers): void {
  const store = authRequestStorage.getStore();
  if (!store) {
    return;
  }

  appendHeaders(store.pendingHeaders, headers);
}

export function mergeAuthResponseHeaders(response: Response): Response {
  const store = authRequestStorage.getStore();
  if (!store) {
    return response;
  }

  const hasPendingSetCookie = getSetCookieValues(store.pendingHeaders).length > 0;
  const hasPendingOtherHeaders = Array.from(store.pendingHeaders.keys()).some(
    (key) => key.toLowerCase() !== "set-cookie",
  );
  if (!hasPendingSetCookie && !hasPendingOtherHeaders) {
    return response;
  }

  const headers = new Headers(response.headers);
  appendHeaders(headers, store.pendingHeaders);

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

export function getAuthRefreshPromise<T>(): Promise<T> | null {
  const store = authRequestStorage.getStore();
  return (store?.refreshPromise as Promise<T> | null) ?? null;
}

export function setAuthRefreshPromise<T>(promise: Promise<T> | null): void {
  const store = authRequestStorage.getStore();
  if (!store) {
    return;
  }

  store.refreshPromise = promise;
}

export function getRefreshedAccessToken(): string | null {
  return authRequestStorage.getStore()?.refreshedAccessToken ?? null;
}

export function setRefreshedAccessToken(token: string | null): void {
  const store = authRequestStorage.getStore();
  if (store) {
    store.refreshedAccessToken = token;
  }
}

export function setCurrentAuthSession(input: {
  sessionId: string | null;
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
}): void {
  const store = authRequestStorage.getStore();
  if (!store) {
    return;
  }

  store.sessionId = input.sessionId;
  store.accessToken = input.accessToken;
  store.refreshToken = input.refreshToken;
  store.user = input.user;
  store.refreshedAccessToken = input.accessToken;
}

export function getCurrentSessionId(): string | null {
  return authRequestStorage.getStore()?.sessionId ?? null;
}

export function getCurrentAccessToken(): string | null {
  return authRequestStorage.getStore()?.accessToken ?? null;
}

export function getCurrentRefreshToken(): string | null {
  return authRequestStorage.getStore()?.refreshToken ?? null;
}

export function getCurrentUser(): User | null {
  return authRequestStorage.getStore()?.user ?? null;
}

export function markRetriedAfterRefresh(): void {
  const store = authRequestStorage.getStore();
  if (store) {
    store.retriedAfterRefresh = true;
  }
}

export function hasRetriedAfterRefresh(): boolean {
  return authRequestStorage.getStore()?.retriedAfterRefresh ?? false;
}
