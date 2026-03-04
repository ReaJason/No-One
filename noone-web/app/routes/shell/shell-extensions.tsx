import { Puzzle, X } from "lucide-react";
import { useCallback, useState } from "react";
import { type LoaderFunctionArgs, useLoaderData } from "react-router";
import * as pluginApi from "@/api/plugin-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import PluginCatalog from "@/components/shell/plugin-catalog";
import PluginTabPanel, { type PluginTabState } from "@/components/shell/plugin-tab-panel";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { Plugin } from "@/types/plugin";
import { useShellManagerContext } from "./shell-manager-context";

export async function loader({ params }: LoaderFunctionArgs) {
  const shellId = params.shellId as string;
  const shell = await getShellConnectionById(shellId);
  const result = await pluginApi.getPlugins({ type: "Extension", language: shell.language });
  return { extensionPlugins: result.content };
}

export default function ShellExtensionsRoute() {
  const { shell } = useShellManagerContext();
  const { extensionPlugins } = useLoaderData<typeof loader>();

  const [extensionSubTab, setExtensionSubTab] = useState("catalog");
  const [openPlugins, setOpenPlugins] = useState<Plugin[]>([]);
  const [pluginStates, setPluginStates] = useState<Record<string, PluginTabState>>({});

  const openPlugin = useCallback((plugin: Plugin) => {
    setOpenPlugins((prev) => {
      if (prev.some((p) => p.id === plugin.id)) {
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

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
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

          <TabsContent value="catalog" className="mt-4 flex min-h-0 flex-1 flex-col overflow-auto">
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
    </div>
  );
}
