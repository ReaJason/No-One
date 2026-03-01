import { CheckCircle, ChevronLeft, ChevronRight, Filter, XCircle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
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

interface ShellOperationLogListProps {
  shellId: number;
}

const ALL_VALUE = "__ALL__";

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

export default function ShellOperationLogList({ shellId }: ShellOperationLogListProps) {
  const [logs, setLogs] = useState<ShellOperationLog[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [pluginFilter, setPluginFilter] = useState<string>(ALL_VALUE);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const loadLogs = useCallback(async () => {
    setLoading(true);
    try {
      const filters: Record<string, any> = { page, pageSize: 20 };
      if (pluginFilter !== ALL_VALUE) {
        filters.pluginId = pluginFilter;
      }
      const result = await opLogApi.getShellOperationLogs(shellId, filters);
      setLogs(result.content);
      setTotalPages(result.totalPages);
      setTotal(result.total);
    } catch (err) {
      console.error("Failed to load operation logs:", err);
    } finally {
      setLoading(false);
    }
  }, [shellId, page, pluginFilter]);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  const handleFilterChange = (value: string | null) => {
    setPluginFilter(value ?? ALL_VALUE);
    setPage(1);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3">
      {/* Filters */}
      <div className="flex shrink-0 items-center gap-3">
        <Filter className="h-4 w-4 text-muted-foreground" />
        <Select value={pluginFilter} onValueChange={handleFilterChange as any}>
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
        {loading && logs.length === 0 ? (
          <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
            Loading...
          </div>
        ) : logs.length === 0 ? (
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
            onClick={() => setPage((p) => Math.max(1, p - 1))}
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
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
