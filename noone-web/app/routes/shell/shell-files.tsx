import { lazy, Suspense, use, useMemo } from "react";
import {
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
  useLoaderData,
  useRevalidator,
} from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { dispatchPlugin } from "@/api/shell-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { deriveFileManagerInitialState } from "@/lib/file-manager-initial-state";
import { ensureShellDispatchPayload } from "@/lib/shell-dispatch";
import {
  getShellPluginStatusFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
  updateShellPluginFromRoute,
} from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

const FileManager = lazy(() => import("@/components/shell/file-manager"));

type ShellFilesRouteData = {
  pluginStatus: Awaited<ReturnType<typeof getShellPluginStatusFromRoute>>;
  initialState: ReturnType<typeof deriveFileManagerInitialState>;
  initialDirectory: Record<string, unknown>;
};
type ShellFilesLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

export function loader({ context, params, request }: LoaderFunctionArgs) {
  return {
    routeData: loadShellFilesRouteData({ context, params, request }),
  };
}

async function loadShellFilesRouteData({
  context,
  params,
  request,
}: ShellFilesLoaderArgs): Promise<ShellFilesRouteData> {
  const shellId = parseShellIdParam(params.shellId);
  const authFetch = createAuthFetch(request, context);
  const [shell, initialPluginStatus] = await Promise.all([
    getShellConnectionById(shellId, authFetch),
    getShellPluginStatusFromRoute(request, context, shellId, "file-manager"),
  ]);
  if (!shell) {
    throw new Response("Shell connection not found", { status: 404 });
  }
  const initialState = deriveFileManagerInitialState(shell.basicInfo);
  const initialDirectory = ensureShellDispatchPayload(
    await dispatchPlugin(
      {
        id: shellId,
        pluginId: "file-manager",
        args: { op: "list", path: initialState.cwd },
      },
      authFetch,
    ),
  );
  const pluginStatus = initialPluginStatus.loaded
    ? initialPluginStatus
    : await getShellPluginStatusFromRoute(request, context, shellId, "file-manager");

  return {
    pluginStatus,
    initialState,
    initialDirectory,
  };
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  try {
    const shellId = parseShellIdParam(params.shellId);
    const { intent, requestId } = await parseShellRouteFormData<Record<string, unknown>>(request);
    if (intent !== "update-plugin") {
      return Response.json({ ok: false, error: "Unsupported action", requestId }, { status: 400 });
    }
    const data = await updateShellPluginFromRoute(request, context, shellId, "file-manager");
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json({ ok: false, error: message }, { status: error.status || 400 });
    }
    return shellRouteError(error, "File manager plugin update failed");
  }
}

export default function ShellFilesRoute() {
  const { routeData } = useLoaderData() as {
    routeData: Promise<ShellFilesRouteData>;
  };
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="file-manager" />}>
      <ShellFilesContent routeData={routeData} />
    </Suspense>
  );
}

function ShellFilesContent({ routeData }: { routeData: Promise<ShellFilesRouteData> }) {
  const { shell } = useShellManagerContext();
  const { initialState, initialDirectory, pluginStatus } = use(routeData);
  const revalidator = useRevalidator();
  const actionPath = useMemo(() => `/shells/${shell.id}/files/data`, [shell.id]);

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard
        pluginId="file-manager"
        pluginName="File Manager"
        status={pluginStatus}
        actionPath={`/shells/${shell.id}/files`}
        onUpdated={() => revalidator.revalidate()}
      />
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
            dataPath={actionPath}
          />
        </Suspense>
      )}
    </div>
  );
}
