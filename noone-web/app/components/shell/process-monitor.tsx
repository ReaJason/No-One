import { ArrowDown, ArrowUp, ArrowUpDown, RefreshCw, Search } from "lucide-react";
import { useMemo, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface ProcessInfo {
  pid: string;
  ppid?: string;
  name: string;
  user: string;
  uid?: string;
  state: string;
  vmRss?: string;
  vmSize?: string;
  threads?: string;
  command: string;
}

interface ProcessMonitorData {
  os?: string;
  total?: number;
  processes?: ProcessInfo[];
  error?: string;
}

type SortField =
  | "pid"
  | "ppid"
  | "name"
  | "user"
  | "uid"
  | "state"
  | "vmRss"
  | "vmSize"
  | "threads"
  | "command";
type SortDir = "asc" | "desc";

function formatBytes(bytesStr: string | undefined): string {
  if (!bytesStr) return "-";
  const bytes = Number(bytesStr);
  if (Number.isNaN(bytes) || bytes < 0) return bytesStr;
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, i);
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[i]}`;
}

function stateBadgeVariant(state: string): "default" | "secondary" | "outline" | "destructive" {
  switch (state) {
    case "R":
      return "default";
    case "Z":
      return "destructive";
    case "S":
      return "secondary";
    default:
      return "outline";
  }
}

function compareField(a: ProcessInfo, b: ProcessInfo, field: SortField): number {
  switch (field) {
    case "pid":
      return Number(a.pid) - Number(b.pid);
    case "ppid":
      return Number(a.ppid || "0") - Number(b.ppid || "0");
    case "uid":
      return Number(a.uid || "0") - Number(b.uid || "0");
    case "vmRss":
      return Number(a.vmRss || "0") - Number(b.vmRss || "0");
    case "vmSize":
      return Number(a.vmSize || "0") - Number(b.vmSize || "0");
    case "threads":
      return Number(a.threads || "0") - Number(b.threads || "0");
    case "name":
      return (a.name || "").localeCompare(b.name || "");
    case "user":
      return (a.user || "").localeCompare(b.user || "");
    case "state":
      return (a.state || "").localeCompare(b.state || "");
    case "command":
      return (a.command || "").localeCompare(b.command || "");
    default:
      return 0;
  }
}

interface ProcessMonitorProps {
  data: ProcessMonitorData;
  onRefresh: () => void;
  refreshing: boolean;
}

export default function ProcessMonitor({ data, onRefresh, refreshing }: ProcessMonitorProps) {
  const [search, setSearch] = useState("");
  const [sortField, setSortField] = useState<SortField>("pid");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  if (data.error) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 text-muted-foreground">
        <p className="text-sm">{data.error}</p>
      </div>
    );
  }

  const processes = data.processes || [];
  const searchLower = search.toLowerCase();

  const filtered = useMemo(() => {
    if (!searchLower) return processes;
    return processes.filter(
      (p) =>
        p.pid.includes(searchLower) ||
        (p.name || "").toLowerCase().includes(searchLower) ||
        (p.user || "").toLowerCase().includes(searchLower) ||
        (p.command || "").toLowerCase().includes(searchLower),
    );
  }, [processes, searchLower]);

  const sorted = useMemo(() => {
    const copy = filtered.slice();
    copy.sort((a, b) => {
      const cmp = compareField(a, b, sortField);
      return sortDir === "asc" ? cmp : -cmp;
    });
    return copy;
  }, [filtered, sortField, sortDir]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir(sortDir === "asc" ? "desc" : "asc");
    } else {
      setSortField(field);
      setSortDir("asc");
    }
  };

  const SortIcon = ({ field }: { field: SortField }) => {
    if (sortField !== field) return <ArrowUpDown className="ml-1 inline size-3 opacity-40" />;
    return sortDir === "asc" ? (
      <ArrowUp className="ml-1 inline size-3" />
    ) : (
      <ArrowDown className="ml-1 inline size-3" />
    );
  };

  return (
    <div className="flex h-full flex-col gap-3">
      <div className="flex shrink-0 items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Badge variant="secondary" className="tabular-nums">
            {filtered.length === processes.length
              ? `${processes.length} processes`
              : `${filtered.length} / ${processes.length} processes`}
          </Badge>
          {data.os && <Badge variant="outline">{data.os}</Badge>}
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Filter by PID, name, user, command..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="h-8 w-64 pl-8 text-sm"
            />
          </div>
          <Button variant="outline" size="sm" onClick={onRefresh} disabled={refreshing}>
            <RefreshCw className={`mr-1.5 size-3.5 ${refreshing ? "animate-spin" : ""}`} />
            Refresh
          </Button>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-auto rounded-md border">
        <Table>
          <TableHeader className="sticky top-0 z-10 bg-background">
            <TableRow>
              <SortableHead
                field="pid"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
              >
                PID
              </SortableHead>
              <SortableHead
                field="ppid"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
              >
                PPID
              </SortableHead>
              <SortableHead
                field="name"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
              >
                Name
              </SortableHead>
              <SortableHead
                field="user"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
              >
                User
              </SortableHead>
              <SortableHead
                field="uid"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
              >
                UID
              </SortableHead>
              <SortableHead
                field="state"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
              >
                State
              </SortableHead>
              <SortableHead
                field="vmRss"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
                align="right"
              >
                RSS
              </SortableHead>
              <SortableHead
                field="vmSize"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
                align="right"
              >
                Virtual
              </SortableHead>
              <SortableHead
                field="threads"
                current={sortField}
                dir={sortDir}
                onSort={handleSort}
                Icon={SortIcon}
                align="right"
              >
                Threads
              </SortableHead>
              <TableHead className="min-w-[300px]">Command</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.length === 0 ? (
              <TableRow>
                <TableCell colSpan={10} className="h-24 text-center text-muted-foreground">
                  {search ? "No processes match your search" : "No processes found"}
                </TableCell>
              </TableRow>
            ) : (
              sorted.map((proc) => (
                <TableRow key={proc.pid}>
                  <TableCell className="font-mono text-xs tabular-nums">{proc.pid}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground tabular-nums">
                    {proc.ppid || "-"}
                  </TableCell>
                  <TableCell className="font-medium">{proc.name}</TableCell>
                  <TableCell>{proc.user}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground tabular-nums">
                    {proc.uid || "-"}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={stateBadgeVariant(proc.state)}
                      className="px-1.5 py-0 text-[10px]"
                    >
                      {proc.state}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs tabular-nums">
                    {formatBytes(proc.vmRss)}
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs tabular-nums">
                    {formatBytes(proc.vmSize)}
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs tabular-nums">
                    {proc.threads || "-"}
                  </TableCell>
                  <TableCell className="font-mono text-[11px] break-all whitespace-normal text-muted-foreground">
                    {proc.command || "-"}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}

function SortableHead({
  field,
  current,
  dir,
  onSort,
  Icon,
  align,
  children,
}: {
  field: SortField;
  current: SortField;
  dir: SortDir;
  onSort: (f: SortField) => void;
  Icon: React.ComponentType<{ field: SortField }>;
  align?: "right";
  children: React.ReactNode;
}) {
  return (
    <TableHead
      className={`cursor-pointer select-none ${align === "right" ? "text-right" : ""}`}
      onClick={() => onSort(field)}
    >
      {children} <Icon field={field} />
    </TableHead>
  );
}
