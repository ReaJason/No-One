import type { InitCoreResponse, PingShellResponse } from "@/api/shell-connection-api";
import type { ShellConnection } from "@/types/shell-connection";
import type { ShellOperationLog } from "@/types/shell-operation-log";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import {
  ArrowLeft,
  CheckCircle2,
  CircleDashed,
  PlugZap,
  Server,
  ShieldAlert,
  SkipForward,
  Wifi,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useFetcher, useLoaderData, useNavigate } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getShellConnectionById, initShellCore, pingShell } from "@/api/shell-connection-api";
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

type StepKey = "resolve" | "initCore" | "statusCheck" | "fetchLogs" | "handoff";
type StepStatus = "pending" | "running" | "success" | "skipped" | "error";

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

interface ShellConnectActionData {
  success?: boolean;
  error?: string;
  intent?: "init-core" | "check-status" | "fetch-logs" | null | string;
  logs?: ShellOperationLog[];
}

type InitCoreActionData = ShellConnectActionData & InitCoreResponse;
type CheckStatusActionData = ShellConnectActionData & PingShellResponse;

const HANDOFF_DELAY_MS = 400;

const CONNECT_STEPS: ConnectStep[] = [
  {
    id: "resolve",
    title: "Resolve shell metadata",
    description: "Load shell profile, language and URL context",
  },
  {
    id: "initCore",
    title: "Initialize core",
    description: "Inject core module into target (staging mode only)",
  },
  {
    id: "statusCheck",
    title: "Check server status",
    description: "Probe status first, then auto-recover core once if communication fails",
  },
  {
    id: "fetchLogs",
    title: "Fetch operation logs",
    description: "Retrieve latest operation details for diagnostics",
  },
  {
    id: "handoff",
    title: "Handoff to manager",
    description: "Finalize state and enter interactive shell manager",
  },
];

function createInitialStepStatus(): Record<StepKey, StepStatus> {
  return Object.fromEntries(
    CONNECT_STEPS.map((step) => [step.id, "pending" as StepStatus]),
  ) as Record<StepKey, StepStatus>;
}

function formatLogTimestamp(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString();
}

function statusBadgeClassName(status: StepStatus): string {
  switch (status) {
    case "success":
      return "bg-emerald-600 text-white";
    case "running":
      return "bg-sky-600 text-white";
    case "error":
      return "bg-red-600 text-white";
    case "skipped":
      return "bg-zinc-400 text-white";
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
    return { success: false, error: "Invalid shell ID", intent: null };
  }

  const formData = await request.formData();
  const intent = String(formData.get("intent") ?? "");
  const authFetch = createAuthFetch(request, context);

  if (intent === "init-core") {
    try {
      const result = await initShellCore(shellId, authFetch);
      return { intent, ...result };
    } catch (error: any) {
      return { success: false, intent, error: error?.message ?? "Core init failed" };
    }
  }

  if (intent === "check-status") {
    try {
      const result = await pingShell(shellId, authFetch);
      return { success: result.connected, intent, ...result };
    } catch (error: any) {
      return { success: false, intent, error: error?.message ?? "Status check failed" };
    }
  }

  if (intent === "fetch-logs") {
    try {
      const response = await getShellOperationLogs(
        shellId,
        { operation: "TEST", page: 1, pageSize: 5 },
        authFetch,
      );
      return { success: true, intent, logs: response.content };
    } catch {
      return { success: true, intent, logs: [] };
    }
  }

  return { success: false, error: "Unsupported action", intent };
}

