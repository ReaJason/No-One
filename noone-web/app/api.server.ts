import type { User } from "@/types/admin";
import type { RouterContextProvider } from "react-router";

import { type FetchOptions, ofetch } from "ofetch";
import { redirect } from "react-router";

import {
  getAuthRefreshPromise,
  getRefreshedAccessToken,
  setAuthRefreshPromise,
  setRefreshedAccessToken,
} from "@/api/auth-context.server";
import { pendingCookieContext } from "@/context.server";
import { commitSession, destroySession, getSession } from "@/sessions.server";

const BASE_URL = process.env.INTERNAL_API_BASE_URL ?? import.meta.env.INTERNAL_API_BASE_URL;

export interface ApiError {
  message: string;
  code?: number;
  details?: any;
}

export class ApiClientError extends Error implements ApiError {
  code?: number;
  details?: any;

  constructor(
    message: string,
    options: {
      code?: number;
      details?: any;
      cause?: unknown;
    } = {},
  ) {
    super(message, { cause: options.cause });
    this.name = "ApiClientError";
    this.code = options.code;
    this.details = options.details;
  }
}

interface LoginResponse {
  token: string;
  refreshToken: string;
  user: User;
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
      console.log("api auth no authenticated, so redirect");
      throw redirect("/auth/login");
    }
    const doRequest = (token: string) =>
      ofetch<T>(`${BASE_URL}${endpoint}`, {
        ...options,
        async onRequest({ request, options }) {
          const headers = new Headers(options.headers);
          headers.set("Authorization", `Bearer ${token}`);
          options.headers = headers;
        },
        async onResponseError({ request, response }: any) {
          const error = new ApiClientError(
            response._data?.message ||
              response._data?.error ||
              response.statusText ||
              "Unknown error",
            {
              code: response.status,
              details: response._data,
            },
          );

          // 处理特定的 HTTP 状态码
          switch (response.status) {
            case 401:
              console.log("401 need authenticated for: ", request);
              break;
            case 403:
              if (!response._data?.message && !response._data?.error) {
                error.message = "Access denied";
              }
              break;
            case 404:
              if (!response._data?.message && !response._data?.error) {
                error.message = "Resource not found";
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
      console.log("refresh token");
      const newTokens = await refreshPromise.finally(() => {
        if (getAuthRefreshPromise() === refreshPromise) {
          setAuthRefreshPromise(null);
        }
      });

      if (!newTokens) {
        console.debug("refresh failed no authenticated, so redirect");
        throw redirect("/auth/login", {
          headers: { "Set-Cookie": await destroySession(session) },
        });
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
  async onRequest({ request, response }: any) {},
  async onResponseError({ request, response }: any) {
    console.error(`[API] Response error for ${request}:`, {
      status: response.status,
      statusText: response.statusText,
      data: response._data,
    });

    const error = new ApiClientError(
      response._data?.message || response._data?.error || response.statusText || "Unknown error",
      {
        code: response.status,
        details: response._data,
      },
    );

    // 处理特定的 HTTP 状态码
    switch (response.status) {
      case 401:
        break;
      case 403:
        if (!response._data?.message && !response._data?.error) {
          error.message = "Access denied";
        }
        break;
      case 404:
        if (!response._data?.message && !response._data?.error) {
          error.message = "Resource not found";
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

    throw error;
  },
});
