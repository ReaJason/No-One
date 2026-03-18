import type { RouterContextProvider } from "react-router";

import { type FetchOptions, type IFetchError, ofetch } from "ofetch";

import {
  getAuthRefreshPromise,
  getRefreshedAccessToken,
  setAuthRefreshPromise,
  setRefreshedAccessToken,
} from "@/api/auth-context.server";
import { pendingCookieContext } from "@/api/context.server";
import { commitSession, getSession } from "@/api/sessions.server";
import { AuthRedirectError } from "@/lib/auth-redirect-error";
import { ApiClientError, type LoginResponse } from "@/types/api";

const BASE_URL = process.env.INTERNAL_API_BASE_URL ?? import.meta.env.INTERNAL_API_BASE_URL;
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

export function createAuthFetch(
  request: Request,
  context: Readonly<RouterContextProvider>,
): AuthFetch {
  return async <T>(endpoint: string, options: FetchOptions<"json", any> = {}): Promise<T> => {
    const session = await getSession(request.headers.get("Cookie"));
    let accessToken = getRefreshedAccessToken() ?? session.get("accessToken");
    const refreshToken = session.get("refreshToken");

    if (!accessToken || !refreshToken) {
      throw new AuthRedirectError("missing-token");
    }
    const doRequest = (token: string) =>
      ofetch<T>(`${BASE_URL}${endpoint}`, {
        ...options,
        async onRequest({ options }) {
          const headers = new Headers(options.headers);
          const forwardedHeaders = pickForwardHeaders(request);
          for (const [key, value] of Object.entries(forwardedHeaders)) {
            headers.set(key, value);
          }
          headers.set("Authorization", `Bearer ${token}`);
          options.headers = headers;
        },
        async onRequestError({ error }: any) {
          sanitizeRequestError(error);
        },
        async onResponseError({ r, response }: any) {
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

      let refreshPromise = getAuthRefreshPromise<LoginResponse | null>();
      if (!refreshPromise) {
        refreshPromise = refreshTokens(refreshToken);
        setAuthRefreshPromise(refreshPromise);
      }
      const newTokens = await refreshPromise.finally(() => {
        if (getAuthRefreshPromise() === refreshPromise) {
          setAuthRefreshPromise(null);
        }
      });
      if (!newTokens) {
        throw new AuthRedirectError("refresh-failed");
      }

      setRefreshedAccessToken(newTokens.token);
      session.set("accessToken", newTokens.token);
      session.set("refreshToken", newTokens.refreshToken);
      session.set("user", newTokens.user);
      context.set(pendingCookieContext, await commitSession(session));
      return await doRequest(newTokens.token);
    }
  };
}

// ─── 不需要认证的公开请求 ─────────────────────────────────────────────────────

export const publicApi = ofetch.create({
  baseURL: BASE_URL,
  async onRequestError({ error }: any) {
    sanitizeRequestError(error);
  },
  async onResponseError({ request, response }: any) {
    throw createApiClientErrorFromResponse(response);
  },
});

export function createPublicApi(request: Request) {
  return ofetch.create({
    baseURL: BASE_URL,
    async onRequest({ options }) {
      const headers = new Headers(options.headers);
      const forwardedHeaders = pickForwardHeaders(request);
      for (const [key, value] of Object.entries(forwardedHeaders)) {
        headers.set(key, value);
      }
      options.headers = headers;
    },
    async onRequestError({ error }: any) {
      sanitizeRequestError(error);
    },
    async onResponseError({ request, response }: any) {
      throw createApiClientErrorFromResponse(response);
    },
  });
}

export const FORWARD_HEADERS = [
  "cookie",
  "authorization",
  "accept-language",
  "x-request-id",
  "x-trace-id",
  "x-forwarded-for",
  "x-real-ip",
  "x-forwarded-proto",
  "x-forwarded-host",
] as const;
export function pickForwardHeaders(requestOrHeaders: Request | Headers): Record<string, string> {
  const headers = requestOrHeaders instanceof Request ? requestOrHeaders.headers : requestOrHeaders;

  const result: Record<string, string> = {};
  for (const key of FORWARD_HEADERS) {
    const value = headers.get(key);
    if (value) {
      result[key] = value;
    }
  }
  const ua = headers.get("user-agent");
  if (ua) {
    result["user-agent"] = ua;
    result["x-forwarded-by"] = "ssr-node";
  }

  return result;
}