export default function ShellConnectPage() {
  const { shell } = useLoaderData() as { shell: ShellConnection };
  const fetcher = useFetcher<ShellConnectActionData>();
  const navigate = useNavigate();

  const [steps, setSteps] = useState<Record<StepKey, StepStatus>>(createInitialStepStatus);
  const [logs, setLogs] = useState<ConnectLog[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [currentStep, setCurrentStep] = useState<StepKey | null>(null);
  const [attempt, setAttempt] = useState(0);

  const logIdRef = useRef(1);
  const logPanelRef = useRef<HTMLDivElement | null>(null);
  const handoffTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startedFlowIdRef = useRef<string | null>(null);
  const fetcherRef = useRef(fetcher);
  const statusCheckPassedRef = useRef(false);

  fetcherRef.current = fetcher;

  const shellId = Number(shell.id);
  const currentFlowId = String(attempt);

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

  const submitStep = useCallback((intent: string) => {
    const formData = new FormData();
    formData.set("intent", intent);
    fetcherRef.current.submit(formData, { method: "post" });
  }, []);

  const progressValue = useMemo(() => {
    const total = CONNECT_STEPS.length;
    const completed = CONNECT_STEPS.filter(
      (step) => steps[step.id] === "success" || steps[step.id] === "skipped",
    ).length;
    const runningIndex = CONNECT_STEPS.findIndex((step) => steps[step.id] === "running");
    if (runningIndex >= 0) {
      return Math.max(10, Math.round(((runningIndex + 0.5) / total) * 100));
    }
    return Math.round((completed / total) * 100);
  }, [steps]);

  useEffect(() => {
    if (logPanelRef.current) {
      logPanelRef.current.scrollTop = logPanelRef.current.scrollHeight;
    }
  }, [logs]);

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
    logIdRef.current = 1;
    statusCheckPassedRef.current = false;

    appendLog("info", `Starting connection flow for shell #${shellId}`);
    setStepStatus("resolve", "running");
    appendLog("info", "Resolving shell metadata...");
    setStepStatus("resolve", "success");
    appendLog("success", `Resolved: ${shell.url} (${shell.language})`);

    setCurrentStep("initCore");
    setStepStatus("initCore", "running");
    appendLog("info", shell.staging ? "Injecting core module..." : "Core injection not needed");
    submitStep("init-core");
  }, [
    appendLog,
    currentFlowId,
    setStepStatus,
    shell.language,
    shell.staging,
    shell.url,
    shellId,
    submitStep,
  ]);

  useEffect(() => {
    if (fetcher.state !== "idle" || !fetcher.data) {
      return;
    }

    const data = fetcher.data;
    const intent = data.intent;

    if (intent === "init-core" && currentStep === "initCore") {
      const initData = data as InitCoreActionData;
      if (initData.skipped) {
        setStepStatus("initCore", "skipped");
        appendLog("info", "Skipped - non-staging shell");
      } else if (initData.success) {
        setStepStatus("initCore", "success");
        appendLog("success", `Core injected (${initData.durationMs}ms)`);
      } else {
        const failureMessage = initData.error ?? "Core injection failed";
        setStepStatus("initCore", "error");
        setError(failureMessage);
        appendLog("error", failureMessage);
        setCurrentStep(null);
        return;
      }

      setCurrentStep("statusCheck");
      setStepStatus("statusCheck", "running");
      appendLog("info", "Checking server status...");
      submitStep("check-status");
      return;
    }

    if (intent === "check-status" && currentStep === "statusCheck") {
      const statusData = data as CheckStatusActionData;
      if (statusData.success) {
        statusCheckPassedRef.current = true;
        setStepStatus("statusCheck", "success");
        if (statusData.recovered) {
          appendLog("info", "Initial status probe failed; core reinjection succeeded");
        }
        appendLog(
          "success",
          statusData.recovered
            ? `Connected after recovery (${statusData.latencyMs}ms)`
            : `Connected (${statusData.latencyMs}ms)`,
        );
      } else {
        statusCheckPassedRef.current = false;
        setStepStatus("statusCheck", "error");
        const failureMessage =
          statusData.error ??
          (statusData.recoveryAttempted
            ? "Status check failed after core recovery attempt"
            : "Status check failed");
        setError(failureMessage);
        appendLog("error", failureMessage);
      }

      setCurrentStep("fetchLogs");
      setStepStatus("fetchLogs", "running");
      appendLog("info", "Fetching operation logs...");
      submitStep("fetch-logs");
      return;
    }

    if (intent === "fetch-logs" && currentStep === "fetchLogs") {
      const opLogs = data.logs ?? [];
      if (opLogs.length > 0) {
        setStepStatus("fetchLogs", "success");
        appendLog("info", `Retrieved ${opLogs.length} recent operation log(s)`);
        for (const log of opLogs.slice(0, 3)) {
          const level: ConnectLog["level"] = log.success ? "success" : "error";
          const message = log.success
            ? `[${log.operation}] ${log.action ?? log.pluginId ?? "test"} - ${log.durationMs}ms`
            : `[${log.operation}] ${log.errorMessage ?? "failed"} - ${log.durationMs}ms`;
          appendLog(level, message);
        }
      } else {
        setStepStatus("fetchLogs", "skipped");
        appendLog("info", "No recent operation logs");
      }

      if (statusCheckPassedRef.current) {
        setCurrentStep("handoff");
        setStepStatus("handoff", "running");
        appendLog("info", "Preparing shell manager handoff...");
        handoffTimeoutRef.current = setTimeout(() => {
          setStepStatus("handoff", "success");
          appendLog("success", "Handoff complete - entering manager");
          setCurrentStep(null);
          navigate(`/shells/${shellId}/info`);
        }, HANDOFF_DELAY_MS);
      } else {
        setCurrentStep(null);
      }
    }
  }, [
    appendLog,
    currentStep,
    fetcher.data,
    fetcher.state,
    navigate,
    setStepStatus,
    shellId,
    submitStep,
  ]);

  const canRetry = currentStep === null && error != null;
  const isRunning = currentStep !== null;

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
                ) : status === "skipped" ? (
                  <SkipForward className="h-4 w-4 text-muted-foreground" />
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
                    status === "skipped" && "border-muted bg-muted/5",
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
