import { lazy, Suspense, use, useState } from "react";
import { type LoaderFunctionArgs, useLoaderData, useNavigate } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import * as shellApi from "@/api/shell-api";
import * as opLogApi from "@/api/shell-operation-log-api";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { parseShellIdParam } from "@/lib/shell-route.server";

const ProcessMonitor = lazy(() => import("@/components/shell/process-monitor"));

type ShellProcessesRouteData = {
  processData: unknown;
  error: string | null;
};
type ShellProcessesLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

export function loader({ context, params, request }: LoaderFunctionArgs) {
  return {
    routeData: loadShellProcessesRouteData({ context, params, request }),
  };
}

const DISPATCH_TIMEOUT_MS = 20_000;

async function loadShellProcessesRouteData({
  context,
  params,
  request,
}: ShellProcessesLoaderArgs): Promise<ShellProcessesRouteData> {
  const shellId = parseShellIdParam(params.shellId);
  const url = new URL(request.url);
  const forceRefresh = url.searchParams.has("forceRefresh");
  const authFetch = createAuthFetch(request, context);
  const cachedResult = forceRefresh
    ? null
    : await opLogApi
        .getLatestShellOperation(shellId, "process-monitor", authFetch)
        .catch(() => null);

  try {
    if (cachedResult?.result) {
      return { processData: cachedResult.result, error: null };
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), DISPATCH_TIMEOUT_MS);
    try {
      const data = await shellApi.dispatchPlugin(
        { id: shellId, pluginId: "process-monitor" },
        authFetch,
        { signal: controller.signal },
      );
      return { processData: data, error: null };
    } finally {
      clearTimeout(timeout);
    }
  } catch (err: any) {
    const message =
      err.name === "AbortError"
        ? "Request timed out — the remote shell may be unresponsive"
        : err.message || "Unknown error";
    return {
      processData: null,
      error: `Failed to load process data: ${message}`,
    };
  }
}

export default function ShellProcessesRoute() {
  const { routeData } = useLoaderData() as {
    routeData: Promise<ShellProcessesRouteData>;
  };
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="list" />}>
      <ShellProcessesContent routeData={routeData} />
    </Suspense>
  );
}

function ShellProcessesContent({ routeData }: { routeData: Promise<ShellProcessesRouteData> }) {
  const { processData, error } = use(routeData);
  const navigate = useNavigate();

  const [refreshing, setRefreshing] = useState(false);

  const handleRefresh = () => {
    setRefreshing(true);
    navigate(`?forceRefresh=${Date.now()}`, { replace: true });
    setTimeout(() => setRefreshing(false), 500);
  };

  const data = (processData as any)?.data;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard pluginId="process-monitor" pluginName="Process Monitor" />
      {error && (
        <div className="shrink-0 rounded-md border border-red-200 bg-red-50 p-4 text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-400">
          {error}
        </div>
      )}
      {data && (
        <div className="min-h-0 flex-1 overflow-hidden">
          <Suspense
            fallback={
              <ShellSectionSkeleton
                label="Loading process monitor"
                variant="list"
                showStatusCard={false}
              />
            }
          >
            <ProcessMonitor data={data} onRefresh={handleRefresh} refreshing={refreshing} />
          </Suspense>
        </div>
      )}
    </div>
  );
}
