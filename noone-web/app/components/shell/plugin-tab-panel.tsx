import type { Plugin, TaskStatus } from "@/types/plugin";

import {
  ArrowUpCircle,
  CheckCircle2,
  Clock,
  Loader2,
  Play,
  Puzzle,
  RefreshCw,
  Square,
} from "lucide-react";
import { useCallback, useEffect, useRef } from "react";

import { usePluginStatusContext } from "@/components/shell/plugin-status-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useShellRouteFetcher } from "@/hooks/use-shell-route-fetcher";
import { buildShellRouteFormData, createShellRouteRequestId } from "@/lib/shell-route";

export interface PluginTabState {
  selectedAction: string;
  args: Record<string, string>;
  result: string | null;
  loading: boolean;
  taskId?: string;
  taskStatus?: TaskStatus;
  polling?: boolean;
}

interface PluginTabPanelProps {
  plugin: Plugin;
  actionPath: string;
  taskStatusPath: string;
  pluginStatusPath: string;
  state: PluginTabState;
  onStateChange: (update: Partial<PluginTabState>) => void;
}

const TERMINAL_STATUSES: TaskStatus[] = ["COMPLETED", "FAILED", "CANCELLED"];

function isAsyncMode(plugin: Plugin): boolean {
  return plugin.runMode === "async";
}

function isScheduledMode(plugin: Plugin): boolean {
  return plugin.runMode === "scheduled";
}

function isAsyncLike(plugin: Plugin): boolean {
  return isAsyncMode(plugin) || isScheduledMode(plugin);
}

function formatResult(data: any): string {
  if (!data) return "";
  if (data.error) return `Error: ${data.error}`;
  if (data.text) return data.text;
  if (data.lines) return data.lines;
  if (typeof data === "string") return data;
  return JSON.stringify(data, null, 2);
}

