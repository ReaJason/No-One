import "@xterm/xterm/css/xterm.css";
import { useCallback, useEffect } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useCommandTerminal } from "@/hooks/use-command-terminal";
import {
  AUTO_CHARSET_VALUE,
  CHARSET_OPTIONS,
  parseTemplateArgs,
  parseTemplateEnv,
  TEMPLATE_PRESETS,
  usePersistedCommandConfig,
} from "@/hooks/use-persisted-command-config";
import { useShellRouteFetcher } from "@/hooks/use-shell-route-fetcher";
import { buildShellRouteFormData, createShellRouteRequestId } from "@/lib/shell-route";

interface CommandExecuteProps {
  shellId: number;
  actionPath: string;
  osName?: string;
  cwdHint?: string;
  onExecuted?: () => void;
}

export default function CommandExecute({
  shellId,
  actionPath,
  osName,
  cwdHint,
  onExecuted,
}: CommandExecuteProps) {
  const config = usePersistedCommandConfig({ shellId, osName, cwdHint });
  const { submit: submitCommandRequest } = useShellRouteFetcher<Record<string, unknown>>();

  const { containerRef, executeCommand, setRunCommand } = useCommandTerminal({
    onCwdChanged: config.setCwd,
  });

  const runCommand = useCallback(
    (cmd: string) => {
      executeCommand(async () => {
        const template = {
          executable: config.templateExecutable.trim(),
          args: parseTemplateArgs(config.templateArgs),
          env: parseTemplateEnv(config.templateEnv),
        };
        const requestArgs: Record<string, unknown> = { cmd, commandTemplate: template };
        const normalizedCwd = config.cwd.trim();
        if (normalizedCwd) requestArgs.cwd = normalizedCwd;
        const normalizedCharset = config.charset.trim();
        if (normalizedCharset) requestArgs.charset = normalizedCharset;

        const requestId = createShellRouteRequestId();
        const result = await submitCommandRequest(
          buildShellRouteFormData("run-command", requestArgs, requestId),
          { method: "post", action: actionPath },
          requestId,
        );
        onExecuted?.();
        return {
          stdout: result?.stdout,
          stderr: result?.stderr,
          error: result?.error,
          cwd: result?.cwd,
          charsetUsed: result?.charsetUsed,
          exitCode: result?.exitCode,
        };
      });
    },
    [
      actionPath,
      config.charset,
      config.cwd,
      config.templateArgs,
      config.templateEnv,
      config.templateExecutable,
      executeCommand,
      onExecuted,
      submitCommandRequest,
    ],
  );

  useEffect(() => {
    setRunCommand(runCommand);
  }, [runCommand, setRunCommand]);

  return (
    <div className="grid min-h-0 flex-1 gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <Card size="sm" className="h-fit max-h-full overflow-y-auto">
        <CardHeader>
          <CardTitle>Configuration</CardTitle>
        </CardHeader>
        <CardContent>
          <FieldGroup className="gap-3">
            <Field>
              <FieldLabel htmlFor="cmd-cwd">Working Directory</FieldLabel>
              <Input
                id="cmd-cwd"
                className="font-mono text-xs"
                value={config.cwd}
                onChange={(e) => config.setCwd(e.target.value)}
                placeholder="server default"
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="cmd-charset">Charset</FieldLabel>
              <Select
                value={config.charset || AUTO_CHARSET_VALUE}
                onValueChange={(value) => {
                  config.setCharset(!value || value === AUTO_CHARSET_VALUE ? "" : value);
                }}
              >
                <SelectTrigger id="cmd-charset" className="w-full font-mono text-xs">
                  <SelectValue placeholder="Select charset" />
                </SelectTrigger>
                <SelectContent>
                  {CHARSET_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field>
              <FieldLabel htmlFor="cmd-executable">Template Executable</FieldLabel>
              <Input
                id="cmd-executable"
                className="font-mono text-xs"
                value={config.templateExecutable}
                onChange={(e) => config.setTemplateExecutable(e.target.value)}
                placeholder="/bin/sh or cmd.exe"
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="cmd-args">
                {"Template Args (one per line, supports {{cmd}} / {{cwd}})"}
              </FieldLabel>
              <Textarea
                id="cmd-args"
                className="min-h-16 font-mono text-xs"
                value={config.templateArgs}
                onChange={(e) => config.setTemplateArgs(e.target.value)}
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="cmd-env">
                {"Template Env (KEY=VALUE per line, supports {{cmd}} / {{cwd}})"}
              </FieldLabel>
              <Textarea
                id="cmd-env"
                className="min-h-14 font-mono text-xs"
                value={config.templateEnv}
                onChange={(e) => config.setTemplateEnv(e.target.value)}
              />
            </Field>

            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-xs text-muted-foreground">Presets:</span>
              {TEMPLATE_PRESETS.map((preset) => (
                <Button
                  key={preset.label}
                  type="button"
                  variant="outline"
                  size="xs"
                  onClick={() => config.applyPreset(preset.executable, preset.args)}
                >
                  {preset.label}
                </Button>
              ))}
            </div>
          </FieldGroup>
        </CardContent>
      </Card>

      <Card size="sm" className="flex min-h-0 flex-col overflow-hidden">
        <CardHeader className="border-b">
          <CardTitle className="flex items-center gap-2 font-mono text-sm">
            NoOne Command Console
          </CardTitle>
        </CardHeader>
        <div ref={containerRef} className="min-h-0 flex-1 p-2" />
      </Card>
    </div>
  );
}
