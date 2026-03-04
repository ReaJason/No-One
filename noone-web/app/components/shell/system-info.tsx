import { lazy, memo, Suspense, useMemo } from "react";
import {
  Activity,
  Box,
  Clock,
  Cpu,
  HardDrive,
  Layers,
  MemoryStick,
  MonitorCheck,
  Network,
  RefreshCw,
  Server,
  Terminal,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import KeyValueList from "@/components/shell/key-value-list";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableRow } from "@/components/ui/table";
import { formatBytes, formatUptime } from "@/lib/utils";

const MemoryCharts = lazy(() => import("./system-info-memory-charts"));

type SystemInfoOs = {
  hostname?: string;
  platform_type?: string;
  name?: string;
  arch?: string;
  version?: string;
};

type SystemInfoRuntimeMem = {
  rss?: number;
  heap_used?: number;
  heap_max?: number;
  heap_total?: number;
  working_set?: number;
  private_mem?: number;
  external?: number;
  nonheap_used?: number;
  nonheap_max?: number;
};

type SystemInfoRuntime = {
  type?: string;
  version?: string;
  mem?: SystemInfoRuntimeMem;
  system_props?: Record<string, unknown>;
};

type SystemInfoProcess = {
  user?: string;
  pid?: number;
  uptime_ms?: number;
  cwd?: string;
  tmp_dir?: string;
  user_home?: string;
  argv?: string[] | string;
};

type SystemInfoFileSystem = {
  path: string;
  total_space?: number;
  free_space?: number;
};

type SystemInfoNetworkInterface = {
  name: string;
  ips: string;
};

type SystemInfoData = {
  os?: SystemInfoOs;
  runtime?: SystemInfoRuntime;
  process?: SystemInfoProcess;
  file_systems?: SystemInfoFileSystem[];
  network?: SystemInfoNetworkInterface[];
  env?: Record<string, unknown>;
};

type SystemDashboardProps = {
  data?: SystemInfoData | null;
  onRefresh?: () => void;
  refreshing?: boolean;
};

function toStringRecord(input: Record<string, unknown> | undefined) {
  if (!input) return {};
  return Object.fromEntries(
    Object.entries(input).map(([key, value]) => [key, String(value)]),
  ) as Record<string, string>;
}

function splitIps(ips: unknown) {
  if (typeof ips !== "string") return [];
  return ips
    .split(",")
    .map((ip) => ip.trim())
    .filter(Boolean);
}

const ChartFallback = memo(function ChartFallback({ isJava }: { isJava: boolean }) {
  if (isJava) {
    return (
      <div className="flex shrink-0 flex-col flex-wrap items-center justify-center gap-2">
        <div className="h-24 w-24 animate-pulse rounded-full bg-zinc-100 dark:bg-zinc-800" />
        <div className="h-24 w-24 animate-pulse rounded-full bg-zinc-100 dark:bg-zinc-800" />
      </div>
    );
  }

  return <div className="h-24 w-24 animate-pulse rounded-full bg-zinc-100 dark:bg-zinc-800" />;
});

const OsWidget = memo(function OsWidget({ os }: { os: SystemInfoOs }) {
  const hostname = os.hostname ?? "-";
  const platformType = os.platform_type ?? "-";
  const osName = os.name ?? "-";
  const arch = os.arch ?? "-";
  const version = os.version ?? "-";

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm font-semibold lg:gap-4">
          <Server className="h-4 w-4" />
          Operating System
        </CardTitle>
      </CardHeader>
      <CardContent className="grid grid-cols-2 gap-2 p-4 lg:grid-cols-4 lg:gap-4">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-blue-50 p-2 dark:bg-blue-900/20">
            <Server className="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div className="overflow-hidden">
            <p className="text-xs text-zinc-500">Hostname</p>
            <p
              className="truncate text-sm font-medium"
              title={hostname !== "-" ? hostname : undefined}
            >
              {hostname}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-green-50 p-2 dark:bg-green-900/20">
            <MonitorCheck className="h-5 w-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <p className="text-xs text-zinc-500">Platform</p>
            <p className="text-sm font-medium">{platformType}</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-orange-50 p-2 dark:bg-orange-900/20">
            <HardDrive className="h-5 w-5 text-orange-600 dark:text-orange-400" />
          </div>
          <div>
            <p className="text-xs text-zinc-500">OS Name</p>
            <p className="text-sm font-medium">{osName}</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-purple-50 p-2 dark:bg-purple-900/20">
            <Cpu className="h-5 w-5 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <p className="text-xs text-zinc-500">Architecture</p>
            <p className="text-sm font-medium">{arch}</p>
          </div>
        </div>
        <div className="col-span-2 space-y-1 border-t pt-2 lg:col-span-4">
          <div className="flex items-center justify-between">
            <p className="text-xs text-zinc-500">Kernel / Version</p>
            <p className="text-right font-mono text-xs text-zinc-600 dark:text-zinc-400">
              {version}
            </p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
});

