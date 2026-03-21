import type { FileManagerInitialState } from "@/lib/file-manager-initial-state";

import { lazy, Suspense, use, useMemo } from "react";
import { type LoaderFunctionArgs, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { dispatchPlugin } from "@/api/shell-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { deriveFileManagerInitialState } from "@/lib/file-manager-initial-state";
import { ensureShellDispatchPayload } from "@/lib/shell-dispatch";
import { parseShellIdParam } from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

const FileManager = lazy(() => import("@/components/shell/file-manager"));

const DISPATCH_TIMEOUT_MS = 20_000;

type ShellFilesRouteData = {
  initialState: FileManagerInitialState;
  initialDirectory: Record<string, unknown> | null;
  error: string | null;
};

export function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = parseShellIdParam(params.shellId);
  const authFetch = createAuthFetch(request, context);
  return {
    routeData: loadShellFilesRouteData(shellId, authFetch),
  };
}

async function loadShellFilesRouteData(
  shellId: number,
  authFetch: ReturnType<typeof createAuthFetch>,
): Promise<ShellFilesRouteData> {
  const shell = await getShellConnectionById(shellId, authFetch);
  if (!shell) {
    throw new Response("Shell connection not found", { status: 404 });
  }
  const initialState = deriveFileManagerInitialState(shell.basicInfo, shell.os);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), DISPATCH_TIMEOUT_MS);
  try {
    const initialDirectory = ensureShellDispatchPayload(
      await dispatchPlugin(
        { id: shellId, pluginId: "file-manager", args: { op: "list", path: initialState.cwd } },
        authFetch,
        { signal: controller.signal },
      ),
    );
    return { initialState, initialDirectory, error: null };
  } catch (err: any) {
    const message =
      err.name === "AbortError"
        ? "Request timed out — the remote shell may be unresponsive"
        : err.message || "Unknown error";
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
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="file-manager" />}>
      <ShellFilesContent routeData={routeData} />
    </Suspense>
  );
}

function ShellFilesContent({ routeData }: { routeData: Promise<ShellFilesRouteData> }) {
  const { shell } = useShellManagerContext();
  const { initialState, initialDirectory, error } = use(routeData);
  const dataPath = useMemo(() => `/shells/${shell.id}/files/data`, [shell.id]);

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
