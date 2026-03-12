import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import {
  dispatchShellPluginFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
} from "@/lib/shell-route.server";

function getNumberParam(value: string | null): number | undefined {
  if (value == null || value.trim() === "") {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const requestId = new URL(request.url).searchParams.get("requestId")?.trim() || undefined;

  try {
    const shellId = parseShellIdParam(params.shellId);
    const url = new URL(request.url);
    const intent = url.searchParams.get("intent")?.trim();
    const path = url.searchParams.get("path")?.trim();

    if (!intent) {
      return Response.json({ ok: false, error: "Missing intent", requestId }, { status: 400 });
    }
    if (!path) {
      return Response.json({ ok: false, error: "Missing path", requestId }, { status: 400 });
    }

    const args: Record<string, unknown> = { op: intent, path };
    const maxBytes = getNumberParam(url.searchParams.get("maxBytes"));
    const offset = getNumberParam(url.searchParams.get("offset"));
    const length = getNumberParam(url.searchParams.get("length"));
    if (maxBytes != null) args.maxBytes = maxBytes;
    if (offset != null) args.offset = offset;
    if (length != null) args.length = length;

    const data = await dispatchShellPluginFromRoute(request, context, shellId, {
      pluginId: "file-manager",
      args,
    });
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json({ ok: false, error: message }, { status: error.status || 400 });
    }
    return shellRouteError(error, "File manager request failed", requestId);
  }
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  try {
    const shellId = parseShellIdParam(params.shellId);
    const { payload, requestId } = await parseShellRouteFormData<Record<string, unknown>>(request);
    const data = await dispatchShellPluginFromRoute(request, context, shellId, {
      pluginId: "file-manager",
      args: payload,
    });
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json({ ok: false, error: message }, { status: error.status || 400 });
    }
    return shellRouteError(error, "File manager request failed");
  }
}
