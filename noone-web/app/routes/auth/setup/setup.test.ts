import { beforeEach, describe, expect, it, vi } from "vitest";

const { createPublicApiMock, getSessionMock, commitSessionMock } = vi.hoisted(() => ({
  createPublicApiMock: vi.fn(),
  getSessionMock: vi.fn(),
  commitSessionMock: vi.fn(),
}));

vi.mock("@/api/api", () => ({
  createPublicApi: createPublicApiMock,
}));

vi.mock("@/api/session", () => ({
  getSession: getSessionMock,
  commitSession: commitSessionMock,
}));

import {
  clientAction as setupAction,
  clientLoader as setupLoader,
} from "@/routes/auth/setup/route";

describe("auth setup flow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    commitSessionMock.mockResolvedValue("__session=updated;");
  });

  it("loads the password setup form without requesting a 2FA QR code", async () => {
    const request = new Request("http://localhost/auth/setup?token=setup-token");

    const result = await setupLoader({ request } as any);

    expect(result).toEqual({ token: "setup-token" });
    expect(createPublicApiMock).not.toHaveBeenCalled();
  });

  it("activates with only a new password and enters the console", async () => {
    const session = { set: vi.fn() };
    const apiCall = vi.fn().mockResolvedValue({
      token: "access-token",
      refreshToken: "refresh-token",
      user: { id: 1, username: "admin", mfaEnabled: false },
    });
    getSessionMock.mockResolvedValue(session);
    createPublicApiMock.mockReturnValue(apiCall);

    const request = new Request("http://localhost/auth/setup", {
      method: "POST",
      body: new URLSearchParams({
        token: "setup-token",
        newPassword: "new-password",
        confirmPassword: "new-password",
      }),
    });

    const response = (await setupAction({ request } as any)) as Response;

    expect(apiCall).toHaveBeenCalledWith("/setup/activate", {
      method: "POST",
      headers: { "Setup-Token": "setup-token" },
      body: { newPassword: "new-password" },
    });
    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe("/");
    expect(session.set).toHaveBeenCalledWith("accessToken", "access-token");
    expect(session.set).toHaveBeenCalledWith("refreshToken", "refresh-token");
  });
});
