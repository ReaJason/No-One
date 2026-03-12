import { useMemo } from "react";
import { type LoaderFunctionArgs, useLoaderData } from "react-router";
import { createAuthFetch } from "@/api.server";
import { dispatchPlugin } from "@/api/shell-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import FileManager from "@/components/shell/file-manager";
import { deriveFileManagerInitialState } from "@/lib/file-manager-initial-state";
import { ensureShellDispatchPayload } from "@/lib/shell-dispatch";
import { parseShellIdParam } from "@/lib/shell-route.server";
import { useShellManagerContext } from "./shell-manager-context";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = parseShellIdParam(params.shellId);
  const authFetch = createAuthFetch(request, context);
  const shell = await getShellConnectionById(shellId, authFetch);
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

  return {
    initialState,
    initialDirectory,
  };
}

export default function ShellFilesRoute() {
  const { shell } = useShellManagerContext();
  const { initialState, initialDirectory } = useLoaderData<typeof loader>();
  const actionPath = useMemo(() => `/shells/${shell.id}/files/data`, [shell.id]);

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <FileManager
        initialState={initialState}
        initialDirectory={initialDirectory}
        dataPath={actionPath}
      />
    </div>
  );
}
