import { type FetchOptions, type IFetchError, ofetch } from "ofetch";

import { commitSession, getSession } from "@/api/session";
import { AuthRedirectError } from "@/lib/auth-redirect-error";
import { ApiClientError, type LoginResponse } from "@/types/api";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";
const BACKEND_UNAVAILABLE_MESSAGE = "Backend service is unavailable";
const BACKEND_UNAVAILABLE_PATTERNS = [
  "no response",
  "fetch failed",
  "backend unavailable",
  "service unavailable",
  "connection refused",
  "connect: connection refused",
  "dial tcp",
  "socket hang up",
  "timed out",
  "timeout",
  "econnrefused",
  "econnreset",
  "enotfound",
  "eai_again",
  "bad gateway",
  "upstream connect error",
];

let authRefreshPromise: Promise<LoginResponse | null> | null = null;

function isBackendUnavailableMessage(message?: string): boolean {
  if (!message) {
    return false;
  }

  const normalizedMessage = message.toLowerCase();
  return BACKEND_UNAVAILABLE_PATTERNS.some((pattern) => normalizedMessage.includes(pattern));
}

function createBackendUnavailableError(code = 503) {
  return new ApiClientError(BACKEND_UNAVAILABLE_MESSAGE, { code });
}

function createApiClientErrorFromResponse(response: any) {
  const rawMessage =
    response._data?.message || response._data?.error || response.statusText || "Unknown error";
  const error = isBackendUnavailableMessage(rawMessage)
    ? createBackendUnavailableError(response.status)
    : new ApiClientError(rawMessage, {
        code: response.status,
        details: response._data,
      });

  if (error.message === BACKEND_UNAVAILABLE_MESSAGE) {
    return error;
  }

  switch (response.status) {
    case 403:
      if (!response._data?.message && !response._data?.error) {
        error.message = "Access denied";
      }
      break;
    case 422:
      if (!response._data?.message && !response._data?.error) {
        error.message = "Validation error";
      }
      break;
    case 500:
      if (!response._data?.message && !response._data?.error) {
        error.message = "Internal server error";
      }
      break;
  }

  return error;
}

function sanitizeRequestError(error: unknown): never {
  const fetchError = error as Partial<IFetchError> & {
    cause?: { code?: string };
  };
  const errorMessage = error instanceof Error ? error.message : String(error ?? "");
  const errorCode = fetchError.cause?.code?.toLowerCase();

  if (
    !fetchError.response &&
    (isBackendUnavailableMessage(errorMessage) ||
      errorMessage.includes("<no response>") ||
      errorCode === "econnrefused" ||
      errorCode === "econnreset" ||
      errorCode === "enotfound" ||
      errorCode === "eai_again" ||
      errorCode === "etimedout")
  ) {
    throw createBackendUnavailableError();
  }

  throw error;
}

async function refreshTokens(refreshToken: string): Promise<LoginResponse | null> {
  try {
    return await ofetch<LoginResponse>(`${BASE_URL}/auth/refresh`, {
      method: "POST",
      body: { refreshToken },
    });
  } catch {
    return null;
  }
}

export type AuthFetch = <T>(endpoint: string, options?: FetchOptions<"json", any>) => Promise<T>;

export function createAuthFetch(_request: Request, _context?: unknown): AuthFetch {
  return async <T>(endpoint: string, options: FetchOptions<"json", any> = {}): Promise<T> => {
    const session = getSession();
    const accessToken = session.get("accessToken");
    const refreshToken = session.get("refreshToken");

    if (!accessToken || !refreshToken) {
      throw new AuthRedirectError("missing-token");
    }
    const doRequest = (token: string) =>
      ofetch<T>(`${BASE_URL}${endpoint}`, {
        ...options,
        async onRequest({ options }) {
          const headers = new Headers(options.headers);
          headers.set("Authorization", `Bearer ${token}`);
          options.headers = headers;
        },
        async onRequestError({ error }: any) {
          sanitizeRequestError(error);
        },
        async onResponseError({ response }: any) {
          const error = createApiClientErrorFromResponse(response);
          if (response.status === 404) {
            throw new Response("Resource not found", {
              status: 404,
              statusText: "Not Found",
            });
          }
          throw error;
        },
      });

    try {
      return await doRequest(accessToken);
    } catch (error: any) {
      const e = error as ApiClientError;
      if (e?.code !== 401) {
        throw error;
      }

      if (!authRefreshPromise) {
        authRefreshPromise = refreshTokens(refreshToken);
      }
      const refreshPromise = authRefreshPromise;
      const newTokens = await refreshPromise.finally(() => {
        if (authRefreshPromise === refreshPromise) {
          authRefreshPromise = null;
        }
      });
      if (!newTokens) {
        throw new AuthRedirectError("refresh-failed");
      }

      session.set("accessToken", newTokens.token);
      session.set("refreshToken", newTokens.refreshToken);
      session.set("user", newTokens.user);
      commitSession(session);
      return await doRequest(newTokens.token);
    }
  };
}

export function createPublicApi(_request: Request) {
  return ofetch.create({
    baseURL: BASE_URL,
    async onRequestError({ error }: any) {
      sanitizeRequestError(error);
    },
    async onResponseError({ response }: any) {
      throw createApiClientErrorFromResponse(response);
    },
  });
}
