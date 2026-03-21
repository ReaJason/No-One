import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getAllPluginStatuses, updatePlugin } from "@/api/shell-api";
import { parseShellIdParam, shellRouteError, shellRouteSuccess } from "@/lib/shell-route.server";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const requestId = new URL(request.url).searchParams.get("requestId")?.trim() || undefined;
  try {
    const shellId = parseShellIdParam(params.shellId);
    const authFetch = createAuthFetch(request, context);
    const data = await getAllPluginStatuses(shellId, authFetch);
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json(
        { ok: false, error: message, requestId },
        { status: error.status || 400 },
      );
    }
    return shellRouteError(error, "Failed to load plugin statuses", requestId);
  }
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const requestId = String(formData.get("requestId") ?? "").trim() || undefined;
  try {
    const shellId = parseShellIdParam(params.shellId);
    const authFetch = createAuthFetch(request, context);
    const pluginIds = String(formData.get("pluginIds") ?? "")
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);

    if (pluginIds.length === 0) {
      return Response.json({ ok: false, error: "Missing pluginIds", requestId }, { status: 400 });
    }

    const results: Record<string, unknown> = {};
    for (const pluginId of pluginIds) {
      try {
        results[pluginId] = await updatePlugin(shellId, pluginId, authFetch);
      } catch (err: any) {
        results[pluginId] = { error: err.message || "Update failed" };
      }
    }
    return shellRouteSuccess(results, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json(
        { ok: false, error: message, requestId },
        { status: error.status || 400 },
      );
    }
    return shellRouteError(error, "Plugin update failed", requestId);
  }
}
