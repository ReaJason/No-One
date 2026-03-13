import type { Plugin, PluginRuntimeStatus, TaskStatus } from "@/types/plugin";

import { Clock, Loader2, Play, Puzzle, RefreshCw, Square } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
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
  pluginStatusPath,
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
  const { fetcher: pluginStatusFetcher, load: loadPluginStatus } =
    useShellRouteFetcher<PluginRuntimeStatus>();
  const [pluginStatus, setPluginStatus] = useState<PluginRuntimeStatus | null>(null);
  const refreshPluginStatus = useCallback(async () => {
    const requestId = createShellRouteRequestId();
    const url = new URL(pluginStatusPath, window.location.origin);
    url.searchParams.set("pluginId", plugin.id);
    url.searchParams.set("requestId", requestId);
    const status = await loadPluginStatus(`${url.pathname}${url.search}`, requestId);
    setPluginStatus(status);
    return status;
  }, [loadPluginStatus, plugin.id, pluginStatusPath]);

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

  useEffect(() => {
    refreshPluginStatus().catch(() => undefined);
  }, [refreshPluginStatus]);

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
        await refreshPluginStatus().catch(() => undefined);
        onStateChange({
          taskId,
          taskStatus: result.status as TaskStatus,
          result: `Task ${taskId} ${result.status}`,
          loading: true,
        });
        startPolling(taskId);
      } else {
        await refreshPluginStatus().catch(() => undefined);
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
  const isPluginStatusLoading = pluginStatus == null && pluginStatusFetcher.state !== "idle";

  return (
    <div className="space-y-4">
      {/* Plugin header */}
      <div className="flex items-center gap-3">
        <Puzzle className="h-5 w-5 text-muted-foreground" />
        <div className="flex items-center gap-2">
          <span className="text-base font-semibold">{plugin.name}</span>
          <Badge variant="outline">{plugin.version}</Badge>
          <Badge variant="secondary">{plugin.language}</Badge>
          {plugin.runMode && plugin.runMode !== "sync" && (
            <Badge variant="default" className="gap-1">
              {plugin.runMode === "async" ? (
                <RefreshCw className="h-3 w-3" />
              ) : (
                <Clock className="h-3 w-3" />
              )}
              {plugin.runMode}
            </Badge>
          )}
        </div>
      </div>
      {pluginStatus && (
        <PluginRuntimeStatusCard
          pluginId={plugin.id}
          pluginName={plugin.name}
          status={pluginStatus}
          actionPath={actionPath}
          onUpdated={(nextStatus) => {
            setPluginStatus(nextStatus);
            onStateChange({ result: null, loading: false });
          }}
        />
      )}
      {isPluginStatusLoading && (
        <div className="rounded-xl border border-border/70 bg-muted/20 p-4 text-sm text-muted-foreground">
          Checking plugin runtime status...
        </div>
      )}
      {firstActionDescription && (
        <p className="text-sm text-muted-foreground">{firstActionDescription}</p>
      )}

      {/* Action selector (only if >1 actions) */}
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

      {/* Dynamic form fields */}
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

      {/* Scheduled mode: interval field */}
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

      {/* Action buttons */}
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

      {/* Task status indicator */}
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

      {/* Result display */}
      {state.result && (
        <div className="max-h-96 overflow-auto rounded-lg bg-zinc-950 p-4 font-mono text-sm whitespace-pre-wrap text-zinc-200">
          {state.result}
        </div>
      )}
    </div>
  );
}
