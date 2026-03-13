import { useMemo } from "react";
import {
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
  useLoaderData,
  useRevalidator,
} from "react-router";

import { createAuthFetch } from "@/api.server";
import { dispatchPlugin } from "@/api/shell-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import FileManager from "@/components/shell/file-manager";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
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

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = parseShellIdParam(params.shellId);
  const authFetch = createAuthFetch(request, context);
  const shell = await getShellConnectionById(shellId, authFetch);
  const initialState = deriveFileManagerInitialState(shell.basicInfo);
  let pluginStatus = await getShellPluginStatusFromRoute(request, context, shellId, "file-manager");
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
  if (!pluginStatus.loaded) {
    pluginStatus = await getShellPluginStatusFromRoute(request, context, shellId, "file-manager");
  }

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
  const { shell } = useShellManagerContext();
  const { initialState, initialDirectory, pluginStatus } = useLoaderData<typeof loader>();
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
        <FileManager
          initialState={initialState}
          initialDirectory={initialDirectory}
          dataPath={actionPath}
        />
      )}
    </div>
  );
}
