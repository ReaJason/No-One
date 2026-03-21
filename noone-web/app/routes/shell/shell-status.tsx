import type { PluginRuntimeStatus } from "@/types/plugin";

import { ArrowUpCircle, CheckCircle2, Circle, Loader2, RefreshCw } from "lucide-react";

import { usePluginStatusContext } from "@/components/shell/plugin-status-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

function StatusIndicator({ status }: { status: PluginRuntimeStatus }) {
  if (!status.loaded) {
    return (
      <div className="flex items-center gap-1.5">
        <Circle className="size-2.5 fill-muted-foreground/40 text-muted-foreground/40" />
        <span className="text-muted-foreground">Not Installed</span>
      </div>
    );
  }
  if (status.needsUpdate) {
    return (
      <div className="flex items-center gap-1.5">
        <Circle className="size-2.5 fill-amber-500 text-amber-500" />
        <span className="text-amber-600 dark:text-amber-400">Update Available</span>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-1.5">
      <Circle className="size-2.5 fill-emerald-500 text-emerald-500" />
      <span className="text-emerald-600 dark:text-emerald-400">Ready</span>
    </div>
  );
}

function PluginUpdateButton({
  status,
  isUpdating,
  onUpdate,
}: {
  status: PluginRuntimeStatus;
  isUpdating: boolean;
  onUpdate: () => void;
}) {
  if (status.loaded && !status.needsUpdate) {
    return (
      <Button variant="ghost" size="sm" disabled className="gap-1.5">
        <CheckCircle2 className="size-3.5" />
        Synced
      </Button>
    );
  }

  return (
    <Button
      variant="outline"
      size="sm"
      onClick={onUpdate}
      disabled={isUpdating}
      className="gap-1.5"
    >
      {isUpdating ? (
        <>
          <Loader2 className="size-3.5 animate-spin" />
          Updating...
        </>
      ) : (
        <>
          <ArrowUpCircle className="size-3.5" />
          {status.loaded ? "Update" : "Install"}
        </>
      )}
    </Button>
  );
}

export default function ShellStatusRoute() {
  const { statuses, loading, refreshAll, updatePlugin, updateAll, updatingIds } =
    usePluginStatusContext();

  const entries = Object.values(statuses);
  const outdatedCount = entries.filter((s) => s.needsUpdate || !s.loaded).length;
  const isUpdatingAny = updatingIds.size > 0;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Plugin Status</h2>
          <p className="text-sm text-muted-foreground">
            {entries.length} {entries.length === 1 ? "plugin" : "plugins"}
            {outdatedCount > 0 && (
              <span className="text-amber-600 dark:text-amber-400">
                {" "}
                &middot; {outdatedCount} need{outdatedCount === 1 ? "s" : ""} update
              </span>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={refreshAll}
            disabled={loading}
            className="gap-1.5"
          >
            <RefreshCw className={`size-3.5 ${loading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
          {outdatedCount > 0 && (
            <Button size="sm" onClick={updateAll} disabled={isUpdatingAny} className="gap-1.5">
              {isUpdatingAny ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" />
                  Updating...
                </>
              ) : (
                <>
                  <ArrowUpCircle className="size-3.5" />
                  Update All ({outdatedCount})
                </>
              )}
            </Button>
          )}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-auto rounded-md border">
        <Table>
          <TableHeader className="sticky top-0 z-10 bg-background">
            <TableRow>
              <TableHead className="w-[200px]">Plugin</TableHead>
              <TableHead className="w-[140px]">Status</TableHead>
              <TableHead className="w-[120px]">Server Version</TableHead>
              <TableHead className="w-[120px]">Shell Version</TableHead>
              <TableHead className="w-[120px] text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && entries.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center">
                  <div className="flex items-center justify-center gap-2 text-muted-foreground">
                    <Loader2 className="size-4 animate-spin" />
                    Loading plugin statuses...
                  </div>
                </TableCell>
              </TableRow>
            ) : entries.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                  No plugins found for this shell.
                </TableCell>
              </TableRow>
            ) : (
              entries.map((status) => (
                <TableRow key={status.pluginId}>
                  <TableCell className="font-medium">{status.pluginId}</TableCell>
                  <TableCell>
                    <StatusIndicator status={status} />
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{status.serverVersion}</Badge>
                  </TableCell>
                  <TableCell>
                    {status.shellVersion ? (
                      <Badge variant={status.needsUpdate ? "secondary" : "outline"}>
                        {status.shellVersion}
                      </Badge>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <PluginUpdateButton
                      status={status}
                      isUpdating={updatingIds.has(status.pluginId)}
                      onUpdate={() => updatePlugin(status.pluginId)}
                    />
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
