import type { ActionFunctionArgs } from "react-router";
import CommandExecute from "@/components/shell/command-execute";
import {
  dispatchShellPluginFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
} from "@/lib/shell-route.server";
import { useShellManagerContext } from "./shell-manager-context";

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
  const { shell } = useShellManagerContext();
  const os = shell.basicInfo?.os;
  const process = shell.basicInfo?.process;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <CommandExecute
        shellId={shell.id}
        osName={os.name}
        cwdHint={process.cwd}
        actionPath={`/shells/${shell.id}/command`}
      />
    </div>
  );
}
