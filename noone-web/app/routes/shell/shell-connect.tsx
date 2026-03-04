import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  CircleDashed,
  PlugZap,
  Server,
  ShieldAlert,
  Wifi,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { getShellConnectionById, testShellConnection } from "@/api/shell-connection-api";
import { getShellOperationLogs } from "@/api/shell-operation-log-api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import type { ShellConnection } from "@/types/shell-connection";

type StepKey = "resolveShell" | "testConnection" | "handoff";
type StepStatus = "pending" | "running" | "success" | "error";

interface ConnectStep {
  id: StepKey;
  title: string;
  description: string;
}

interface ConnectLog {
  id: number;
  timestamp: number;
  level: "info" | "success" | "error";
  message: string;
}

const HANDOFF_DELAY_MS = 600;

const CONNECT_STEPS: ConnectStep[] = [
  {
    id: "resolveShell",
    title: "Resolve shell metadata",
    description: "Load shell profile, language and URL context",
  },
  {
    id: "testConnection",
    title: "Test shell connection",
    description: "Execute /shells/{id}/test and verify reachability",
  },
  {
    id: "handoff",
    title: "Handoff to manager",
    description: "Finalize state and enter interactive shell manager",
  },
];

function createInitialStepStatus(): Record<StepKey, StepStatus> {
  return {
    resolveShell: "pending",
    testConnection: "pending",
    handoff: "pending",
  };
}

function formatLogTimestamp(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString();
}

function resolveErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message.trim();
  }
  if (typeof error === "string" && error.trim()) {
    return error.trim();
  }
  return fallback;
}

function isAbortError(error: unknown): boolean {
  if (!error || typeof error !== "object") {
    return false;
  }
  const candidate = error as { name?: string; code?: string; message?: string };
  if (candidate.name === "AbortError" || candidate.code === "ABORT_ERR") {
    return true;
  }
  const message = (candidate.message ?? "").toLowerCase();
  return (
    message.includes("aborted") || message.includes("cancelled") || message.includes("canceled")
  );
}

function statusBadgeClassName(status: StepStatus): string {
  switch (status) {
    case "success":
      return "bg-emerald-600 text-white";
    case "running":
      return "bg-sky-600 text-white";
    case "error":
      return "bg-red-600 text-white";
    default:
      return "bg-zinc-500 text-white";
  }
}

function logColorClassName(level: ConnectLog["level"]): string {
  switch (level) {
    case "success":
      return "text-emerald-400";
    case "error":
      return "text-red-400";
    default:
      return "text-slate-300";
  }
}

