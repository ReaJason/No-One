import { lazy, Suspense, use, useState } from "react";
import {
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
  useLoaderData,
  useNavigate,
  useRevalidator,
} from "react-router";

import { createAuthFetch } from "@/api/api.server";
import * as shellApi from "@/api/shell-api";
import * as opLogApi from "@/api/shell-operation-log-api";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import {
  getShellPluginStatusFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
  updateShellPluginFromRoute,
} from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

const SystemDashboard = lazy(() => import("@/components/shell/system-info"));

type ShellInfoRouteData = {
  pluginStatus: Awaited<ReturnType<typeof getShellPluginStatusFromRoute>>;
  systemInfo: unknown;
  error: string | null;
};
type ShellInfoLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

export function loader({ context, params, request }: LoaderFunctionArgs) {
  return {
    routeData: loadShellInfoRouteData({ context, params, request }),
  };
}

async function loadShellInfoRouteData({
  context,
  params,
  request,
}: ShellInfoLoaderArgs): Promise<ShellInfoRouteData> {
  const shellId = parseShellIdParam(params.shellId);
  const url = new URL(request.url);
  const forceRefresh = url.searchParams.has("forceRefresh");
  const authFetch = createAuthFetch(request, context);
  const pluginStatusPromise = getShellPluginStatusFromRoute(
    request,
    context,
    shellId,
    "system-info",
  );
  const cachedResultPromise = forceRefresh
    ? Promise.resolve(null)
    : opLogApi.getLatestShellOperation(shellId, "system-info", authFetch).catch(() => null);
  let [pluginStatus, cached] = await Promise.all([pluginStatusPromise, cachedResultPromise]);

  try {
    if (cached?.result) {
      return { pluginStatus, systemInfo: cached.result, error: null };
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
  const { routeData } = useLoaderData() as {
    routeData: Promise<ShellInfoRouteData>;
  };
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="dashboard" />}>
      <ShellInfoContent routeData={routeData} />
    </Suspense>
  );
}

function ShellInfoContent({ routeData }: { routeData: Promise<ShellInfoRouteData> }) {
  const { pluginStatus, systemInfo, error } = use(routeData);
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
          <Suspense
            fallback={
              <ShellSectionSkeleton
                label="Loading system dashboard"
                variant="dashboard"
                showStatusCard={false}
              />
            }
          >
            <SystemDashboard
              data={(systemInfo as any)?.data}
              onRefresh={handleRefresh}
              refreshing={refreshing}
            />
          </Suspense>
        </div>
      )}
    </div>
  );
}
