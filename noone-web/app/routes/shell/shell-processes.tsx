import { lazy, Suspense } from "react";

import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { useShellPluginSnapshot } from "@/hooks/use-shell-plugin-snapshot";

import { useShellManagerContext } from "./shell-manager-context";

const ProcessMonitor = lazy(() => import("@/components/shell/process-monitor"));

export default function ShellProcessesRoute() {
  const { shell } = useShellManagerContext();
  const {
    data: processData,
    error,
    loading,
    refresh,
  } = useShellPluginSnapshot({
    shellId: shell.id,
    pluginId: "process-monitor",
    errorPrefix: "Failed to load process data",
  });
  const data = (processData as any)?.data;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard pluginId="process-monitor" pluginName="Process Monitor" />
      {error && (
        <div className="shrink-0 rounded-md border border-red-200 bg-red-50 p-4 text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-400">
          {error}
        </div>
      )}
      {loading && !data && <ShellSectionSkeleton variant="list" showStatusCard={false} />}
      {data && (
        <div className="min-h-0 flex-1 overflow-hidden">
          <Suspense
            fallback={
              <ShellSectionSkeleton
                label="Loading process monitor"
                variant="list"
                showStatusCard={false}
              />
            }
          >
            <ProcessMonitor data={data} onRefresh={() => void refresh()} refreshing={loading} />
          </Suspense>
        </div>
      )}
    </div>
  );
}
