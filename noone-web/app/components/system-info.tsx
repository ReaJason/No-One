import {
    Activity,
    ChevronDown,
    ChevronRight,
    Clock,
    Code,
    Copy,
    Database,
    Eye,
    Folder,
    Globe,
    Network,
    Search,
    Server,
    Settings,
    Terminal,
    User,
} from "lucide-react";
import {useCallback, useMemo, useState} from "react";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Input} from "@/components/ui/input";
import {ScrollArea} from "@/components/ui/scroll-area";

interface ApiSystemInfo {
  memoryInfo?: {
    usedMemory?: number;
    totalMemory?: number;
    freeMemory?: number;
    maxMemory?: number;
  };
  processorInfo?: {
    processorEndianness?: string;
    availableProcessors?: number;
    processorArchitecture?: string;
  };
  javaVersion?: string;
  javaVendor?: string;
  timezone?: string;
  osArch?: string;
  osName?: string;
  osVersion?: string;
  userName?: string;
  userHome?: string;
  userDir?: string;
  hostname?: string;
  currentTime?: number;
  systemProperties?: Record<string, string>;
  environment?: Record<string, string>;
  curStackTrace?: string[];
  threadDump?: Array<{
    name?: string;
    id?: number;
    state?: string;
    alive?: boolean;
    priority?: number;
    daemon?: boolean;
  }>;
  ipAddresses?: string[];
  macAddresses?: string[];
  jvmInfo?: {
    systemProperties?: Record<string, string>;
    inputArguments?: string[];
    classPath?: string;
    libraryPath?: string;
    bootClassPath?: string;
    startTime?: number;
    uptime?: number;
  };
}

interface CurrentThreadStack {
  threadName: string;
  state: string;
  stackElements: string[];
}

