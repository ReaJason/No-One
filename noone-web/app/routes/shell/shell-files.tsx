import type { FileManagerInitialState } from "@/lib/file-manager-initial-state";

import { lazy, Suspense, use, useEffect, useMemo } from "react";
import { type LoaderFunctionArgs, useLoaderData, useLocation } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { dispatchPlugin } from "@/api/shell-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { deriveFileManagerInitialState } from "@/lib/file-manager-initial-state";
import { ensureShellDispatchPayload } from "@/lib/shell-dispatch";
import {
  clearShellNavigationTrace,
  createShellRouteTraceId,
  getShellNavigationDurationMs,
  getShellRouteRequestKind,
  logShellRouteDebug,
  readShellNavigationTrace,
} from "@/lib/shell-route-debug";
import { parseShellIdParam } from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

const FileManager = lazy(async () => {
  const startedAt = Date.now();
  logShellRouteDebug("shell-files", "file-manager chunk import start");
  const mod = await import("@/components/shell/file-manager");
  logShellRouteDebug("shell-files", "file-manager chunk import resolved", {
    durationMs: Date.now() - startedAt,
  });
  return mod;
});

const DISPATCH_TIMEOUT_MS = 20_000;

type ShellFilesRouteData = {
  initialState: FileManagerInitialState;
  initialDirectory: Record<string, unknown> | null;
  error: string | null;
};

export function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = parseShellIdParam(params.shellId);
  const traceId = createShellRouteTraceId();
  const url = new URL(request.url);
  logShellRouteDebug("shell-files", "loader start", {
    traceId,
    shellId,
    kind: getShellRouteRequestKind(url),
    method: request.method,
    url: request.url,
  });
  const authFetch = createAuthFetch(request, context);
  return {
    routeData: loadShellFilesRouteData({ shellId, authFetch, traceId, request }),
  };
}

async function loadShellFilesRouteData({
  shellId,
  authFetch,
  traceId,
  request,
}: {
  shellId: number;
  authFetch: ReturnType<typeof createAuthFetch>;
  traceId: string;
  request: Request;
}): Promise<ShellFilesRouteData> {
  const startedAt = Date.now();
  const url = new URL(request.url);
  logShellRouteDebug("shell-files", "routeData start", {
    traceId,
    shellId,
    kind: getShellRouteRequestKind(url),
    pathname: url.pathname,
    search: url.search,
  });
  const shellLookupStartedAt = Date.now();
  const shell = await getShellConnectionById(shellId, authFetch);
  if (!shell) {
    throw new Response("Shell connection not found", { status: 404 });
  }
  logShellRouteDebug("shell-files", "shell lookup resolved", {
    traceId,
    shellId,
    durationMs: Date.now() - shellLookupStartedAt,
  });
  const initialState = deriveFileManagerInitialState(shell.basicInfo, shell.os);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), DISPATCH_TIMEOUT_MS);
  try {
    const dispatchStartedAt = Date.now();
    logShellRouteDebug("shell-files", "initial directory dispatch start", {
      traceId,
      shellId,
      cwd: initialState.cwd,
    });
    const initialDirectory = ensureShellDispatchPayload(
      await dispatchPlugin(
        { id: shellId, pluginId: "file-manager", args: { op: "list", path: initialState.cwd } },
        authFetch,
        { signal: controller.signal },
      ),
    );
    logShellRouteDebug("shell-files", "initial directory dispatch resolved", {
      traceId,
      shellId,
      dispatchDurationMs: Date.now() - dispatchStartedAt,
      totalDurationMs: Date.now() - startedAt,
    });
    return { initialState, initialDirectory, error: null };
  } catch (err: any) {
    const message =
      err.name === "AbortError"
        ? "Request timed out — the remote shell may be unresponsive"
        : err.message || "Unknown error";
    logShellRouteDebug("shell-files", "routeData failed", {
      traceId,
      shellId,
      errorName: err?.name,
      errorMessage: message,
      totalDurationMs: Date.now() - startedAt,
    });
    return {
      initialState,
      initialDirectory: null,
      error: `Failed to load file manager: ${message}`,
    };
  } finally {
    clearTimeout(timeout);
  }
}

export default function ShellFilesRoute() {
  const { routeData } = useLoaderData() as {
    routeData: Promise<ShellFilesRouteData>;
  };
  const location = useLocation();
  useEffect(() => {
    const trace = readShellNavigationTrace(location.pathname);
    logShellRouteDebug("shell-files", "route commit", {
      traceId: trace?.traceId,
      pathname: location.pathname,
      search: location.search,
      navDurationMs: getShellNavigationDurationMs(location.pathname),
    });
  }, [location.pathname, location.search]);
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="file-manager" />}>
      <ShellFilesContent routeData={routeData} />
    </Suspense>
  );
}

function ShellFilesContent({ routeData }: { routeData: Promise<ShellFilesRouteData> }) {
  const { shell } = useShellManagerContext();
  const location = useLocation();
  const { initialState, initialDirectory, error } = use(routeData);
  const dataPath = useMemo(() => `/shells/${shell.id}/files/data`, [shell.id]);
  useEffect(() => {
    const trace = readShellNavigationTrace(location.pathname);
    logShellRouteDebug("shell-files", "content commit", {
      traceId: trace?.traceId,
      pathname: location.pathname,
      hasInitialDirectory: Boolean(initialDirectory),
      hasError: Boolean(error),
      navDurationMs: getShellNavigationDurationMs(location.pathname),
    });
    clearShellNavigationTrace(location.pathname);
  }, [error, initialDirectory, location.pathname]);

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard pluginId="file-manager" pluginName="File Manager" />
      {error && (
        <div className="shrink-0 rounded-md border border-red-200 bg-red-50 p-4 text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-400">
          {error}
        </div>
      )}
      {initialDirectory && (
        <Suspense
          fallback={
            <ShellSectionSkeleton
              label="Loading file manager"
              variant="file-manager"
              showStatusCard={false}
            />
          }
        >
          <FileManager
            initialState={initialState}
            initialDirectory={initialDirectory}
            dataPath={dataPath}
          />
        </Suspense>
      )}
    </div>
  );
}