export default function ShellConnectPage() {
  const { shellId } = useParams();
  const navigate = useNavigate();

  const [shell, setShell] = useState<ShellConnection | null>(null);
  const [steps, setSteps] = useState<Record<StepKey, StepStatus>>(() => createInitialStepStatus());
  const [logs, setLogs] = useState<ConnectLog[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [isRunning, setIsRunning] = useState(false);

  const mountedRef = useRef(true);
  const logIdRef = useRef(1);
  const abortControllerRef = useRef<AbortController | null>(null);
  const logPanelRef = useRef<HTMLDivElement | null>(null);

  const parsedShellId = useMemo(() => {
    if (!shellId) return null;
    const value = Number(shellId);
    if (!Number.isInteger(value) || value <= 0) {
      return null;
    }
    return value;
  }, [shellId]);

  const progressValue = useMemo(() => {
    const total = CONNECT_STEPS.length;
    const completed = CONNECT_STEPS.filter((step) => steps[step.id] === "success").length;
    const runningIndex = CONNECT_STEPS.findIndex((step) => steps[step.id] === "running");
    if (runningIndex >= 0) {
      return Math.max(10, Math.round(((runningIndex + 0.5) / total) * 100));
    }
    return Math.round((completed / total) * 100);
  }, [steps]);

  const appendLog = useCallback((level: ConnectLog["level"], message: string) => {
    setLogs((prev) => [
      ...prev,
      {
        id: logIdRef.current++,
        timestamp: Date.now(),
        level,
        message,
      },
    ]);
  }, []);

  const setStepStatus = useCallback((step: StepKey, status: StepStatus) => {
    setSteps((prev) => ({ ...prev, [step]: status }));
  }, []);

  const loadLatestFailureReason = useCallback(
    async (currentShellId: number): Promise<string | null> => {
      try {
        const response = await getShellOperationLogs(currentShellId, {
          operation: "TEST",
          success: false,
          page: 1,
          pageSize: 1,
        });
        const latest = response.content[0];
        if (latest?.errorMessage && latest.errorMessage.trim()) {
          return latest.errorMessage.trim();
        }
        return null;
      } catch {
        return null;
      }
    },
    [],
  );

  const runConnectionFlow = useCallback(async () => {
    abortControllerRef.current?.abort();
    const controller = new AbortController();
    abortControllerRef.current = controller;

    setSteps(createInitialStepStatus());
    setLogs([]);
    setError(null);
    setIsRunning(true);
    logIdRef.current = 1;

    if (parsedShellId == null) {
      setStepStatus("resolveShell", "error");
      setError("Invalid shell id.");
      appendLog("error", "Invalid shell id.");
      setIsRunning(false);
      return;
    }

    appendLog("info", `Starting connection flow for shell #${parsedShellId}`);

    try {
      setStepStatus("resolveShell", "running");
      appendLog("info", "Resolving shell metadata...");
      const shellData = await getShellConnectionById(parsedShellId, { signal: controller.signal });
      if (!mountedRef.current || controller.signal.aborted) return;

      setShell(shellData);
      setStepStatus("resolveShell", "success");
      appendLog("success", `Loaded ${shellData.url} (${shellData.language})`);

      setStepStatus("testConnection", "running");
      appendLog("info", "Running connection test...");
      const testResult = await testShellConnection(parsedShellId, { signal: controller.signal });
      if (!mountedRef.current || controller.signal.aborted) return;

      if (!testResult.connected) {
        setStepStatus("testConnection", "error");
        appendLog("error", "Connection test returned disconnected state");
        const reason = await loadLatestFailureReason(parsedShellId);
        throw new Error(reason ?? "Connection test failed");
      }

      setStepStatus("testConnection", "success");
      appendLog("success", "Connection test passed");

      setStepStatus("handoff", "running");
      appendLog("info", "Preparing shell manager handoff...");
      await new Promise((resolve) => setTimeout(resolve, HANDOFF_DELAY_MS));
      if (!mountedRef.current || controller.signal.aborted) return;

      setStepStatus("handoff", "success");
      appendLog("success", "Handoff complete, entering manager");
      navigate(`/shells/${parsedShellId}/info`);
    } catch (flowError) {
      if (!mountedRef.current || controller.signal.aborted || isAbortError(flowError)) {
        return;
      }

      const failureMessage = resolveErrorMessage(flowError, "Connection workflow failed.");
      setError(failureMessage);
      setSteps((prev) => {
        const runningStep = (Object.keys(prev) as StepKey[]).find((key) => prev[key] === "running");
        if (!runningStep) return prev;
        return { ...prev, [runningStep]: "error" };
      });
      appendLog("error", failureMessage);
    } finally {
      if (mountedRef.current && abortControllerRef.current === controller) {
        setIsRunning(false);
      }
    }
  }, [appendLog, loadLatestFailureReason, navigate, parsedShellId, setStepStatus]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      abortControllerRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    void runConnectionFlow();
  }, [runConnectionFlow, attempt]);

  useEffect(() => {
    if (!logPanelRef.current) return;
    logPanelRef.current.scrollTop = logPanelRef.current.scrollHeight;
  }, [logs]);

  const canRetry = !isRunning && error != null && parsedShellId != null;

  return (
    <div className="container mx-auto max-w-4xl p-6">
      <Card className="mx-auto mt-8">
        <CardHeader className="border-b">
          <div className="flex items-start justify-between gap-3">
            <div className="space-y-1">
              <CardTitle className="flex items-center gap-2 text-lg">
                <PlugZap className="h-5 w-5" />
                Connect Shell
              </CardTitle>
              <CardDescription>
                Running preflight checks before entering shell manager.
              </CardDescription>
            </div>
            <Badge variant="secondary">#{shellId ?? "unknown"}</Badge>
          </div>
          {shell && (
            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1">
                <Server className="h-3.5 w-3.5" />
                {shell.url}
              </span>
              <Badge variant="outline" className="text-[11px]">
                {shell.language}
              </Badge>
              <Badge variant="outline" className="text-[11px]">
                {shell.status}
              </Badge>
            </div>
          )}
        </CardHeader>

        <CardContent className="space-y-5 pt-4">
          <Progress value={progressValue} className="w-full">
            <div className="flex w-full items-center justify-between text-xs text-muted-foreground">
              <span>Progress</span>
              <span>{progressValue}%</span>
            </div>
          </Progress>

          <div className="space-y-2">
            {CONNECT_STEPS.map((step) => {
              const status = steps[step.id];
              const icon =
                status === "running" ? (
                  <Spinner className="size-4" />
                ) : status === "success" ? (
                  <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                ) : status === "error" ? (
                  <XCircle className="h-4 w-4 text-red-500" />
                ) : (
                  <CircleDashed className="h-4 w-4 text-muted-foreground" />
                );
              return (
                <div
                  key={step.id}
                  className={cn(
                    "rounded-md border px-3 py-2 transition-colors",
                    status === "running" && "border-sky-500/40 bg-sky-500/5",
                    status === "success" && "border-emerald-500/40 bg-emerald-500/5",
                    status === "error" && "border-red-500/40 bg-red-500/5",
                  )}
                >
                  <div className="flex items-start gap-2">
                    <div className="pt-0.5">{icon}</div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{step.title}</span>
                        <Badge variant="secondary" className={statusBadgeClassName(status)}>
                          {status}
                        </Badge>
                      </div>
                      <p className="mt-0.5 text-xs text-muted-foreground">{step.description}</p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2 text-xs font-medium tracking-wider text-muted-foreground uppercase">
              <Wifi className="h-3.5 w-3.5" />
              Steps logs
            </div>
            <div
              ref={logPanelRef}
              className="h-56 overflow-auto rounded-md border bg-slate-950 p-3 font-mono text-xs leading-relaxed"
            >
              {logs.length === 0 ? (
                <div className="text-slate-500">Awaiting connection workflow...</div>
              ) : (
                <div className="space-y-1">
                  {logs.map((log) => (
                    <div key={log.id} className="flex gap-2">
                      <span className="shrink-0 text-slate-500">
                        [{formatLogTimestamp(log.timestamp)}]
                      </span>
                      <span className={logColorClassName(log.level)}>{log.message}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {error && (
            <div className="rounded-md border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-500">
              <div className="flex items-start gap-2">
                <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            </div>
          )}
        </CardContent>

        <CardFooter className="justify-between gap-2">
          <Link to="/shells">
            <Button variant="outline" size="sm">
              <ArrowLeft className="mr-1 h-4 w-4" />
              Back to Shells
            </Button>
          </Link>
          <div className="flex items-center gap-2">
            {isRunning && (
              <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                <Spinner className="size-3.5" />
                Connecting...
              </span>
            )}
            {canRetry && (
              <Button variant="default" size="sm" onClick={() => setAttempt((prev) => prev + 1)}>
                Retry
              </Button>
            )}
            {!canRetry && !isRunning && (
              <span className="inline-flex items-center gap-1 text-xs text-emerald-600">
                <CheckCircle2 className="h-3.5 w-3.5" />
                Ready
              </span>
            )}
          </div>
        </CardFooter>
      </Card>
      {parsedShellId == null && (
        <div className="mx-auto mt-4 flex max-w-4xl items-center gap-2 rounded-md border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-500">
          <AlertCircle className="h-4 w-4 shrink-0" />
          The provided shell id is invalid.
        </div>
      )}
    </div>
  );
}
