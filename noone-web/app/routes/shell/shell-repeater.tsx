import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { lazy, Suspense, use } from "react";
import { useLoaderData } from "react-router";

import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import {
  dispatchShellPluginFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
} from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

const HttpRepeater = lazy(() => import("@/components/shell/http-repeater"));

type ShellRepeaterRouteData = Record<string, never>;
type ShellRepeaterLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

export function loader({ context, params, request }: LoaderFunctionArgs) {
  return {
    routeData: loadShellRepeaterRouteData({ context, params, request }),
  };
}

async function loadShellRepeaterRouteData({
  params,
}: ShellRepeaterLoaderArgs): Promise<ShellRepeaterRouteData> {
  parseShellIdParam(params.shellId);
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
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="dashboard" />}>
      <ShellRepeaterContent routeData={routeData} />
    </Suspense>
  );
}

function ShellRepeaterContent({ routeData }: { routeData: Promise<ShellRepeaterRouteData> }) {
  const { shell } = useShellManagerContext();
  use(routeData);

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
