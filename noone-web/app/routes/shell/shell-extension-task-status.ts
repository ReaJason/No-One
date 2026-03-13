import type { LoaderFunctionArgs } from "react-router";

import {
  dispatchShellPluginFromRoute,
  parseShellIdParam,
  shellRouteError,
  shellRouteSuccess,
} from "@/lib/shell-route.server";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const requestId = new URL(request.url).searchParams.get("requestId")?.trim() || undefined;

  try {
    const shellId = parseShellIdParam(params.shellId);
    const url = new URL(request.url);
    const pluginId = url.searchParams.get("pluginId")?.trim();
    const taskId = url.searchParams.get("taskId")?.trim();

    if (!pluginId || !taskId) {
      return Response.json(
        { ok: false, error: "Missing pluginId or taskId", requestId },
        { status: 400 },
      );
    }

    const data = await dispatchShellPluginFromRoute(request, context, shellId, {
      pluginId,
      action: "_task_status",
      args: { taskId },
    });
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json({ ok: false, error: message }, { status: error.status || 400 });
    }
    return shellRouteError(error, "Failed to load task status", requestId);
  }
}
