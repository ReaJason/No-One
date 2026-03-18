import { beforeEach, describe, expect, it, vi } from "vitest";

const { createPublicApiMock, getSessionMock, commitSessionMock } = vi.hoisted(() => ({
  createPublicApiMock: vi.fn(),
  getSessionMock: vi.fn(),
  commitSessionMock: vi.fn(),
}));

vi.mock("@/api/api.server", () => ({
  createPublicApi: createPublicApiMock,
}));

vi.mock("@/api/sessions.server", () => ({
  getSession: getSessionMock,
  commitSession: commitSessionMock,
}));

import { action as loginAction } from "@/routes/auth/login";
import {
  action as verifyTwoFactorAction,
  loader as verifyTwoFactorLoader,
} from "@/routes/auth/verify-2fa";

type SessionStore = {
  get: (key: string) => any;
  set: ReturnType<typeof vi.fn>;
  unset: ReturnType<typeof vi.fn>;
};

function createSessionStore(initial: Record<string, any> = {}): SessionStore {
  const state = new Map<string, any>(Object.entries(initial));
  return {
    get: (key: string) => state.get(key),
    set: vi.fn((key: string, value: any) => {
      state.set(key, value);
    }),
    unset: vi.fn((key: string) => {
      state.delete(key);
    }),
  };
}

describe("auth login/2fa flow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    commitSessionMock.mockResolvedValue("__session=updated;");
  });

  it("redirects login action to /auth/verify-2fa when backend returns REQUIRE_2FA", async () => {
    const session = createSessionStore();
    const apiCall = vi.fn().mockResolvedValue({
      code: "REQUIRE_2FA",
      actionToken: "challenge-token",
    });
    getSessionMock.mockResolvedValue(session);
    createPublicApiMock.mockReturnValue(apiCall);

    const request = new Request("http://localhost/auth/login", {
      method: "POST",
      body: new URLSearchParams({
        username: "admin",
        password: "password",
        returnTo: "/settings",
      }),
    });

    const response = (await loginAction({ request } as any)) as Response;

    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe("/auth/verify-2fa");
    expect(session.set).toHaveBeenCalledWith("pending2faActionToken", "challenge-token");
    expect(session.set).toHaveBeenCalledWith("pending2faReturnTo", "/settings");
  });

  it("redirects verify-2fa loader to /auth/login when pending challenge is missing", async () => {
    const session = createSessionStore();
    getSessionMock.mockResolvedValue(session);

    const request = new Request("http://localhost/auth/verify-2fa");

    let thrown: unknown;
    try {
      await verifyTwoFactorLoader({ request } as any);
    } catch (error) {
      thrown = error;
    }

    expect(thrown).toBeInstanceOf(Response);
    const response = thrown as Response;
    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe("/auth/login");
    expect(session.unset).toHaveBeenCalledWith("pending2faActionToken");
    expect(session.unset).toHaveBeenCalledWith("pending2faReturnTo");
  });

  it("writes tokens and redirects to returnTo on successful verify-2fa action", async () => {
    const session = createSessionStore({
      pending2faActionToken: "challenge-token",
      pending2faReturnTo: "/shells",
    });
    const apiCall = vi.fn().mockResolvedValue({
      token: "access-token",
      refreshToken: "refresh-token",
      user: { id: 1, username: "admin" },
    });
    getSessionMock.mockResolvedValue(session);
    createPublicApiMock.mockReturnValue(apiCall);

    const request = new Request("http://localhost/auth/verify-2fa", {
      method: "POST",
      body: new URLSearchParams({
        twoFactorCode: "123456",
      }),
    });

    const response = (await verifyTwoFactorAction({ request } as any)) as Response;

    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe("/shells");
    expect(session.set).toHaveBeenCalledWith("accessToken", "access-token");
    expect(session.set).toHaveBeenCalledWith("refreshToken", "refresh-token");
    expect(session.unset).toHaveBeenCalledWith("pending2faActionToken");
    expect(session.unset).toHaveBeenCalledWith("pending2faReturnTo");
  });
});
