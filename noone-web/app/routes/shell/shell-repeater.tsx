import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { lazy, Suspense, use, useEffect } from "react";
import { useLoaderData, useLocation } from "react-router";

import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import {
  clearShellNavigationTrace,
  createShellRouteTraceId,
  getShellNavigationDurationMs,
  getShellRouteRequestKind,
  logShellRouteDebug,
  readShellNavigationTrace,
} from "@/lib/shell-route-debug";
import {
  dispatchShellPluginFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
} from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

const HttpRepeater = lazy(async () => {
  const startedAt = Date.now();
  logShellRouteDebug("shell-repeater", "repeater chunk import start");
  const mod = await import("@/components/shell/http-repeater");
  logShellRouteDebug("shell-repeater", "repeater chunk import resolved", {
    durationMs: Date.now() - startedAt,
  });
  return mod;
});

type ShellRepeaterRouteData = Record<string, never>;
type ShellRepeaterLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

export function loader({ context, params, request }: LoaderFunctionArgs) {
  const traceId = createShellRouteTraceId();
  const url = new URL(request.url);
  logShellRouteDebug("shell-repeater", "loader start", {
    traceId,
    shellId: params.shellId,
    kind: getShellRouteRequestKind(url),
    method: request.method,
    url: request.url,
  });
  return {
    routeData: loadShellRepeaterRouteData({ context, params, request }, traceId),
  };
}

async function loadShellRepeaterRouteData(
  { params, request }: ShellRepeaterLoaderArgs,
  traceId: string,
): Promise<ShellRepeaterRouteData> {
  const startedAt = Date.now();
  const url = new URL(request.url);
  logShellRouteDebug("shell-repeater", "routeData start", {
    traceId,
    shellId: params.shellId,
    kind: getShellRouteRequestKind(url),
    pathname: url.pathname,
    search: url.search,
  });
  parseShellIdParam(params.shellId);
  logShellRouteDebug("shell-repeater", "routeData resolved", {
    traceId,
    shellId: params.shellId,
    totalDurationMs: Date.now() - startedAt,
  });
  return {};
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  let requestId: string | undefined;
  try {
    const shellId = parseShellIdParam(params.shellId);
    const parsed = await parseShellRouteFormData<Record<string, unknown>>(request);
    requestId = parsed.requestId;

    if (parsed.intent !== "send-request") {
      return Response.json({ ok: false, error: "Unsupported action", requestId }, { status: 400 });
    }

    const data = await dispatchShellPluginFromRoute(request, context, shellId, {
      pluginId: "http-repeater",
      args: parsed.payload,
    });
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json(
        { ok: false, error: message, requestId },
        { status: error.status || 400 },
      );
    }
    return shellRouteError(error, "HTTP request failed", requestId);
  }
}

export default function ShellRepeaterRoute() {
  const { routeData } = useLoaderData() as {
    routeData: Promise<ShellRepeaterRouteData>;
  };
  const location = useLocation();
  useEffect(() => {
    const trace = readShellNavigationTrace(location.pathname);
    logShellRouteDebug("shell-repeater", "route commit", {
      traceId: trace?.traceId,
      pathname: location.pathname,
      search: location.search,
      navDurationMs: getShellNavigationDurationMs(location.pathname),
    });
  }, [location.pathname, location.search]);
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="dashboard" />}>
      <ShellRepeaterContent routeData={routeData} />
    </Suspense>
  );
}

function ShellRepeaterContent({ routeData }: { routeData: Promise<ShellRepeaterRouteData> }) {
  const { shell } = useShellManagerContext();
  const location = useLocation();
  use(routeData);
  useEffect(() => {
    const trace = readShellNavigationTrace(location.pathname);
    logShellRouteDebug("shell-repeater", "content commit", {
      traceId: trace?.traceId,
      pathname: location.pathname,
      navDurationMs: getShellNavigationDurationMs(location.pathname),
    });
    clearShellNavigationTrace(location.pathname);
  }, [location.pathname]);

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard pluginId="http-repeater" pluginName="HTTP Repeater" />
      <Suspense
        fallback={
          <ShellSectionSkeleton
            label="Loading HTTP repeater"
            variant="dashboard"
            showStatusCard={false}
          />
        }
      >
        <HttpRepeater shellId={shell.id} actionPath={`/shells/${shell.id}/repeater`} />
      </Suspense>
    </div>
  );
}
