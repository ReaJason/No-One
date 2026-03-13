import type { ShellConnection } from "@/types/shell-connection";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import {
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
import { Link, useFetcher, useLoaderData, useNavigate } from "react-router";

import { createAuthFetch } from "@/api.server";
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

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = params.shellId as string | undefined;
  if (!shellId) {
    throw new Response("Invalid shell ID", { status: 400 });
  }

  const authFetch = createAuthFetch(request, context);
  const shell = await getShellConnectionById(shellId, authFetch);
  if (!shell) {
    throw new Response("Shell connection not found", { status: 404 });
  }

  return { shell };
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  const shellId = Number(params.shellId);
  if (!Number.isInteger(shellId) || shellId <= 0) {
    return { success: false, error: "Invalid shell ID", flowId: null };
  }

  const formData = await request.formData();
  const flowId = String(formData.get("flowId") ?? "");
  if (formData.get("intent") !== "test-connection") {
    return { success: false, error: "Unsupported action", flowId };
  }

  const authFetch = createAuthFetch(request, context);

  try {
    const result = await testShellConnection(shellId, authFetch);
    if (result.connected) {
      return { success: true, flowId };
    }
  } catch (error: any) {
    const latestFailure = await getLatestFailureReason(shellId, authFetch);
    return {
      success: false,
      error: latestFailure ?? error?.message ?? "Connection test failed",
      flowId,
    };
  }

  const latestFailure = await getLatestFailureReason(shellId, authFetch);
  return {
    success: false,
    error: latestFailure ?? "Connection test failed",
    flowId,
  };
}

async function getLatestFailureReason(
  shellId: number,
  authFetch: ReturnType<typeof createAuthFetch>,
): Promise<string | null> {
  try {
    const response = await getShellOperationLogs(
      shellId,
      {
        operation: "TEST",
        success: false,
        page: 1,
        pageSize: 1,
      },
      authFetch,
    );
    const latest = response.content[0];
    if (latest?.errorMessage && latest.errorMessage.trim()) {
      return latest.errorMessage.trim();
    }
  } catch {
    return null;
  }

  return null;
}

export default function ShellConnectPage() {
  const { shell } = useLoaderData() as { shell: ShellConnection };
  const testFetcher = useFetcher<{ success?: boolean; error?: string; flowId?: string | null }>();
  const navigate = useNavigate();

  const [steps, setSteps] = useState<Record<StepKey, StepStatus>>(() => createInitialStepStatus());
  const [logs, setLogs] = useState<ConnectLog[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [isRunning, setIsRunning] = useState(false);

  const logIdRef = useRef(1);
  const logPanelRef = useRef<HTMLDivElement | null>(null);
  const handoffTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startedFlowIdRef = useRef<string | null>(null);
  const shellId = useMemo(() => Number(shell.id), [shell.id]);
  const currentFlowId = String(attempt);

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

  useEffect(() => {
    return () => {
      if (handoffTimeoutRef.current) {
        clearTimeout(handoffTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (startedFlowIdRef.current === currentFlowId) {
      return;
    }
    startedFlowIdRef.current = currentFlowId;

    if (handoffTimeoutRef.current) {
      clearTimeout(handoffTimeoutRef.current);
      handoffTimeoutRef.current = null;
    }

    setSteps(createInitialStepStatus());
    setLogs([]);
    setError(null);
    setIsRunning(true);
    logIdRef.current = 1;

    appendLog("info", `Starting connection flow for shell #${shellId}`);
    setStepStatus("resolveShell", "running");
    appendLog("info", "Resolving shell metadata...");
    setStepStatus("resolveShell", "success");
    appendLog("success", `Loaded ${shell.url} (${shell.language})`);
    setStepStatus("testConnection", "running");
    appendLog("info", "Running connection test...");

    const formData = new FormData();
    formData.set("intent", "test-connection");
    formData.set("flowId", currentFlowId);
    testFetcher.submit(formData, { method: "post" });
  }, [appendLog, currentFlowId, setStepStatus, shell.language, shell.url, shellId, testFetcher]);

  useEffect(() => {
    if (!logPanelRef.current) return;
    logPanelRef.current.scrollTop = logPanelRef.current.scrollHeight;
  }, [logs]);

  useEffect(() => {
    if (
      testFetcher.state !== "idle" ||
      !testFetcher.data ||
      !isRunning ||
      testFetcher.data.flowId !== currentFlowId
    ) {
      return;
    }

    if (testFetcher.data.success) {
      setStepStatus("testConnection", "success");
      appendLog("success", "Connection test passed");
      setStepStatus("handoff", "running");
      appendLog("info", "Preparing shell manager handoff...");
      handoffTimeoutRef.current = setTimeout(() => {
        setStepStatus("handoff", "success");
        appendLog("success", "Handoff complete, entering manager");
        setIsRunning(false);
        navigate(`/shells/${shellId}/info`);
      }, HANDOFF_DELAY_MS);
      return;
    }

    const failureMessage = resolveErrorMessage(
      testFetcher.data.error,
      "Connection workflow failed.",
    );
    setError(failureMessage);
    setStepStatus("testConnection", "error");
    appendLog("error", failureMessage);
    setIsRunning(false);
  }, [
    appendLog,
    currentFlowId,
    isRunning,
    navigate,
    setStepStatus,
    shellId,
    testFetcher.data,
    testFetcher.state,
  ]);

  const canRetry = !isRunning && error != null;

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
            <Badge variant="secondary">#{shell.id}</Badge>
          </div>
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
    </div>
  );
}
