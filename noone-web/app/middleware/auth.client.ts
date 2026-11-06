import type { Route } from "../+types/root";

import { redirect } from "react-router";

import { getSession } from "@/api/session";

export const authMiddleware: Route.ClientMiddlewareFunction = async ({ request }, next) => {
  const session = getSession();
  if (!session.get("accessToken") || !session.get("refreshToken")) {
    const returnTo = new URL(request.url).pathname;
    throw redirect(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  }
  return next();
};
