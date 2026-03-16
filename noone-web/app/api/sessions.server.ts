import type { User } from "@/types/admin";

import { createCookieSessionStorage } from "react-router";

type SessionData = {
  accessToken: string;
  refreshToken: string;
  user: User;
};

type SessionFlashData = {
  error: string;
  success: string;
};

const { getSession, commitSession, destroySession } = createCookieSessionStorage<
  SessionData,
  SessionFlashData
>({
  cookie: {
    name: "__session",
    httpOnly: true,
    maxAge: 60 * 60 * 24 * 7,
    path: "/",
    sameSite: "lax",
    secrets: [process.env.SESSION_SECRET!],
    secure: process.env.SESSION_COOKIE_SECURE === "true",
  },
});

export { getSession, commitSession, destroySession };
