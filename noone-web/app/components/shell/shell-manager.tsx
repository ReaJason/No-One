import { Info, Loader2, RefreshCw, Terminal, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import * as shellApi from "@/api/shell-api";
import { JavaSystemMonitor } from "@/components/system-info";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { ShellConnection } from "@/types/shell-connection";

interface ShellManagerProps {
  shell: ShellConnection;
  onClose: () => void;
}

export default function ShellManager({ shell, onClose }: ShellManagerProps) {
  const [systemInfo, setSystemInfo] = useState<any>(null);
  const [loading, setLoading] = useState({
    systemInfo: false,
    files: false,
    command: false,
  });
  const [error, setError] = useState<string | null>(null);

  const loadSystemInfo = useCallback(async () => {
    setLoading((prev) => ({ ...prev, systemInfo: true }));
    setError(null);
    try {
      const data = await shellApi.getSystemInfo(shell.id);
      setSystemInfo(data);
    } catch (err: any) {
      setError(`Failed to load system info: ${err.message || "Unknown error"}`);
      console.error("Failed to load system info:", err);
    } finally {
      setLoading((prev) => ({ ...prev, systemInfo: false }));
    }
  }, [shell.id]);

  // Load system info on mount
  useEffect(() => {
    loadSystemInfo();
  }, [loadSystemInfo]);

  const handleRefreshSystemInfo = () => {
    loadSystemInfo();
  };

  return (
    <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-50">
      <div className="fixed inset-4 bg-background border rounded-lg shadow-lg flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b">
          <div className="flex items-center gap-3">
            <Terminal className="h-5 w-5" />
            <div>
              <h2 className="text-lg font-semibold">Shell Manager</h2>
              <div className="flex items-center gap-2">
                <p className="text-sm text-muted-foreground">{shell.url}</p>
                <Badge variant="secondary" className="bg-green-500 text-white">
                  Connected
                </Badge>
              </div>
            </div>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* Content */}
        <div className="flex-1 min-h-0 flex flex-col p-4">
          <Tabs defaultValue="info" className="flex-1 min-h-0 flex flex-col">
            <TabsList className="grid w-full grid-cols-1 shrink-0">
              <TabsTrigger value="info" className="flex items-center gap-2">
                <Info className="h-4 w-4" />
                System Info
              </TabsTrigger>
            </TabsList>

            {/* Error display */}
            {error && (
              <div className="mt-4 p-4 bg-red-50 border border-red-200 rounded-md text-red-700 shrink-0">
                {error}
              </div>
            )}

            {/* 系统信息 */}
            <TabsContent
              value="info"
              className="flex-1 min-h-0 mt-4 overflow-auto"
            >
              {loading.systemInfo ? (
                <div className="flex items-center justify-center h-64">
                  <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                  <span className="ml-2 text-muted-foreground">
                    Loading system info...
                  </span>
                </div>
              ) : systemInfo ? (
                <div>
                  <div className="flex justify-end mb-4">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleRefreshSystemInfo}
                    >
                      <RefreshCw className="h-4 w-4 mr-2" />
                      Refresh
                    </Button>
                  </div>
                  <JavaSystemMonitor data={systemInfo} />
                </div>
              ) : (
                <div className="flex items-center justify-center h-64 text-muted-foreground">
                  No system information available
                </div>
              )}
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </div>
  );
}
