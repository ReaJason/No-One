import { useState } from "react";
import {
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
  useLoaderData,
  useNavigate,
  useRevalidator,
} from "react-router";

import { createAuthFetch } from "@/api.server";
import * as shellApi from "@/api/shell-api";
import * as opLogApi from "@/api/shell-operation-log-api";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import SystemDashboard from "@/components/shell/system-info";
import {
  getShellPluginStatusFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
  updateShellPluginFromRoute,
} from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = parseShellIdParam(params.shellId);
  const url = new URL(request.url);
  const forceRefresh = url.searchParams.has("forceRefresh");
  const authFetch = createAuthFetch(request, context);
  let pluginStatus = await getShellPluginStatusFromRoute(request, context, shellId, "system-info");

  try {
    if (!forceRefresh) {
      try {
        const cached = await opLogApi.getLatestShellOperation(shellId, "system-info", authFetch);
        if (cached?.result) {
          return { pluginStatus, systemInfo: cached.result, error: null };
        }
      } catch {
        // Fall through to fresh request
      }
    }

    const data = await shellApi.dispatchPlugin(
      {
        id: shellId,
        pluginId: "system-info",
      },
      authFetch,
    );
    if (!pluginStatus.loaded) {
      pluginStatus = await getShellPluginStatusFromRoute(request, context, shellId, "system-info");
    }
    return { pluginStatus, systemInfo: data, error: null };
  } catch (err: any) {
    return {
      pluginStatus,
      systemInfo: null,
      error: `Failed to load system info: ${err.message || "Unknown error"}`,
    };
  }
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  try {
    const shellId = parseShellIdParam(params.shellId);
    const { intent, requestId } = await parseShellRouteFormData<Record<string, unknown>>(request);
    if (intent !== "update-plugin") {
      return Response.json({ ok: false, error: "Unsupported action", requestId }, { status: 400 });
    }
    const data = await updateShellPluginFromRoute(request, context, shellId, "system-info");
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json({ ok: false, error: message }, { status: error.status || 400 });
    }
    return shellRouteError(error, "System info plugin update failed");
  }
}

export default function ShellInfoRoute() {
  const { pluginStatus, systemInfo, error } = useLoaderData<typeof loader>();
  const navigate = useNavigate();
  const revalidator = useRevalidator();
  const { shell } = useShellManagerContext();
  const [refreshing, setRefreshing] = useState(false);
  const hasSystemInfo = systemInfo != null;

  const handleRefresh = () => {
    setRefreshing(true);
    navigate(`?forceRefresh=${Date.now()}`, { replace: true });
    // refreshing state will reset on re-render from navigation
    setTimeout(() => setRefreshing(false), 500);
  };

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard
        pluginId="system-info"
        pluginName="System Info"
        status={pluginStatus}
        actionPath={`/shells/${shell.id}/info`}
        onUpdated={() => revalidator.revalidate()}
      />
      {error && (
        <div className="shrink-0 rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
          {error}
        </div>
      )}
      {hasSystemInfo && (
        <div className="min-h-0 flex-1 space-y-4 overflow-auto">
          <SystemDashboard
            data={(systemInfo as any)?.data}
            onRefresh={handleRefresh}
            refreshing={refreshing}
          />
        </div>
      )}
    </div>
  );
}
