import type { PluginTabState } from "@/components/shell/plugin-tab-panel";
import type { Plugin } from "@/types/plugin";

import { Puzzle, X } from "lucide-react";
import { lazy, startTransition, Suspense, use, useCallback, useState } from "react";
import { type ActionFunctionArgs, type LoaderFunctionArgs, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import * as pluginApi from "@/api/plugin-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  dispatchShellPluginFromRoute,
  updateShellPluginFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
} from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

const PluginCatalog = lazy(() => import("@/components/shell/plugin-catalog"));
const PluginTabPanel = lazy(() => import("@/components/shell/plugin-tab-panel"));

type ShellExtensionsRouteData = {
  extensionPlugins: Plugin[];
};
type ShellExtensionsLoaderArgs = Pick<LoaderFunctionArgs, "context" | "params" | "request">;

export function loader({ context, params, request }: LoaderFunctionArgs) {
  return {
    routeData: loadShellExtensionsRouteData({ context, params, request }),
  };
}

async function loadShellExtensionsRouteData({
  context,
  params,
  request,
}: ShellExtensionsLoaderArgs): Promise<ShellExtensionsRouteData> {
  const shellId = params.shellId as string;
  const authFetch = createAuthFetch(request, context);
  const shell = await getShellConnectionById(shellId, authFetch);
  if (!shell) {
    throw new Response("Shell connection not found", { status: 404 });
  }
  const result = await pluginApi.getPlugins(
    { type: "Extension", language: shell.language },
    authFetch,
  );
  return { extensionPlugins: result.content };
}

export async function action({ context, params, request }: ActionFunctionArgs) {
  try {
    const shellId = parseShellIdParam(params.shellId);
    const { intent, payload, requestId } = await parseShellRouteFormData<{
      pluginId?: string;
      action?: string;
      args?: Record<string, unknown>;
    }>(request);
    if (intent !== "execute-plugin" && intent !== "cancel-plugin" && intent !== "update-plugin") {
      return Response.json({ ok: false, error: "Unsupported action", requestId }, { status: 400 });
    }
    if (!payload.pluginId) {
      return Response.json({ ok: false, error: "Missing pluginId", requestId }, { status: 400 });
    }

    if (intent === "update-plugin") {
      const data = await updateShellPluginFromRoute(request, context, shellId, payload.pluginId);
      return shellRouteSuccess(data, requestId);
    }

    const data = await dispatchShellPluginFromRoute(request, context, shellId, {
      pluginId: payload.pluginId,
      action: payload.action,
      args: payload.args,
    });
    return shellRouteSuccess(data, requestId);
  } catch (error) {
    if (error instanceof Response) {
      const message = (await error.text()) || error.statusText || "Invalid request";
      return Response.json({ ok: false, error: message }, { status: error.status || 400 });
    }
    return shellRouteError(error, "Plugin execution failed");
  }
}

export default function ShellExtensionsRoute() {
  const { routeData } = useLoaderData() as {
    routeData: Promise<ShellExtensionsRouteData>;
  };
  return (
    <Suspense fallback={<ShellSectionSkeleton variant="extensions" showStatusCard={false} />}>
      <ShellExtensionsContent routeData={routeData} />
    </Suspense>
  );
}

function ShellExtensionsContent({ routeData }: { routeData: Promise<ShellExtensionsRouteData> }) {
  const { shell } = useShellManagerContext();
  const { extensionPlugins } = use(routeData);

  const [extensionSubTab, setExtensionSubTab] = useState("catalog");
  const [openPlugins, setOpenPlugins] = useState<Plugin[]>([]);
  const [pluginStates, setPluginStates] = useState<Record<string, PluginTabState>>({});

  const openPlugin = useCallback((plugin: Plugin) => {
    startTransition(() => {
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
    });
  }, []);

  const closePlugin = useCallback((pluginId: string) => {
    startTransition(() => {
      setOpenPlugins((prev) => prev.filter((p) => p.id !== pluginId));
      setPluginStates((prev) => {
        const next = { ...prev };
        delete next[pluginId];
        return next;
      });
      setExtensionSubTab((prev) => (prev === `plugin-${pluginId}` ? "catalog" : prev));
    });
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
            <Suspense
              fallback={
                <ShellSectionSkeleton
                  label="Loading extension catalog"
                  variant="extensions"
                  showStatusCard={false}
                />
              }
            >
              <PluginCatalog
                extensionPlugins={extensionPlugins}
                openPluginIds={openPlugins.map((p) => p.id)}
                onOpenPlugin={openPlugin}
              />
            </Suspense>
          </TabsContent>

          {openPlugins.map((plugin) => (
            <TabsContent
              key={`plugin-${plugin.id}`}
              value={`plugin-${plugin.id}`}
              className="mt-4 flex min-h-0 flex-1 flex-col overflow-auto"
            >
              {pluginStates[plugin.id] && (
                <Suspense
                  fallback={
                    <ShellSectionSkeleton
                      label="Loading extension panel"
                      variant="extensions"
                      showStatusCard={false}
                    />
                  }
                >
                  <PluginTabPanel
                    plugin={plugin}
                    actionPath={`/shells/${shell.id}/extensions`}
                    taskStatusPath={`/shells/${shell.id}/extensions/status`}
                    pluginStatusPath={`/shells/${shell.id}/extensions/plugin-status`}
                    state={pluginStates[plugin.id]}
                    onStateChange={(update) => updatePluginState(plugin.id, update)}
                  />
                </Suspense>
              )}
            </TabsContent>
          ))}
        </Tabs>
      </div>
    </div>
  );
}
