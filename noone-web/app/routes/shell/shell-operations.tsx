import { CheckCircle, ChevronLeft, ChevronRight, Filter, XCircle } from "lucide-react";
import { useState } from "react";
import { type LoaderFunctionArgs, useLoaderData, useSearchParams } from "react-router";
import * as opLogApi from "@/api/shell-operation-log-api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { ShellOperationLog } from "@/types/shell-operation-log";

const ALL_VALUE = "__ALL__";
const PAGE_SIZE = 20;

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleString();
}

function getOperationBadgeClass(operation: string): string {
  switch (operation) {
    case "TEST":
      return "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300";
    case "DISPATCH":
      return "bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300";
    default:
      return "";
  }
}

function summarizeArgs(log: ShellOperationLog): string | null {
  if (!log.args) return null;
  if (log.pluginId === "command-execute") {
    const cmd = log.args.cmd;
    if (typeof cmd === "string") {
      return cmd.length > 60 ? `${cmd.slice(0, 60)}...` : cmd;
    }
  }
  return null;
}

export async function loader({ params, request }: LoaderFunctionArgs) {
  const shellId = params.shellId as string;
  const url = new URL(request.url);
  const page = Number(url.searchParams.get("page")) || 1;
  const pluginId = url.searchParams.get("pluginId") || undefined;

  const filters: Record<string, any> = { page, pageSize: PAGE_SIZE };
  if (pluginId) {
    filters.pluginId = pluginId;
  }

  const result = await opLogApi.getShellOperationLogs(Number(shellId), filters);
  return {
    logs: result.content,
    totalPages: result.totalPages,
    total: result.total,
  };
}

export default function ShellOperationsRoute() {
  const { logs, totalPages, total } = useLoaderData<typeof loader>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const page = Number(searchParams.get("page")) || 1;
  const pluginFilter = searchParams.get("pluginId") || ALL_VALUE;

  const handleFilterChange = (value: string | null) => {
    const next = new URLSearchParams(searchParams);
    if (!value || value === ALL_VALUE) {
      next.delete("pluginId");
    } else {
      next.set("pluginId", value);
    }
    next.set("page", "1");
    setSearchParams(next, { replace: true });
  };

  const handlePageChange = (newPage: number) => {
    const next = new URLSearchParams(searchParams);
    next.set("page", String(newPage));
    setSearchParams(next, { replace: true });
  };

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <div className="flex min-h-0 flex-1 flex-col gap-3">
        {/* Filters */}
        <div className="flex shrink-0 items-center gap-3">
          <Filter className="h-4 w-4 text-muted-foreground" />
          <Select value={pluginFilter} onValueChange={handleFilterChange}>
            <SelectTrigger className="w-48">
              <SelectValue placeholder="Filter by plugin" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_VALUE}>All Plugins</SelectItem>
              <SelectItem value="system-info">system-info</SelectItem>
              <SelectItem value="command-execute">command-execute</SelectItem>
              <SelectItem value="file-manager">file-manager</SelectItem>
            </SelectContent>
          </Select>
          <span className="text-xs text-muted-foreground">{total} records</span>
        </div>

        {/* Log list */}
        <div className="min-h-0 flex-1 overflow-auto">
          {logs.length === 0 ? (
            <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
              No operation logs found
            </div>
          ) : (
            <div className="space-y-1">
              {logs.map((log) => {
                const summary = summarizeArgs(log);
                const isExpanded = expandedId === log.id;
                return (
                  <div
                    key={log.id}
                    className="cursor-pointer rounded-md border px-3 py-2 text-sm transition-colors hover:bg-muted/50"
                    onClick={() => setExpandedId(isExpanded ? null : log.id)}
                  >
                    <div className="flex items-center gap-2">
                      {log.success ? (
                        <CheckCircle className="h-4 w-4 shrink-0 text-emerald-500" />
                      ) : (
                        <XCircle className="h-4 w-4 shrink-0 text-red-500" />
                      )}
                      <Badge variant="secondary" className={getOperationBadgeClass(log.operation)}>
                        {log.operation}
                      </Badge>
                      {log.pluginId && (
                        <span className="truncate font-mono text-xs">{log.pluginId}</span>
                      )}
                      {log.action && (
                        <span className="text-xs text-muted-foreground">/ {log.action}</span>
                      )}
                      <span className="ml-auto shrink-0 text-xs text-muted-foreground">
                        {formatDuration(log.durationMs)}
                      </span>
                    </div>
                    {summary && (
                      <div className="mt-1 truncate pl-6 font-mono text-xs text-muted-foreground">
                        {summary}
                      </div>
                    )}
                    <div className="mt-1 pl-6 text-xs text-muted-foreground">
                      {formatTime(log.createdAt)}
                    </div>
                    {isExpanded && (
                      <div
                        className="mt-2 space-y-2 border-t pt-2 pl-6"
                        onClick={(e) => e.stopPropagation()}
                      >
                        {log.errorMessage && (
                          <div className="text-xs text-red-600 dark:text-red-400">
                            Error: {log.errorMessage}
                          </div>
                        )}
                        {log.args && (
                          <details className="text-xs">
                            <summary className="cursor-pointer text-muted-foreground">
                              Request Args
                            </summary>
                            <pre className="mt-1 max-h-40 overflow-auto rounded bg-muted p-2 font-mono text-xs">
                              {JSON.stringify(log.args, null, 2)}
                            </pre>
                          </details>
                        )}
                        {log.result && (
                          <details className="text-xs">
                            <summary className="cursor-pointer text-muted-foreground">
                              Response Result
                            </summary>
                            <pre className="mt-1 max-h-40 overflow-auto rounded bg-muted p-2 font-mono text-xs">
                              {JSON.stringify(log.result, null, 2)}
                            </pre>
                          </details>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex shrink-0 items-center justify-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page <= 1}
              onClick={() => handlePageChange(Math.max(1, page - 1))}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-xs text-muted-foreground">
              {page} / {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages}
              onClick={() => handlePageChange(Math.min(totalPages, page + 1))}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