export default function PluginTabPanel({
  plugin,
  actionPath,
  taskStatusPath,
  state,
  onStateChange,
}: PluginTabPanelProps) {
  const actions = plugin.actions ?? {};
  const actionKeys = Object.keys(actions);
  const currentAction = actions[state.selectedAction];
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const { submit: submitPluginAction } = useShellRouteFetcher<Record<string, unknown>>();
  const { fetcher: taskStatusFetcher, load: loadTaskStatus } =
    useShellRouteFetcher<Record<string, unknown>>();
  const pluginStatusCtx = usePluginStatusContext();

  const refreshPluginStatus = useCallback(async () => {
    await pluginStatusCtx.refreshOne(plugin.id);
  }, [pluginStatusCtx, plugin.id]);

  const handleActionSelect = (action: string | null) => {
    if (!action) return;
    stopPolling();
    onStateChange({
      selectedAction: action,
      args: {},
      result: null,
      taskId: undefined,
      taskStatus: undefined,
      polling: false,
    });
  };

  const stopPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  }, []);

  useEffect(() => {
    return () => stopPolling();
  }, [stopPolling]);

  const pollTaskStatus = useCallback(
    async (taskId: string) => {
      try {
        if (taskStatusFetcher.state !== "idle") {
          return;
        }
        const requestId = createShellRouteRequestId();
        const url = new URL(taskStatusPath, window.location.origin);
        url.searchParams.set("pluginId", plugin.id);
        url.searchParams.set("taskId", taskId);
        url.searchParams.set("requestId", requestId);
        const taskResult = await loadTaskStatus(`${url.pathname}${url.search}`, requestId);
        const status = taskResult?.status as TaskStatus | undefined;
        const partialResult = taskResult?.partialResult;
        const finalResult = taskResult?.result;
        const lastResult = taskResult?.lastResult;

        let displayResult: string | null = null;
        if (finalResult) {
          displayResult = formatResult(finalResult);
        } else if (partialResult) {
          displayResult = formatResult(partialResult);
        } else if (lastResult) {
          displayResult = formatResult(lastResult);
        }

        const isTerminal = status && TERMINAL_STATUSES.includes(status);
        onStateChange({
          taskStatus: status,
          result: displayResult,
          loading: !isTerminal,
          polling: !isTerminal,
        });

        if (isTerminal) {
          stopPolling();
        }
      } catch {
        stopPolling();
        onStateChange({ polling: false, loading: false });
      }
    },
    [
      loadTaskStatus,
      onStateChange,
      plugin.id,
      stopPolling,
      taskStatusFetcher.state,
      taskStatusPath,
    ],
  );

  const startPolling = useCallback(
    (taskId: string) => {
      stopPolling();
      onStateChange({ polling: true });
      pollingRef.current = setInterval(() => pollTaskStatus(taskId), 1500);
      pollTaskStatus(taskId);
    },
    [pollTaskStatus, stopPolling, onStateChange],
  );

  const executePlugin = useCallback(async () => {
    if (!state.selectedAction) return;
    stopPolling();
    onStateChange({
      loading: true,
      result: null,
      taskId: undefined,
      taskStatus: undefined,
      polling: false,
    });
    try {
      const requestId = createShellRouteRequestId();
      const result = await submitPluginAction(
        buildShellRouteFormData(
          "execute-plugin",
          {
            pluginId: plugin.id,
            action: state.selectedAction,
            args: state.args as any,
          },
          requestId,
        ),
        {
          method: "post",
          action: actionPath,
        },
        requestId,
      );

      if (isAsyncLike(plugin) && result?.taskId) {
        const taskId = String(result.taskId);
        await refreshPluginStatus();
        onStateChange({
          taskId,
          taskStatus: result.status as TaskStatus,
          result: `Task ${taskId} ${result.status}`,
          loading: true,
        });
        startPolling(taskId);
      } else {
        await refreshPluginStatus();
        onStateChange({
          result: formatResult(result),
          loading: false,
        });
      }
    } catch (err: any) {
      onStateChange({
        result: `Error: ${err.message || "Plugin execution failed"}`,
        loading: false,
      });
    }
  }, [
    plugin,
    refreshPluginStatus,
    state.selectedAction,
    state.args,
    onStateChange,
    startPolling,
    stopPolling,
    submitPluginAction,
    actionPath,
  ]);

  const cancelTask = useCallback(async () => {
    if (!state.taskId) return;
    try {
      const requestId = createShellRouteRequestId();
      await submitPluginAction(
        buildShellRouteFormData(
          "cancel-plugin",
          {
            pluginId: plugin.id,
            action: "_task_cancel",
            args: { taskId: state.taskId },
          },
          requestId,
        ),
        {
          method: "post",
          action: actionPath,
        },
        requestId,
      );
      stopPolling();
      onStateChange({
        taskStatus: "CANCELLED",
        loading: false,
        polling: false,
      });
    } catch (err: any) {
      onStateChange({
        result: `Cancel failed: ${err.message}`,
      });
    }
  }, [actionPath, onStateChange, plugin.id, state.taskId, stopPolling, submitPluginAction]);

  const firstActionDescription =
    actionKeys.length > 0 ? actions[actionKeys[0]]?.description : undefined;

  const isRunning =
    state.taskStatus === "RUNNING" ||
    state.taskStatus === "SUBMITTED" ||
    state.taskStatus === "SCHEDULED";

  const pluginStatus = pluginStatusCtx.statuses[plugin.id];
  const isPluginUpdating = pluginStatusCtx.updatingIds.has(plugin.id);
  const needsAction = pluginStatus && (pluginStatus.needsUpdate || !pluginStatus.loaded);

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Puzzle className="h-4 w-4 shrink-0 text-muted-foreground" />
            <span className="text-base font-semibold">{plugin.name}</span>
            <Badge variant="outline" className="text-xs">
              {plugin.version}
            </Badge>
            {plugin.runMode && plugin.runMode !== "sync" && (
              <Badge variant="default" className="gap-1 text-xs">
                {plugin.runMode === "async" ? (
                  <RefreshCw className="h-3 w-3" />
                ) : (
                  <Clock className="h-3 w-3" />
                )}
                {plugin.runMode}
              </Badge>
            )}
          </div>
          {pluginStatus && needsAction && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => pluginStatusCtx.updatePlugin(plugin.id)}
              disabled={isPluginUpdating}
            >
              {isPluginUpdating ? (
                <>
                  <Loader2 className="mr-1.5 size-3.5 animate-spin" />
                  Updating...
                </>
              ) : (
                <>
                  {pluginStatus.loaded ? (
                    <ArrowUpCircle className="mr-1.5 size-3.5" />
                  ) : (
                    <CheckCircle2 className="mr-1.5 size-3.5" />
                  )}
                  {pluginStatus.loaded ? "Update Plugin" : "Install Plugin"}
                </>
              )}
            </Button>
          )}
        </div>
        {pluginStatus && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <span>Server</span>
            <Badge variant="outline" className="text-[10px]">
              {pluginStatus.serverVersion}
            </Badge>
            <span>Shell</span>
            <Badge variant={needsAction ? "secondary" : "outline"} className="text-[10px]">
              {pluginStatus.shellVersion ?? "Not installed"}
            </Badge>
            {!needsAction && pluginStatus.loaded && (
              <Badge variant="secondary" className="text-[10px]">
                Ready
              </Badge>
            )}
            {pluginStatus.needsUpdate && (
              <Badge variant="secondary" className="text-[10px] text-amber-600 dark:text-amber-400">
                Update Available
              </Badge>
            )}
            {!pluginStatus.loaded && (
              <Badge variant="outline" className="text-[10px]">
                Not Installed
              </Badge>
            )}
          </div>
        )}
      </div>

      {firstActionDescription && (
        <p className="text-sm text-muted-foreground">{firstActionDescription}</p>
      )}

      {actionKeys.length > 1 && (
        <div>
          <Label className="mb-1.5 block text-sm font-medium">Action</Label>
          <Select value={state.selectedAction} onValueChange={handleActionSelect}>
            <SelectTrigger>
              <SelectValue placeholder="Select an action" />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(actions).map(([key, action]) => (
                <SelectItem key={key} value={key}>
                  {action.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}

      {currentAction?.argSchema && currentAction.argSchema.length > 0 && (
        <div className="space-y-3">
          {currentAction.argSchema.map((field) => (
            <div key={field.name}>
              <Label className="mb-1.5 block text-sm font-medium">
                {field.label || field.name}
                {field.required && <span className="ml-1 text-red-500">*</span>}
              </Label>
              <Input
                value={state.args[field.name] ?? field.default ?? ""}
                onChange={(e) =>
                  onStateChange({
                    args: { ...state.args, [field.name]: e.target.value },
                  })
                }
                placeholder={field.description || ""}
                disabled={isRunning}
              />
            </div>
          ))}
        </div>
      )}

      {isScheduledMode(plugin) && (
        <div>
          <Label className="mb-1.5 block text-sm font-medium">
            Interval (ms)
            <span className="ml-1 text-red-500">*</span>
          </Label>
          <Input
            value={state.args["_interval"] || "5000"}
            onChange={(e) =>
              onStateChange({
                args: { ...state.args, _interval: e.target.value },
              })
            }
            placeholder="Execution interval in milliseconds"
            type="number"
            disabled={isRunning}
          />
        </div>
      )}

      <div className="flex gap-2">
        <Button onClick={executePlugin} disabled={state.loading || isRunning} className="flex-1">
          {state.loading && !isRunning ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Submitting...
            </>
          ) : (
            <>
              <Play className="mr-2 h-4 w-4" />
              {currentAction?.name || "Execute"}
            </>
          )}
        </Button>

        {isAsyncLike(plugin) && isRunning && (
          <Button variant="destructive" onClick={cancelTask} className="gap-1">
            <Square className="h-4 w-4" />
            {isScheduledMode(plugin) ? "Stop" : "Cancel"}
          </Button>
        )}
      </div>

      {state.taskId && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          {isRunning && <Loader2 className="h-4 w-4 animate-spin text-blue-500" />}
          <span>
            Task #{state.taskId} &mdash;{" "}
            <span
              className={
                state.taskStatus === "COMPLETED"
                  ? "font-medium text-green-500"
                  : state.taskStatus === "FAILED"
                    ? "font-medium text-red-500"
                    : state.taskStatus === "CANCELLED"
                      ? "font-medium text-yellow-500"
                      : "font-medium text-blue-500"
              }
            >
              {state.taskStatus}
            </span>
          </span>
        </div>
      )}

      {state.result && (
        <div className="max-h-96 overflow-auto rounded-lg bg-zinc-950 p-4 font-mono text-sm whitespace-pre-wrap text-zinc-200">
          {state.result}
        </div>
      )}
    </div>
  );
}
