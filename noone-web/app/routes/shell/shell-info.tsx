import { Suspense, use, useEffect, useState } from "react";
import { type LoaderFunctionArgs, useLoaderData, useLocation, useNavigate } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import * as shellApi from "@/api/shell-api";
import * as opLogApi from "@/api/shell-operation-log-api";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import SystemDashboard from "@/components/shell/system-info";
import { parseShellIdParam } from "@/lib/shell-route.server";

type ShellInfoRouteData = {
  systemInfo: unknown;
  error: string | null;
};
type ShellInfoLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

function createTraceId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function getRequestKind(url: URL) {
  return url.pathname.endsWith(".data") ? "data" : "document";
}

function logShellInfoDebug(message: string, payload?: Record<string, unknown>) {
  if (!import.meta.env.DEV) return;
  if (payload) {
    console.log(`[shell-info] ${message}`, payload);
    return;
  }
  console.log(`[shell-info] ${message}`);
}

export function loader({ context, params, request }: LoaderFunctionArgs) {
  const traceId = createTraceId();
  const url = new URL(request.url);
  console.count("[shell-info] loader");
  logShellInfoDebug("loader start", {
    traceId,
    kind: getRequestKind(url),
    method: request.method,
    url: request.url,
    accept: request.headers.get("accept"),
    secFetchDest: request.headers.get("sec-fetch-dest"),
    secFetchMode: request.headers.get("sec-fetch-mode"),
    secFetchSite: request.headers.get("sec-fetch-site"),
    referer: request.headers.get("referer"),
    shellId: params.shellId,
  });
  return {
    routeData: loadShellInfoRouteData({ context, params, request }, traceId),
  };
}

const DISPATCH_TIMEOUT_MS = 20_000;

async function loadShellInfoRouteData(
  { context, params, request }: ShellInfoLoaderArgs,
  traceId: string,
): Promise<ShellInfoRouteData> {
  const shellId = parseShellIdParam(params.shellId);
  const url = new URL(request.url);
  const forceRefresh = url.searchParams.has("forceRefresh");
  const startedAt = Date.now();
  logShellInfoDebug("routeData start", {
    traceId,
    kind: getRequestKind(url),
    shellId,
    forceRefresh,
    pathname: url.pathname,
    search: url.search,
  });
  const authFetch = createAuthFetch(request, context);
  const cachedResult = forceRefresh
    ? null
    : await opLogApi.getLatestShellOperation(shellId, "system-info", authFetch).catch(() => null);
  logShellInfoDebug("cache lookup", {
    traceId,
    shellId,
    forceRefresh,
    hit: Boolean(cachedResult?.result),
  });

  try {
    if (cachedResult?.result) {
      logShellInfoDebug("routeData resolved from cache", {
        traceId,
        shellId,
        durationMs: Date.now() - startedAt,
      });
      return { systemInfo: cachedResult.result, error: null };
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), DISPATCH_TIMEOUT_MS);
    try {
      const dispatchStartedAt = Date.now();
      logShellInfoDebug("dispatch start", {
        traceId,
        shellId,
        pluginId: "system-info",
      });
      const data = await shellApi.dispatchPlugin(
        { id: shellId, pluginId: "system-info" },
        authFetch,
        { signal: controller.signal },
      );
      logShellInfoDebug("dispatch success", {
        traceId,
        shellId,
        dispatchDurationMs: Date.now() - dispatchStartedAt,
        totalDurationMs: Date.now() - startedAt,
      });
      return { systemInfo: data, error: null };
    } finally {
      clearTimeout(timeout);
    }
  } catch (err: any) {
    const message =
      err.name === "AbortError"
        ? "Request timed out — the remote shell may be unresponsive"
        : err.message || "Unknown error";
    logShellInfoDebug("routeData failed", {
      traceId,
      shellId,
      errorName: err?.name,
      errorMessage: message,
      durationMs: Date.now() - startedAt,
    });
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
  const location = useLocation();
  console.count("[shell-info] route render");
  logShellInfoDebug("route render", {
    pathname: location.pathname,
    search: location.search,
  });
  useEffect(() => {
    console.count("[shell-info] route commit");
    logShellInfoDebug("route commit", {
      pathname: location.pathname,
      search: location.search,
    });
  }, [location.pathname, location.search]);
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
  console.count("[shell-info] content render");
  logShellInfoDebug("content render", {
    hasSystemInfo,
    hasError: Boolean(error),
    refreshing,
  });
  useEffect(() => {
    console.count("[shell-info] content commit");
    logShellInfoDebug("content commit", {
      hasSystemInfo,
      hasError: Boolean(error),
      refreshing,
    });
  }, [hasSystemInfo, error, refreshing]);
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
