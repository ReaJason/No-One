import type { PluginRuntimeStatus } from "@/types/plugin";

import { ArrowUpCircle, CheckCircle2, ChevronDown, Loader2, Puzzle } from "lucide-react";
import { useState } from "react";

import {
  usePluginAutoLoad,
  usePluginStatusContextOptional,
} from "@/components/shell/plugin-status-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";

interface PluginRuntimeStatusProps {
  pluginId: string;
  pluginName: string;
  status?: PluginRuntimeStatus;
  actionPath?: string;
  onUpdated?: (status: PluginRuntimeStatus) => void;
  showWhenSynced?: boolean;
}

export default function PluginRuntimeStatusCard({
  pluginId,
  pluginName,
  status: statusProp,
  actionPath,
  onUpdated,
  showWhenSynced = true,
}: PluginRuntimeStatusProps) {
  const ctx = usePluginStatusContextOptional();
  const isAutoLoading = usePluginAutoLoad(pluginId);
  const status = ctx?.statuses[pluginId] ?? statusProp;
  const isUpdating = ctx?.updatingIds.has(pluginId) ?? false;

  if (!status) return null;

  const isSynced = status.loaded && !status.needsUpdate;
  if (!showWhenSynced && isSynced) return null;

  const needsAction = status.needsUpdate || !status.loaded;

  const handleUpdate = async () => {
    if (ctx) {
      await ctx.updatePlugin(pluginId);
      if (ctx.statuses[pluginId]) {
        onUpdated?.(ctx.statuses[pluginId]);
      }
    }
  };

  if (isSynced) {
    return <CompactStatusCard pluginName={pluginName} status={status} />;
  }

  return (
    <ExpandedStatusCard
      pluginName={pluginName}
      status={status}
      isUpdating={isUpdating}
      isAutoLoading={isAutoLoading}
      onUpdate={needsAction ? handleUpdate : undefined}
    />
  );
}

function CompactStatusCard({
  pluginName,
  status,
}: {
  pluginName: string;
  status: PluginRuntimeStatus;
}) {
  const [open, setOpen] = useState(false);

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="flex w-full items-center gap-2 rounded-lg border border-border/50 bg-muted/10 px-3 py-2 text-sm transition-colors hover:bg-muted/30">
        <Puzzle className="size-3.5 text-muted-foreground" />
        <span className="font-medium">{pluginName}</span>
        <Badge variant="secondary" className="ml-auto text-[10px]">
          Ready
        </Badge>
        <ChevronDown
          className={`size-3.5 text-muted-foreground transition-transform ${open ? "rotate-180" : ""}`}
        />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="mt-1 rounded-b-lg border border-t-0 border-border/50 bg-muted/10 px-3 py-2">
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span>Server</span>
            <Badge variant="outline" className="text-[10px]">
              {status.serverVersion}
            </Badge>
            <span>Shell</span>
            <Badge variant="outline" className="text-[10px]">
              {status.shellVersion ?? "N/A"}
            </Badge>
          </div>
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

function ExpandedStatusCard({
  pluginName,
  status,
  isUpdating,
  isAutoLoading,
  onUpdate,
}: {
  pluginName: string;
  status: PluginRuntimeStatus;
  isUpdating: boolean;
  isAutoLoading: boolean;
  onUpdate?: () => void;
}) {
  const installing = !status.loaded && (isAutoLoading || isUpdating);
  const badge = !status.loaded ? (installing ? "Installing" : "Not Installed") : "Update Available";
  const buttonLabel = status.loaded
    ? "Update Plugin"
    : installing
      ? "Installing..."
      : "Install Plugin";

  return (
    <div className="rounded-xl border border-border/70 bg-muted/20 p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-1.5">
          <div className="flex items-center gap-2">
            <Puzzle className="size-4 text-muted-foreground" />
            <span className="font-medium">{pluginName}</span>
            <Badge variant={status.loaded ? "secondary" : "outline"}>{badge}</Badge>
          </div>
          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span>Server</span>
            <Badge variant="outline">{status.serverVersion}</Badge>
            <span>Shell</span>
            <Badge variant="outline">{status.shellVersion ?? "Not installed"}</Badge>
          </div>
        </div>

        {onUpdate && (
          <Button type="button" size="sm" onClick={onUpdate} disabled={isUpdating || installing}>
            {isUpdating || installing ? (
              <>
                <Loader2 className="mr-1.5 size-3.5 animate-spin" />
                {status.loaded ? "Updating..." : buttonLabel}
              </>
            ) : (
              <>
                {status.loaded ? (
                  <ArrowUpCircle className="mr-1.5 size-3.5" />
                ) : (
                  <CheckCircle2 className="mr-1.5 size-3.5" />
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
