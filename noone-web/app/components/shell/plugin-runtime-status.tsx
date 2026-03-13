import type { PluginRuntimeStatus } from "@/types/plugin";

import { ArrowUpCircle, CheckCircle2, Loader2, Puzzle } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useShellRouteFetcher } from "@/hooks/use-shell-route-fetcher";
import { buildShellRouteFormData, createShellRouteRequestId } from "@/lib/shell-route";

interface PluginRuntimeStatusProps {
  pluginId: string;
  pluginName: string;
  status: PluginRuntimeStatus;
  actionPath: string;
  onUpdated?: (status: PluginRuntimeStatus) => void;
  showWhenSynced?: boolean;
}

export default function PluginRuntimeStatusCard({
  pluginId,
  pluginName,
  status,
  actionPath,
  onUpdated,
  showWhenSynced = true,
}: PluginRuntimeStatusProps) {
  const { submit, fetcher } = useShellRouteFetcher<PluginRuntimeStatus>();

  if (!showWhenSynced && status.loaded && !status.needsUpdate) {
    return null;
  }

  const buttonLabel = status.loaded ? "Update Plugin" : "Install Plugin";
  const badge = !status.loaded
    ? "Not Installed"
    : status.needsUpdate
      ? "Update Available"
      : "Ready";
  const badgeVariant = !status.loaded ? "outline" : status.needsUpdate ? "secondary" : "secondary";
  const message = status.loaded
    ? status.needsUpdate
      ? "The shell is still running an older plugin version. Updating is optional unless you want to sync to the server copy."
      : "The shell is already synced with the current server plugin."
    : "This shell has not loaded the current plugin yet. Running it will load the current server plugin automatically.";

  return (
    <div className="rounded-xl border border-border/70 bg-muted/20 p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Puzzle className="size-4 text-muted-foreground" />
            <span className="font-medium">{pluginName}</span>
            <Badge variant={badgeVariant}>{badge}</Badge>
          </div>
          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span>Server</span>
            <Badge variant="outline">{status.serverVersion}</Badge>
            <span>Shell</span>
            <Badge variant="outline">{status.shellVersion ?? "Not installed"}</Badge>
          </div>
          <p className="text-sm text-muted-foreground">{message}</p>
        </div>

        {(status.needsUpdate || !status.loaded) && (
          <Button
            type="button"
            onClick={async () => {
              const requestId = createShellRouteRequestId();
              const nextStatus = await submit(
                buildShellRouteFormData("update-plugin", { pluginId }, requestId),
                {
                  method: "post",
                  action: actionPath,
                },
                requestId,
              );
              onUpdated?.(nextStatus);
            }}
            disabled={fetcher.state !== "idle"}
          >
            {fetcher.state !== "idle" ? (
              <>
                <Loader2 className="mr-2 size-4 animate-spin" />
                Updating...
              </>
            ) : (
              <>
                {status.loaded ? (
                  <ArrowUpCircle className="mr-2 size-4" />
                ) : (
                  <CheckCircle2 className="mr-2 size-4" />
                )}
                {buttonLabel}
              </>
            )}
          </Button>
        )}
      </div>
    </div>
  );
}
