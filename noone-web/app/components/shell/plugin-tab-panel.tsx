import { Loader2, Play, Puzzle } from "lucide-react";
import { useCallback } from "react";
import * as shellApi from "@/api/shell-api";
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
import type { Plugin } from "@/types/plugin";

export interface PluginTabState {
  selectedAction: string;
  args: Record<string, string>;
  result: string | null;
  loading: boolean;
}

interface PluginTabPanelProps {
  plugin: Plugin;
  shellId: number;
  state: PluginTabState;
  onStateChange: (update: Partial<PluginTabState>) => void;
}

export default function PluginTabPanel({
  plugin,
  shellId,
  state,
  onStateChange,
}: PluginTabPanelProps) {
  const actions = plugin.actions ?? {};
  const actionKeys = Object.keys(actions);
  const currentAction = actions[state.selectedAction];

  const handleActionSelect = (action: string | null) => {
    if (!action) return;
    onStateChange({ selectedAction: action, args: {}, result: null });
  };

  const executePlugin = useCallback(async () => {
    if (!state.selectedAction) return;
    onStateChange({ loading: true, result: null });
    try {
      const data = await shellApi.dispatchPlugin({
        id: shellId,
        pluginId: plugin.id,
        action: state.selectedAction,
        args: state.args as any,
      });
      const result = data?.data ?? data?.result ?? data;
      if (result?.error) {
        onStateChange({ result: `Error: ${result.error}`, loading: false });
      } else if (result?.text) {
        onStateChange({ result: result.text, loading: false });
      } else {
        onStateChange({
          result: JSON.stringify(result, null, 2),
          loading: false,
        });
      }
    } catch (err: any) {
      onStateChange({
        result: `Error: ${err.message || "Plugin execution failed"}`,
        loading: false,
      });
    }
  }, [shellId, plugin.id, state.selectedAction, state.args, onStateChange]);

  const firstActionDescription =
    actionKeys.length > 0 ? actions[actionKeys[0]]?.description : undefined;

  return (
    <div className="space-y-4">
      {/* Plugin header */}
      <div className="flex items-center gap-3">
        <Puzzle className="h-5 w-5 text-muted-foreground" />
        <div className="flex items-center gap-2">
          <span className="text-base font-semibold">{plugin.name}</span>
          <Badge variant="outline">{plugin.version}</Badge>
          <Badge variant="secondary">{plugin.language}</Badge>
        </div>
      </div>
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
                value={state.args[field.name] || ""}
                onChange={(e) =>
                  onStateChange({
                    args: { ...state.args, [field.name]: e.target.value },
                  })
                }
                placeholder={field.description || ""}
              />
            </div>
          ))}
        </div>
      )}

      {/* Execute button */}
      <Button onClick={executePlugin} disabled={state.loading} className="w-full">
        {state.loading ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            Executing...
          </>
        ) : (
          <>
            <Play className="mr-2 h-4 w-4" />
            {currentAction?.name || "Execute"}
          </>
        )}
      </Button>

      {/* Result display */}
      {state.result && (
        <div className="max-h-96 overflow-auto rounded-lg bg-zinc-950 p-4 font-mono text-sm whitespace-pre-wrap text-zinc-200">
          {state.result}
        </div>
      )}
    </div>
  );
}