const MemoryWidget = memo(function MemoryWidget({ runtime }: { runtime: SystemInfoRuntime }) {
  const runtimeType = runtime.type ?? "unknown";
  const isJava = runtimeType === "java";
  const isDotnet = runtimeType === "dotnet";
  const mem = runtime.mem ?? {};

  const heapUsed = mem.heap_used ?? 0;
  const heapMax = isJava
    ? (mem.heap_max ?? 0)
    : isDotnet
      ? (mem.working_set ?? 0)
      : (mem.heap_total ?? 0);
  const heapFree = Math.max(0, heapMax - heapUsed);

  const nonHeapUsed = mem.nonheap_used ?? 0;
  const nonHeapMax = mem.nonheap_max ?? 0;
  const nonHeapFree = Math.max(0, nonHeapMax - nonHeapUsed);

  const heapUsagePercent = heapMax > 0 ? ((heapUsed / heapMax) * 100).toFixed(1) : "0.0";
  const nonHeapUsagePercent =
    nonHeapMax > 0 ? ((nonHeapUsed / nonHeapMax) * 100).toFixed(1) : "0.0";

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm font-semibold">
          <MemoryStick className="h-4 w-4" />
          {`${isJava ? "JVM" : isDotnet ? "DotNet" : "Node"} Memory`}
        </CardTitle>
        <CardAction>
          <Badge>{isDotnet ? `${heapUsagePercent}% Heap/WS` : `${heapUsagePercent}% Heap`}</Badge>
        </CardAction>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-2 p-4 md:flex-row">
        <Suspense fallback={<ChartFallback isJava={isJava} />}>
          <MemoryCharts
            isJava={isJava}
            heapUsed={heapUsed}
            heapFree={heapFree}
            heapUsagePercent={heapUsagePercent}
            nonHeapUsed={nonHeapUsed}
            nonHeapFree={nonHeapFree}
            nonHeapUsagePercent={nonHeapUsagePercent}
          />
        </Suspense>

        <div className="w-full flex-1 space-y-2">
          {isJava ? (
            <>
              <div className="flex justify-between text-xs">
                <span>Heap Used</span>
                <span>{formatBytes(heapUsed)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span>Heap Max</span>
                <span>{formatBytes(heapMax)}</span>
              </div>
              <div className="flex justify-between border-t border-dashed border-zinc-200 pt-1 text-xs">
                <span>Non-Heap Used</span>
                <span>{formatBytes(nonHeapUsed)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span>Non-Heap Max</span>
                <span>{formatBytes(nonHeapMax)}</span>
              </div>
            </>
          ) : isDotnet ? (
            <>
              <div className="flex justify-between text-xs">
                <span>Heap Used</span>
                <span>{formatBytes(mem.heap_used ?? 0)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span>Working Set</span>
                <span>{formatBytes(mem.working_set ?? 0)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span>Private Memory</span>
                <span>{formatBytes(mem.private_mem ?? 0)}</span>
              </div>
            </>
          ) : (
            <>
              <div className="flex justify-between text-xs">
                <span>RSS</span>
                <span>{formatBytes(mem.rss ?? 0)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span>Heap Total</span>
                <span>{formatBytes(mem.heap_total ?? 0)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span>External</span>
                <span>{formatBytes(mem.external ?? 0)}</span>
              </div>
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
});

const FileSystemWidget = memo(function FileSystemWidget({
  filesystems,
}: {
  filesystems: SystemInfoFileSystem[];
}) {
  const processedData = useMemo(() => {
    return filesystems.map((fs) => {
      const total = fs.total_space ?? 0;
      const free = fs.free_space ?? 0;
      const used = Math.max(0, total - free);
      const percent = total > 0 ? ((used / total) * 100).toFixed(0) : "0";
      return { name: fs.path, used, total, percent };
    });
  }, [filesystems]);

  return (
    <Card className="flex h-full flex-col">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm font-semibold">
          <HardDrive className="h-4 w-4" />
          File Systems
        </CardTitle>
      </CardHeader>
      <div className="flex max-h-64 flex-1 flex-col gap-2 overflow-y-auto p-4">
        {processedData.map((fs) => {
          const percentNumber = Number(fs.percent);
          const barColor =
            percentNumber > 90 ? "bg-red-500" : percentNumber > 75 ? "bg-amber-500" : "bg-blue-500";

          return (
            <div key={fs.name} className="space-y-1.5">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-zinc-700 dark:text-zinc-300">{fs.name}</span>
                <span>
                  {formatBytes(fs.used)} / {formatBytes(fs.total)}
                </span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
                <div
                  className={`h-full rounded-full ${barColor}`}
                  style={{ width: `${fs.percent}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
});

const ProcessWidget = memo(function ProcessWidget({
  process,
  runtime,
}: {
  process: SystemInfoProcess;
  runtime: SystemInfoRuntime;
}) {
  const runtimeType = runtime.type ?? "-";
  const runtimeVersion = runtime.version ?? "-";
  const user = process.user ?? "-";
  const userHome = process.user_home ?? "-";
  const pid = process.pid ?? "-";
  const uptimeMs = process.uptime_ms ?? 0;
  const cwd = process.cwd ?? "-";
  const tmpDir = process.tmp_dir ?? "-";
  const argv = process.argv ?? "-";

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm font-semibold">
          <Activity className="h-4 w-4" />
          Process Info
        </CardTitle>
      </CardHeader>
      <CardContent className="grid grid-cols-2 gap-2 p-4 lg:grid-cols-4">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-orange-50 p-2 dark:bg-orange-900/20">
            <Box className="h-5 w-5 text-orange-600 dark:text-orange-400" />
          </div>
          <div>
            <p className="text-xs text-zinc-500">Runtime Ver</p>
            <p className="text-sm font-medium">
              {runtimeType} {runtimeVersion}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-zinc-50 p-2 dark:bg-zinc-800">
            <Terminal className="h-5 w-5 text-zinc-600 dark:text-zinc-400" />
          </div>
          <div className="overflow-hidden">
            <p className="text-xs text-zinc-500">User</p>
            <p className="truncate text-sm font-medium">{user}</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-purple-50 p-2 dark:bg-purple-900/20">
            <Layers className="h-5 w-5 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <p className="text-xs text-zinc-500">PID</p>
            <p className="font-mono text-sm font-bold">{pid}</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-blue-50 p-2 dark:bg-blue-900/20">
            <Clock className="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <p className="text-xs text-zinc-500">Uptime</p>
            <p className="text-sm font-medium">{formatUptime(uptimeMs)}</p>
          </div>
        </div>
      </CardContent>

      <div className="space-y-3 px-4 pb-4">
        <div className="grid grid-cols-2 gap-2">
          <div className="rounded-md border p-2.5">
            <p className="mb-1 text-[10px] font-bold text-zinc-400 uppercase">Working Directory</p>
            <p className="font-mono text-xs break-all text-zinc-700 dark:text-zinc-300">{cwd}</p>
          </div>
          <div className="rounded-md border p-2.5">
            <p className="mb-1 text-[10px] font-bold text-zinc-400 uppercase">Temp Directory</p>
            <p className="font-mono text-xs break-all text-zinc-700 dark:text-zinc-300">{tmpDir}</p>
          </div>
        </div>
        <div className="rounded-md border p-2.5">
          <p className="mb-1 text-[10px] font-bold text-zinc-400 uppercase">User Home</p>
          <p className="font-mono text-xs break-all text-zinc-700 dark:text-zinc-300">{userHome}</p>
        </div>
        <div className="rounded-md border p-2.5">
          <p className="mb-1 text-[10px] font-bold text-zinc-400 uppercase">Arguments</p>
          <code className="font-mono text-xs break-all text-zinc-600 dark:text-zinc-400">
            {Array.isArray(argv) ? argv.join(" ") : String(argv)}
          </code>
        </div>
      </div>
    </Card>
  );
});

const NetworkWidget = memo(function NetworkWidget({
  network,
}: {
  network: SystemInfoNetworkInterface[];
}) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm font-semibold">
          <Network className="h-4 w-4" />
          Network Interfaces
        </CardTitle>
      </CardHeader>
      <CardContent className="max-h-[250px] overflow-auto p-0">
        <Table className="text-left text-xs">
          <TableBody className="divide-y divide-zinc-100 dark:divide-zinc-800">
            {network.map((ni) => (
              <TableRow
                key={ni.name}
                className="group border-b-0 hover:bg-zinc-50/50 dark:hover:bg-zinc-900/40"
              >
                <TableCell className="w-1/4 px-4 py-2 align-top font-semibold text-zinc-700 dark:text-zinc-300">
                  <div className="flex items-center gap-2">
                    <div className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                    {ni.name}
                  </div>
                </TableCell>
                <TableCell className="px-4 py-2 font-mono break-all whitespace-normal text-zinc-600 dark:text-zinc-400">
                  {splitIps(ni.ips).map((ip) => (
                    <div key={ip} className="mb-0.5">
                      {ip}
                    </div>
                  ))}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
});

const SystemDashboard = memo(function SystemDashboard({
  data,
  onRefresh,
  refreshing = false,
}: SystemDashboardProps) {
  if (data == null) {
    return null;
  }

  const os = data.os ?? {};
  const runtime = data.runtime ?? {};
  const process = data.process ?? {};
  const fileSystems = data.file_systems ?? [];
  const network = data.network ?? [];

  const env = useMemo(() => toStringRecord(data.env), [data.env]);
  const systemProps = useMemo(() => toStringRecord(runtime.system_props), [runtime.system_props]);
  const hasSystemProps = Object.keys(systemProps).length > 0;

  return (
    <div className="p-2 pt-0">
      <div className="mx-auto space-y-2">
        <div className="flex justify-end">
          <Button
            size="icon"
            variant="outline"
            aria-label="Refresh system info"
            onClick={onRefresh}
            disabled={!onRefresh || refreshing}
          >
            <RefreshCw className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`} />
          </Button>
        </div>

        <OsWidget os={os} />
        <ProcessWidget process={process} runtime={runtime} />

        <div className="grid grid-cols-1 gap-2 xl:grid-cols-3">
          <MemoryWidget runtime={runtime} />
          <NetworkWidget network={network} />
          <FileSystemWidget filesystems={fileSystems} />
        </div>

        <div className="grid grid-cols-1 gap-2">
          <Tabs defaultValue="env" className="col-span-2 flex min-h-0 flex-col">
            <TabsList>
              <TabsTrigger value="env">Environment Variables</TabsTrigger>
              {hasSystemProps ? <TabsTrigger value="props">System Properties</TabsTrigger> : null}
            </TabsList>
            <TabsContent value="env">
              <KeyValueList title="Environment Variables" data={env} />
            </TabsContent>
            {hasSystemProps ? (
              <TabsContent value="props">
                <KeyValueList title="System Properties" data={systemProps} />
              </TabsContent>
            ) : null}
          </Tabs>
        </div>
      </div>
    </div>
  );
});

export default SystemDashboard;
