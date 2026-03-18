import { beforeEach, describe, expect, it, vi } from "vitest";

import { AuthRedirectError } from "@/lib/auth-redirect-error";
import { ApiClientError } from "@/types/api";

const {
  getSessionMock,
  commitSessionMock,
  destroySessionMock,
  getAuthRefreshPromiseMock,
  getRefreshedAccessTokenMock,
  setAuthRefreshPromiseMock,
  setRefreshedAccessTokenMock,
  ofetchMock,
} = vi.hoisted(() => ({
  getSessionMock: vi.fn(),
  commitSessionMock: vi.fn(),
  destroySessionMock: vi.fn(),
  getAuthRefreshPromiseMock: vi.fn(),
  getRefreshedAccessTokenMock: vi.fn(),
  setAuthRefreshPromiseMock: vi.fn(),
  setRefreshedAccessTokenMock: vi.fn(),
  ofetchMock: vi.fn(),
}));

vi.mock("@/api/sessions.server", () => ({
  getSession: getSessionMock,
  commitSession: commitSessionMock,
  destroySession: destroySessionMock,
}));

vi.mock("@/api/auth-context.server", () => ({
  getAuthRefreshPromise: getAuthRefreshPromiseMock,
  getRefreshedAccessToken: getRefreshedAccessTokenMock,
  setAuthRefreshPromise: setAuthRefreshPromiseMock,
  setRefreshedAccessToken: setRefreshedAccessTokenMock,
}));

vi.mock("ofetch", () => ({
  ofetch: Object.assign(ofetchMock, {
    create: vi.fn(() => vi.fn()),
  }),
}));

import { createAuthFetch } from "@/api/api.server";

describe("createAuthFetch", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getRefreshedAccessTokenMock.mockReturnValue(null);
    getAuthRefreshPromiseMock.mockReturnValue(null);
    destroySessionMock.mockResolvedValue("__session=;");
    commitSessionMock.mockResolvedValue("__session=next;");
  });

  it("throws AuthRedirectError when tokens are missing", async () => {
    getSessionMock.mockResolvedValue({
      get(key: string) {
        return key === "user" ? null : undefined;
      },
    });

    const authFetch = createAuthFetch(new Request("http://localhost/profiles"), {
      get: vi.fn(),
      set: vi.fn(),
    } as any);

    await expect(authFetch("/profiles")).rejects.toBeInstanceOf(AuthRedirectError);
  });

  it("throws AuthRedirectError when token refresh fails after a 401", async () => {
    const session = {
      get(key: string) {
        if (key === "accessToken") return "expired-token";
        if (key === "refreshToken") return "refresh-token";
        if (key === "user") return { id: 1 };
        return undefined;
      },
      set: vi.fn(),
    };
    getSessionMock.mockResolvedValue(session);
    ofetchMock.mockImplementation(async (url: string) => {
      if (url.endsWith("/auth/refresh")) {
        throw new Error("refresh failed");
      }

      throw new ApiClientError("Unauthorized", { code: 401 });
    });

    const authFetch = createAuthFetch(new Request("http://localhost/profiles"), {
      get: vi.fn(),
      set: vi.fn(),
    } as any);

    await expect(authFetch("/profiles")).rejects.toBeInstanceOf(AuthRedirectError);
  });
});
