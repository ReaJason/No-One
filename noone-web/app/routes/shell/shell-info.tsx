import { lazy, Suspense, use, useState } from "react";
import { type LoaderFunctionArgs, useLoaderData, useNavigate } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import * as shellApi from "@/api/shell-api";
import * as opLogApi from "@/api/shell-operation-log-api";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { parseShellIdParam } from "@/lib/shell-route.server";

const SystemDashboard = lazy(() => import("@/components/shell/system-info"));

type ShellInfoRouteData = {
  systemInfo: unknown;
  error: string | null;
};
type ShellInfoLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

export function loader({ context, params, request }: LoaderFunctionArgs) {
  return {
    routeData: loadShellInfoRouteData({ context, params, request }),
  };
}

const DISPATCH_TIMEOUT_MS = 20_000;

async function loadShellInfoRouteData({
  context,
  params,
  request,
}: ShellInfoLoaderArgs): Promise<ShellInfoRouteData> {
  const shellId = parseShellIdParam(params.shellId);
  const url = new URL(request.url);
  const forceRefresh = url.searchParams.has("forceRefresh");
  const authFetch = createAuthFetch(request, context);
  const cachedResult = forceRefresh
    ? null
    : await opLogApi.getLatestShellOperation(shellId, "system-info", authFetch).catch(() => null);

  try {
    if (cachedResult?.result) {
      return { systemInfo: cachedResult.result, error: null };
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), DISPATCH_TIMEOUT_MS);
    try {
      const data = await shellApi.dispatchPlugin(
        { id: shellId, pluginId: "system-info" },
        authFetch,
        { signal: controller.signal },
      );
      return { systemInfo: data, error: null };
    } finally {
      clearTimeout(timeout);
    }
  } catch (err: any) {
    const message =
      err.name === "AbortError"
        ? "Request timed out — the remote shell may be unresponsive"
        : err.message || "Unknown error";
    return {
      systemInfo: null,
      error: `Failed to load system info: ${message}`,
    };
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
  const { systemInfo, error } = use(routeData);
  const navigate = useNavigate();

  const [refreshing, setRefreshing] = useState(false);
  const hasSystemInfo = systemInfo != null;

  const handleRefresh = () => {
    setRefreshing(true);
    navigate(`?forceRefresh=${Date.now()}`, { replace: true });
    setTimeout(() => setRefreshing(false), 500);
  };

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard pluginId="system-info" pluginName="System Info" />
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