const CompactPropertyList = ({
  data,
  title,
  searchValue,
  onSearchChange,
  maxHeight = "300px",
}: {
  data: Record<string, string>;
  title: string;
  searchValue: string;
  onSearchChange: (value: string) => void;
  maxHeight?: string;
}) => {
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const filteredData = useMemo(
    () =>
      Object.entries(data).filter(([key]) =>
        key.toLowerCase().includes(searchValue.toLowerCase()),
      ),
    [data, searchValue],
  );

  const toggleExpanded = useCallback((key: string) => {
    setExpandedItems((prev) => {
      const newExpanded = new Set(prev);
      if (newExpanded.has(key)) {
        newExpanded.delete(key);
      } else {
        newExpanded.add(key);
      }
      return newExpanded;
    });
  }, []);

  const copyToClipboard = useCallback(async (text: string, key: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedKey(key);
      setTimeout(() => setCopiedKey(null), 2000);
    } catch (err) {
      console.error("Failed to copy text: ", err);
    }
  }, []);

  const isLongValue = useCallback((value: string) => value.length > 50, []);

  return (
    <div className="space-y-3">
      <div className="flex items-center space-x-2">
        <Settings className="h-4 w-4" />
        <h4 className="text-sm font-semibold text-foreground">{title}</h4>
        <span className="text-xs text-muted-foreground">
          ({filteredData.length})
        </span>
      </div>

      <div className="relative">
        <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search..."
          value={searchValue}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-8 h-8 text-xs"
        />
      </div>

      <ScrollArea className="border rounded-lg" style={{ height: maxHeight }}>
        <div className="p-2 space-y-1">
          {filteredData.map(([key, value]) => {
            const isExpanded = expandedItems.has(key);
            const isLong = isLongValue(value);

            return (
              <div
                key={key}
                className="py-1.5 px-2 hover:bg-muted/50 rounded text-xs border-b border-muted/30 last:border-b-0"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-mono text-muted-foreground break-all">
                        {key}
                      </span>
                      {isLong && (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-5 w-5 p-0"
                          onClick={() => toggleExpanded(key)}
                        >
                          {isExpanded ? (
                            <ChevronDown className="h-3 w-3" />
                          ) : (
                            <ChevronRight className="h-3 w-3" />
                          )}
                        </Button>
                      )}
                    </div>

                    <div className="space-y-1">
                      {isExpanded ? (
                        <div className="space-y-2">
                          <div className="bg-muted/30 p-2 rounded border">
                            <div className="flex items-center justify-between mb-1">
                              <span className="text-xs font-medium text-muted-foreground">
                                Full Value:
                              </span>
                              <Button
                                variant="ghost"
                                size="sm"
                                className="h-6 px-2 text-xs"
                                onClick={() => copyToClipboard(value, key)}
                              >
                                {copiedKey === key ? (
                                  <span className="text-green-600">
                                    Copied!
                                  </span>
                                ) : (
                                  <>
                                    <Copy className="h-3 w-3 mr-1" />
                                    Copy
                                  </>
                                )}
                              </Button>
                            </div>
                            <pre className="font-mono text-xs text-foreground whitespace-pre-wrap break-all">
                              {value}
                            </pre>
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-center justify-between">
                          <span
                            className="font-mono text-foreground break-all"
                            title={value}
                            style={{
                              wordBreak: "break-all",
                              lineHeight: "1.3",
                            }}
                          >
                            {isLong ? `${value.substring(0, 50)}...` : value}
                          </span>
                          {isLong && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-5 px-2 text-xs"
                              onClick={() => toggleExpanded(key)}
                            >
                              <Eye className="h-3 w-3 mr-1" />
                              View
                            </Button>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
          {filteredData.length === 0 && (
            <div className="text-xs text-muted-foreground text-center py-4">
              No matching properties found
            </div>
          )}
        </div>
      </ScrollArea>
    </div>
  );
};

type SystemInfoPayload = ApiSystemInfo | { data?: ApiSystemInfo };

export function JavaSystemMonitor({ data }: { data: SystemInfoPayload }) {
  const systemData: ApiSystemInfo =
    data && typeof data === "object" && "data" in data
      ? (data.data ?? {})
      : (data as ApiSystemInfo);
  const [propertySearch, setPropertySearch] = useState("");
  const [envSearch, setEnvSearch] = useState("");

  const javaProperties = useMemo(
    () =>
      normalizeRecord(
        systemData.systemProperties ?? systemData.jvmInfo?.systemProperties,
      ),
    [systemData],
  );
  const envVariables = useMemo(
    () => normalizeRecord(systemData.environment),
    [systemData],
  );
  const currentThreadStack = useMemo<CurrentThreadStack | null>(() => {
    const stack = systemData.curStackTrace ?? [];
    if (stack.length === 0) return null;
    const threadMeta = systemData.threadDump?.[0];
    return {
      threadName: threadMeta?.name ?? "Current Thread",
      state: threadMeta?.state ?? "UNKNOWN",
      stackElements: stack,
    };
  }, [systemData]);

  const memoryUsed = formatBytes(systemData.memoryInfo?.usedMemory);
  const memoryTotal = formatBytes(systemData.memoryInfo?.totalMemory);
  const memoryFree = formatBytes(systemData.memoryInfo?.freeMemory);
  const memoryMax = formatBytes(systemData.memoryInfo?.maxMemory);
  const uptimeText = formatDuration(systemData.jvmInfo?.uptime);
  const startTimeText = formatTimestamp(systemData.jvmInfo?.startTime);
  const currentTimeText = formatTimestamp(systemData.currentTime);
  const ipAddresses = systemData.ipAddresses ?? [];
  const macAddresses = systemData.macAddresses ?? [];
  const jvmArgs = systemData.jvmInfo?.inputArguments ?? [];

  const CompactInfoCard = ({
    title,
    value,
    icon: Icon,
    description,
    className = "",
  }: {
    title: string;
    value: string;
    icon: any;
    description?: string;
    className?: string;
  }) => (
    <div className={`p-3 bg-card border rounded-lg ${className}`}>
      <div className="flex items-center space-x-2">
        <Icon className="h-4 w-4 text-primary flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <p className="text-xs font-medium text-muted-foreground">{title}</p>
          <p
            className="text-sm font-semibold text-foreground truncate"
            title={value}
          >
            {value}
          </p>
          {description && (
            <p
              className="text-xs text-muted-foreground mt-0.5 truncate"
              title={description}
            >
              {description}
            </p>
          )}
        </div>
      </div>
    </div>
  );

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="text-lg flex items-center space-x-2">
            <Code className="h-5 w-5" />
            <span>Runtime Overview</span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <CompactInfoCard
              title="Java Version"
              value={systemData.javaVersion ?? "Unknown"}
              icon={Code}
            />
            <CompactInfoCard
              title="Java Vendor"
              value={systemData.javaVendor ?? "Unknown"}
              icon={Database}
            />
            <CompactInfoCard
              title="CPU Cores"
              value={
                systemData.processorInfo?.availableProcessors?.toString() ??
                "Unknown"
              }
              icon={Activity}
            />
            <CompactInfoCard title="Uptime" value={uptimeText} icon={Clock} />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <CompactInfoCard
              title="Working Directory"
              value={systemData.userDir ?? "Unknown"}
              icon={Folder}
            />
            <CompactInfoCard
              title="User Home"
              value={systemData.userHome ?? "Unknown"}
              icon={Folder}
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <CompactInfoCard
              title="Start Time"
              value={startTimeText}
              icon={Clock}
            />
            <CompactInfoCard
              title="Current Time"
              value={currentTimeText}
              icon={Clock}
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <CompactInfoCard
              title="Memory Used"
              value={
                memoryUsed && memoryTotal
                  ? `${memoryUsed} / ${memoryTotal}`
                  : "Unknown"
              }
              icon={Activity}
              description={memoryFree ? `Free ${memoryFree}` : undefined}
            />
            <CompactInfoCard
              title="Max Memory"
              value={memoryMax || "Unknown"}
              icon={Activity}
            />
          </div>

          <div className="space-y-2">
            <h4 className="text-sm font-semibold text-foreground flex items-center space-x-1">
              <Terminal className="h-4 w-4" />
              <span>JVM Arguments</span>
            </h4>
            <div className="bg-muted/30 p-3 rounded text-xs font-mono text-foreground break-all leading-relaxed">
              {jvmArgs.length > 0 ? jvmArgs.join(" ") : "No arguments"}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg flex items-center space-x-2">
            <Server className="h-5 w-5" />
            <span>System Information</span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
            <CompactInfoCard
              title="Operating System"
              value={systemData.osName ?? "Unknown"}
              icon={Server}
              description={systemData.osVersion}
            />
            <CompactInfoCard
              title="Architecture"
              value={
                systemData.osArch ??
                systemData.processorInfo?.processorArchitecture ??
                "Unknown"
              }
              icon={Server}
            />
            <CompactInfoCard
              title="Current User"
              value={systemData.userName ?? "Unknown"}
              icon={User}
            />
            <CompactInfoCard
              title="Hostname"
              value={systemData.hostname ?? "Unknown"}
              icon={Network}
            />
            <CompactInfoCard
              title="Timezone"
              value={systemData.timezone ?? "Unknown"}
              icon={Globe}
            />
            <CompactInfoCard
              title="Endianness"
              value={systemData.processorInfo?.processorEndianness ?? "Unknown"}
              icon={Server}
            />
          </div>

          <div className="space-y-3">
            <h4 className="text-sm font-semibold text-foreground flex items-center space-x-1">
              <Network className="h-4 w-4" />
              <span>IP Addresses</span>
              <span className="text-xs text-muted-foreground">
                ({ipAddresses.length})
              </span>
            </h4>
            <div className="flex flex-wrap gap-2">
              {ipAddresses.map((ip) => (
                <Badge
                  key={`ip-${ip}`}
                  variant="outline"
                  className="text-xs font-mono"
                >
                  {ip}
                </Badge>
              ))}
              {ipAddresses.length === 0 && (
                <span className="text-xs text-muted-foreground">
                  No IP addresses
                </span>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <h4 className="text-sm font-semibold text-foreground flex items-center space-x-1">
              <Globe className="h-4 w-4" />
              <span>MAC Addresses</span>
            </h4>
            <div className="flex flex-wrap gap-2">
              {macAddresses.map((mac) => (
                <Badge
                  key={`mac-${mac}`}
                  variant="outline"
                  className="text-xs font-mono"
                >
                  {mac}
                </Badge>
              ))}
              {macAddresses.length === 0 && (
                <span className="text-xs text-muted-foreground">
                  No MAC addresses
                </span>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card className="p-0">
          <CardContent className="p-4">
            <CompactPropertyList
              data={javaProperties}
              title="Java System Properties"
              searchValue={propertySearch}
              onSearchChange={setPropertySearch}
              maxHeight="350px"
            />
          </CardContent>
        </Card>

        <Card className="p-0">
          <CardContent className="p-4">
            <CompactPropertyList
              data={envVariables}
              title="Environment Variables"
              searchValue={envSearch}
              onSearchChange={setEnvSearch}
              maxHeight="350px"
            />
          </CardContent>
        </Card>
      </div>

      {currentThreadStack && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg flex items-center space-x-2">
              <Activity className="h-5 w-5" />
              <span>Current Thread Stack</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="border rounded-lg p-4 space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-base">
                  {currentThreadStack.threadName}
                </h4>
                <Badge
                  variant={
                    currentThreadStack.state === "RUNNABLE"
                      ? "default"
                      : "secondary"
                  }
                >
                  {currentThreadStack.state}
                </Badge>
              </div>
              <ScrollArea className="h-48">
                <div className="space-y-1">
                  {currentThreadStack.stackElements.map((element, index) => (
                    <div
                      key={`stack-${index}-${element.substring(0, 20)}`}
                      className="text-xs font-mono text-muted-foreground p-1 hover:bg-muted/30 rounded break-all"
                    >
                      <span className="text-primary mr-2">#{index + 1}</span>
                      {element}
                    </div>
                  ))}
                </div>
              </ScrollArea>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function normalizeRecord(
  input: Record<string, string> | undefined,
): Record<string, string> {
  if (!input) return {};
  return Object.fromEntries(
    Object.entries(input).map(([key, value]) => [key, String(value)]),
  );
}

function formatBytes(value?: number): string {
  if (!Number.isFinite(value)) return "";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let size = value ?? 0;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size.toFixed(size >= 10 ? 0 : 1)} ${units[unitIndex]}`;
}

function formatDuration(value?: number): string {
  if (!Number.isFinite(value)) return "Unknown";
  const totalSeconds = Math.floor((value ?? 0) / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const parts = [];
  if (days > 0) parts.push(`${days}d`);
  if (hours > 0 || days > 0) parts.push(`${hours}h`);
  if (minutes > 0 || hours > 0 || days > 0) parts.push(`${minutes}m`);
  parts.push(`${seconds}s`);
  return parts.join(" ");
}

function formatTimestamp(value?: number): string {
  if (!Number.isFinite(value)) return "Unknown";
  return new Date(value ?? 0).toLocaleString();
}
