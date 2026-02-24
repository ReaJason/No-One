import { Puzzle, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import * as pluginApi from "@/api/plugin-api";
import * as shellApi from "@/api/shell-api";
import CommandExecute from "@/components/shell/command-execute";
import PluginCatalog from "@/components/shell/plugin-catalog";
import PluginTabPanel, { type PluginTabState } from "@/components/shell/plugin-tab-panel";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { Plugin } from "@/types/plugin";
import type { ShellConnection } from "@/types/shell-connection";
import SystemDashboard from "@/components/shell/system-info";

export type ShellManagerSection = "info" | "command" | "extensions";

interface ShellManagerProps {
  shell: ShellConnection;
  section: ShellManagerSection;
}

export default function ShellManager({ shell, section }: ShellManagerProps) {
  const [systemInfo, setSystemInfo] = useState<any>(null);
  const [loading, setLoading] = useState({
    systemInfo: false,
    files: false,
  });
  const [error, setError] = useState<string | null>(null);

  // Extension sub-tab state
  const [extensionSubTab, setExtensionSubTab] = useState("catalog");

  // Extension plugins state
  const [extensionPlugins, setExtensionPlugins] = useState<Plugin[]>([]);
  const [openPlugins, setOpenPlugins] = useState<Plugin[]>([]);
  const [pluginStates, setPluginStates] = useState<Record<string, PluginTabState>>({});

  const loadSystemInfo = useCallback(async () => {
    setLoading((prev) => ({ ...prev, systemInfo: true }));
    setError(null);
    try {
      const data = await shellApi.dispatchPlugin({
        id: shell.id,
        pluginId: "system-info",
      });
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

  const handleRefreshSystemInfo = useCallback(() => {
    loadSystemInfo();
  }, [loadSystemInfo]);

  // Load extension plugins on mount
  useEffect(() => {
    pluginApi
      .getPlugins({ type: "Extension", language: shell.language })
      .then((res) => {
        setExtensionPlugins(res.content);
      })
      .catch(() => {});
  }, []);

  // Plugin tab management
  const openPlugin = useCallback((plugin: Plugin) => {
    setOpenPlugins((prev) => {
      if (prev.some((p) => p.id === plugin.id)) {
        // Already open — just switch to it
        setExtensionSubTab(`plugin-${plugin.id}`);
        return prev;
      }
      return [...prev, plugin];
    });
    setPluginStates((prev) => {
      if (prev[plugin.id]) return prev;
      const actionKeys = plugin.actions ? Object.keys(plugin.actions) : [];
      return {
        ...prev,
        [plugin.id]: {
          selectedAction: actionKeys.length > 0 ? actionKeys[0] : "",
          args: {},
          result: null,
          loading: false,
        },
      };
    });
    setExtensionSubTab(`plugin-${plugin.id}`);
  }, []);

  const closePlugin = useCallback((pluginId: string) => {
    setOpenPlugins((prev) => prev.filter((p) => p.id !== pluginId));
    setPluginStates((prev) => {
      const next = { ...prev };
      delete next[pluginId];
      return next;
    });
    setExtensionSubTab((prev) => (prev === `plugin-${pluginId}` ? "catalog" : prev));
  }, []);

  const updatePluginState = useCallback((pluginId: string, update: Partial<PluginTabState>) => {
    setPluginStates((prev) => ({
      ...prev,
      [pluginId]: { ...prev[pluginId], ...update },
    }));
  }, []);

  const renderContent = () => {
    if (section === "command") {
      return <CommandExecute shellId={shell.id} />;
    }

    if (section === "extensions") {
      return (
        <div className="flex min-h-0 flex-1 flex-col">
          <Tabs
            value={extensionSubTab}
            onValueChange={(val) => val != null && setExtensionSubTab(val)}
            className="flex min-h-0 flex-1 flex-col"
          >
            <TabsList className="flex w-full shrink-0 overflow-x-auto">
              <TabsTrigger value="catalog" className="flex shrink-0 items-center gap-2">
                <Puzzle className="h-4 w-4" />
                Catalog
              </TabsTrigger>
              {openPlugins.map((plugin) => (
                <TabsTrigger
                  key={`plugin-${plugin.id}`}
                  value={`plugin-${plugin.id}`}
                  className="group/tab flex max-w-[160px] shrink-0 items-center gap-1.5"
                >
                  <Puzzle className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate">{plugin.name}</span>
                  <button
                    type="button"
                    className="ml-1 shrink-0 rounded-sm p-0.5 opacity-0 transition-opacity group-hover/tab:opacity-100 hover:bg-muted"
                    onClick={(e) => {
                      e.stopPropagation();
                      closePlugin(plugin.id);
                    }}
                  >
                    <span className="sr-only">Close</span>
                    <X className="h-3 w-3" />
                  </button>
                </TabsTrigger>
              ))}
            </TabsList>

            <TabsContent
              value="catalog"
              className="mt-4 flex min-h-0 flex-1 flex-col overflow-auto"
            >
              <PluginCatalog
                extensionPlugins={extensionPlugins}
                openPluginIds={openPlugins.map((p) => p.id)}
                onOpenPlugin={openPlugin}
              />
            </TabsContent>

            {openPlugins.map((plugin) => (
              <TabsContent
                key={`plugin-${plugin.id}`}
                value={`plugin-${plugin.id}`}
                className="mt-4 flex min-h-0 flex-1 flex-col overflow-auto"
              >
                {pluginStates[plugin.id] && (
                  <PluginTabPanel
                    plugin={plugin}
                    shellId={shell.id}
                    state={pluginStates[plugin.id]}
                    onStateChange={(update) => updatePluginState(plugin.id, update)}
                  />
                )}
              </TabsContent>
            ))}
          </Tabs>
        </div>
      );
    }

    return (
      <div className="min-h-0 flex-1 space-y-4 overflow-auto">
        <SystemDashboard
          data={systemInfo?.data}
          onRefresh={handleRefreshSystemInfo}
          refreshing={loading.systemInfo}
        />
      </div>
    );
  };

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      {error && (
        <div className="shrink-0 rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
          {error}
        </div>
      )}
      {renderContent()}
    </div>
  );
}
