import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { lazy, Suspense, use, useEffect } from "react";
import { useLoaderData, useLocation, useRevalidator } from "react-router";

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

const CommandExecute = lazy(async () => {
  const startedAt = Date.now();
  logShellRouteDebug("shell-command", "command chunk import start");
  const mod = await import("@/components/shell/command-execute");
  logShellRouteDebug("shell-command", "command chunk import resolved", {
    durationMs: Date.now() - startedAt,
  });
  return mod;
});

type ShellCommandRouteData = Record<string, never>;
type ShellCommandLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

export function loader({ context, params, request }: LoaderFunctionArgs) {
  const traceId = createShellRouteTraceId();
  const url = new URL(request.url);
  logShellRouteDebug("shell-command", "loader start", {
    traceId,
    shellId: params.shellId,
    kind: getShellRouteRequestKind(url),
    method: request.method,
    url: request.url,
  });
  return {
    routeData: loadShellCommandRouteData({ context, params, request }, traceId),
  };
}

async function loadShellCommandRouteData(
  { params, request }: ShellCommandLoaderArgs,
  traceId: string,
): Promise<ShellCommandRouteData> {
  const startedAt = Date.now();
  const url = new URL(request.url);
  logShellRouteDebug("shell-command", "routeData start", {
    traceId,
    shellId: params.shellId,
    kind: getShellRouteRequestKind(url),
    pathname: url.pathname,
    search: url.search,
  });
  parseShellIdParam(params.shellId);
  logShellRouteDebug("shell-command", "routeData resolved", {
    traceId,
    shellId: params.shellId,
    totalDurationMs: Date.now() - startedAt,
  });
  return {};
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  try {
    const shellId = parseShellIdParam(params.shellId);
    const { intent, payload, requestId } =
      await parseShellRouteFormData<Record<string, unknown>>(request);
    if (intent !== "run-command") {
      return Response.json({ ok: false, error: "Unsupported action", requestId }, { status: 400 });
    }

    const data = await dispatchShellPluginFromRoute(request, context, shellId, {
      pluginId: "command-execute",
      args: payload,
    });
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json({ ok: false, error: message }, { status: error.status || 400 });
    }
    return shellRouteError(error, "Command execution failed");
  }
}

export default function ShellCommandRoute() {
  const { routeData } = useLoaderData() as {
    routeData: Promise<ShellCommandRouteData>;
  };
  const location = useLocation();
  useEffect(() => {
    const trace = readShellNavigationTrace(location.pathname);
    logShellRouteDebug("shell-command", "route commit", {
      traceId: trace?.traceId,
      pathname: location.pathname,
      search: location.search,
      navDurationMs: getShellNavigationDurationMs(location.pathname),
    });
  }, [location.pathname, location.search]);
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="command" />}>
      <ShellCommandContent routeData={routeData} />
    </Suspense>
  );
}

function ShellCommandContent({ routeData }: { routeData: Promise<ShellCommandRouteData> }) {
  const { shell } = useShellManagerContext();
  const location = useLocation();
  use(routeData);
  const revalidator = useRevalidator();
  useEffect(() => {
    const trace = readShellNavigationTrace(location.pathname);
    logShellRouteDebug("shell-command", "content commit", {
      traceId: trace?.traceId,
      pathname: location.pathname,
      navDurationMs: getShellNavigationDurationMs(location.pathname),
    });
    clearShellNavigationTrace(location.pathname);
  }, [location.pathname]);
  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard pluginId="command-execute" pluginName="Command Execute" />
      <Suspense
        fallback={
          <ShellSectionSkeleton
            label="Loading command console"
            variant="command"
            showStatusCard={false}
          />
        }
      >
        <CommandExecute
          shellId={shell.id}
          osName={shell.os}
          cwdHint={shell.basicInfo?.process?.cwd}
          actionPath={`/shells/${shell.id}/command`}
          onExecuted={() => revalidator.revalidate()}
        />
      </Suspense>
    </div>
  );
}
