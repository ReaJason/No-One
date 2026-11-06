import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import SystemDashboard from "@/components/shell/system-info";
import { useShellPluginSnapshot } from "@/hooks/use-shell-plugin-snapshot";

import { useShellManagerContext } from "./shell-manager-context";

export default function ShellInfoRoute() {
  const { shell } = useShellManagerContext();
  const {
    data: systemInfo,
    error,
    loading,
    refresh,
  } = useShellPluginSnapshot({
    shellId: shell.id,
    pluginId: "system-info",
    errorPrefix: "Failed to load system info",
  });
  const hasSystemInfo = systemInfo != null;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <PluginRuntimeStatusCard pluginId="system-info" pluginName="System Info" />
      {error && (
        <div className="shrink-0 rounded-md border border-red-200 bg-red-50 p-4 text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-400">
          {error}
        </div>
      )}
      {loading && !hasSystemInfo && (
        <ShellSectionSkeleton variant="dashboard" showStatusCard={false} />
      )}
      {hasSystemInfo && (
        <div className="min-h-0 flex-1 space-y-4 overflow-auto">
          <SystemDashboard
            data={(systemInfo as any)?.data}
            onRefresh={() => void refresh()}
            refreshing={loading}
          />
        </div>
      )}
    </div>
  );
}
