import { lazy, Suspense, useEffect, useMemo } from "react";
import { useLocation } from "react-router";

import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { deriveFileManagerInitialState } from "@/lib/file-manager-initial-state";
import { clearShellNavigationTrace } from "@/lib/shell-route-debug";

import { useShellManagerContext } from "./shell-manager-context";

const FileManager = lazy(() => import("@/components/shell/file-manager"));

export default function ShellFilesRoute() {
  const { shell } = useShellManagerContext();
  const location = useLocation();
  const initialState = useMemo(
    () => deriveFileManagerInitialState(shell.basicInfo, shell.os),
    [shell.basicInfo, shell.os],
  );
  const dataPath = useMemo(() => `/shells/${shell.id}/files/data`, [shell.id]);

  useEffect(() => {
    clearShellNavigationTrace(location.pathname);
  }, [location.pathname]);

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard pluginId="file-manager" pluginName="File Manager" />
      <Suspense
        fallback={
          <ShellSectionSkeleton
            label="Loading file manager"
            variant="file-manager"
            showStatusCard={false}
          />
        }
      >
        <FileManager initialState={initialState} initialDirectory={null} dataPath={dataPath} />
      </Suspense>
    </div>
  );
}
