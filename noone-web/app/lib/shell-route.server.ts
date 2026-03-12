import { createAuthFetch } from "@/api.server";
import { dispatchPlugin } from "@/api/shell-api";
import { resolveApiErrorMessage } from "@/lib/api-error";
import { ensureShellDispatchPayload } from "@/lib/shell-dispatch";

export interface ParsedShellRouteFormData<T> {
  intent: string;
  payload: T;
  requestId?: string;
}

export function parseShellIdParam(shellIdParam: string | undefined): number {
  const shellId = Number(shellIdParam);
  if (!Number.isInteger(shellId) || shellId <= 0) {
    throw new Response("Invalid shell ID", { status: 400 });
  }
  return shellId;
}

export async function parseShellRouteFormData<T>(
  request: Request,
): Promise<ParsedShellRouteFormData<T>> {
  const formData = await request.formData();
  const intent = String(formData.get("intent") ?? "").trim();
  if (!intent) {
    throw new Response("Missing intent", { status: 400 });
  }

  const requestId = String(formData.get("requestId") ?? "").trim() || undefined;
  const rawPayload = formData.get("payload");
  if (rawPayload != null && typeof rawPayload !== "string") {
    throw new Response("Invalid payload", { status: 400 });
  }

  if (!rawPayload) {
    return {
      intent,
      payload: {} as T,
      requestId,
    };
  }

  try {
    return {
      intent,
      payload: JSON.parse(rawPayload) as T,
      requestId,
    };
  } catch {
    throw new Response("Invalid payload JSON", { status: 400 });
  }
}

export async function dispatchShellPluginFromRoute(
  request: Request,
  context: Parameters<typeof createAuthFetch>[1],
  shellId: number,
  payload: {
    pluginId: string;
    action?: string;
    args?: Record<string, unknown>;
  },
): Promise<Record<string, unknown>> {
  const authFetch = createAuthFetch(request, context);
  const result = await dispatchPlugin(
    {
      id: shellId,
      pluginId: payload.pluginId,
      action: payload.action,
      args: payload.args,
    },
    authFetch,
  );
  return ensureShellDispatchPayload(result);
}

export function shellRouteSuccess<T>(data: T, requestId?: string) {
  return Response.json({
    ok: true,
    data,
    requestId,
  });
}

export function shellRouteError(
  error: unknown,
  fallback: string,
  requestId?: string,
  status = 500,
) {
  return Response.json(
    {
      ok: false,
      error: resolveApiErrorMessage(error, fallback),
      requestId,
    },
    { status },
  );
}
