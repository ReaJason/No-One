import { createCookieSessionStorage } from "react-router";
import type { User } from "@/types/admin";

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
    secure: process.env.NODE_ENV === "production",
  },
});

export { getSession, commitSession, destroySession };
