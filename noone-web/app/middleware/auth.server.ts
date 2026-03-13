import type { Route } from "../+types/root";

import { redirect } from "react-router";

import { runWithAuthRequestContext } from "@/api/auth-context.server";
import { pendingCookieContext } from "@/context.server";
import { getSession } from "@/sessions.server";

export const authMiddleware: Route.MiddlewareFunction = async ({ request, context }, next) => {
  const session = await getSession(request.headers.get("Cookie"));
  const accessToken = session.get("accessToken");
  const refreshToken = session.get("refreshToken");
  if (!accessToken || !refreshToken) {
    console.log("not authenticated middleware, so redirect");
    throw redirect("/auth/login");
  }
  const response = await runWithAuthRequestContext(() => next());
  const pendingCookie = context.get(pendingCookieContext);
  if (pendingCookie) {
    response.headers.append("Set-Cookie", pendingCookie);
  }
  return response;
};
