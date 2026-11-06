import { beforeEach, describe, expect, it } from "vitest";

import { commitSession, destroySession, getSession } from "@/api/session";

const user = { id: 1, username: "admin" } as any;

describe("browser session", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("persists authentication and pending 2FA state", () => {
    const session = getSession();
    session.set("accessToken", "access-token");
    session.set("refreshToken", "refresh-token");
    session.set("user", user);
    session.set("pending2faReturnTo", "/shells");

    commitSession(session);

    const restored = getSession();
    expect(restored.get("accessToken")).toBe("access-token");
    expect(restored.get("refreshToken")).toBe("refresh-token");
    expect(restored.get("user")).toEqual(user);
    expect(restored.get("pending2faReturnTo")).toBe("/shells");
  });

  it("clears persisted state on logout", () => {
    const session = getSession();
    session.set("accessToken", "access-token");
    commitSession(session);

    destroySession(session);

    expect(getSession().get("accessToken")).toBeUndefined();
  });
});
