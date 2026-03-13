import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { useLoaderData, useRevalidator } from "react-router";

import CommandExecute from "@/components/shell/command-execute";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import {
  dispatchShellPluginFromRoute,
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
  const pluginStatus = await getShellPluginStatusFromRoute(
    request,
    context,
    shellId,
    "command-execute",
  );
  return { pluginStatus };
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  try {
    const shellId = parseShellIdParam(params.shellId);
    const { intent, payload, requestId } =
      await parseShellRouteFormData<Record<string, unknown>>(request);
    if (intent !== "run-command" && intent !== "update-plugin") {
      return Response.json({ ok: false, error: "Unsupported action", requestId }, { status: 400 });
    }

    if (intent === "update-plugin") {
      const data = await updateShellPluginFromRoute(request, context, shellId, "command-execute");
      return shellRouteSuccess(data, requestId);
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
  const { shell } = useShellManagerContext();
  const { pluginStatus } = useLoaderData<typeof loader>();
  const revalidator = useRevalidator();
  const os = shell.basicInfo?.os;
  const process = shell.basicInfo?.process;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard
        pluginId="command-execute"
        pluginName="Command Execute"
        status={pluginStatus}
        actionPath={`/shells/${shell.id}/command`}
        onUpdated={() => revalidator.revalidate()}
      />
      <CommandExecute
        shellId={shell.id}
        osName={os.name}
        cwdHint={process.cwd}
        actionPath={`/shells/${shell.id}/command`}
        onExecuted={() => revalidator.revalidate()}
      />
    </div>
  );
}
