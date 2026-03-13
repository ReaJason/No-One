import type { LoaderFunctionArgs } from "react-router";

import {
  getShellPluginStatusFromRoute,
  parseShellIdParam,
  shellRouteError,
  shellRouteSuccess,
} from "@/lib/shell-route.server";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const requestId = new URL(request.url).searchParams.get("requestId")?.trim() || undefined;

  try {
    const shellId = parseShellIdParam(params.shellId);
    const pluginId = new URL(request.url).searchParams.get("pluginId")?.trim();

    if (!pluginId) {
      return Response.json({ ok: false, error: "Missing pluginId", requestId }, { status: 400 });
    }

    const data = await getShellPluginStatusFromRoute(request, context, shellId, pluginId);
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json({ ok: false, error: message }, { status: error.status || 400 });
    }
    return shellRouteError(error, "Failed to load plugin status", requestId);
  }
}
