import type { PluginTabState } from "@/components/shell/plugin-tab-panel";
import type { Plugin } from "@/types/plugin";

import { Circle, Puzzle, Search } from "lucide-react";
import { lazy, Suspense, use, useCallback, useEffect, useMemo, useState } from "react";
import {
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
  useLoaderData,
  useSearchParams,
} from "react-router";

import { createAuthFetch } from "@/api/api.server";
import * as pluginApi from "@/api/plugin-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import { usePluginStatusContext } from "@/components/shell/plugin-status-context";
import { ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  dispatchShellPluginFromRoute,
  updateShellPluginFromRoute,
  parseShellIdParam,
  parseShellRouteFormData,
  shellRouteError,
  shellRouteSuccess,
} from "@/lib/shell-route.server";

import { useShellManagerContext } from "./shell-manager-context";

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
      return Response.json(
        { ok: false, error: message, requestId: undefined },
        { status: error.status || 400 },
      );
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
  const [searchParams] = useSearchParams();

  const initialPluginId = searchParams.get("plugin");
  const [selectedPluginId, setSelectedPluginId] = useState<string | null>(
    initialPluginId && extensionPlugins.some((p) => p.id === initialPluginId)
      ? initialPluginId
      : null,
  );
  const [search, setSearch] = useState("");
  const [pluginStates, setPluginStates] = useState<Record<string, PluginTabState>>({});

  useEffect(() => {
    if (initialPluginId && extensionPlugins.some((p) => p.id === initialPluginId)) {
      setSelectedPluginId(initialPluginId);
    }
  }, [initialPluginId, extensionPlugins]);

  const filteredPlugins = useMemo(() => {
    if (!search.trim()) return extensionPlugins;
    const q = search.toLowerCase();
    return extensionPlugins.filter(
      (p) => p.name.toLowerCase().includes(q) || p.id.toLowerCase().includes(q),
    );
  }, [extensionPlugins, search]);

  const selectedPlugin = useMemo(
    () => extensionPlugins.find((p) => p.id === selectedPluginId) ?? null,
    [extensionPlugins, selectedPluginId],
  );

  const selectPlugin = useCallback((plugin: Plugin) => {
    setSelectedPluginId(plugin.id);
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
  }, []);

  const updatePluginState = useCallback((pluginId: string, update: Partial<PluginTabState>) => {
    setPluginStates((prev) => ({
      ...prev,
      [pluginId]: { ...prev[pluginId], ...update },
    }));
  }, []);

  return (
    <div className="flex h-full min-h-0">
      {/* Plugin list sidebar */}
      <aside className="hidden w-60 shrink-0 border-r border-border/70 bg-muted/20 md:flex md:flex-col">
        <div className="shrink-0 border-b border-border/70 p-3">
          <div className="relative">
            <Search className="absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search plugins..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="h-8 pl-8 text-sm"
            />
          </div>
        </div>
        <ScrollArea className="flex-1">
          <div className="p-1">
            {filteredPlugins.length === 0 ? (
              <div className="px-3 py-6 text-center text-xs text-muted-foreground">
                {search ? "No plugins match your search" : "No extension plugins available"}
              </div>
            ) : (
              filteredPlugins.map((plugin) => {
                const isSelected = plugin.id === selectedPluginId;
                const actions = plugin.actions ?? {};
                const actionCount = Object.keys(actions).length;
                return (
                  <button
                    key={plugin.id}
                    type="button"
                    onClick={() => selectPlugin(plugin)}
                    className={`flex w-full items-center gap-2.5 rounded-md px-3 py-2 text-left text-sm transition-colors ${
                      isSelected
                        ? "bg-accent text-accent-foreground"
                        : "text-foreground/80 hover:bg-accent/50"
                    }`}
                  >
                    <Puzzle className="size-3.5 shrink-0 text-muted-foreground" />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <span className="truncate font-medium">{plugin.name}</span>
                        <PluginStatusDot pluginId={plugin.id} />
                      </div>
                      <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
                        <span>{plugin.version}</span>
                        <span>&middot;</span>
                        <span>
                          {actionCount} {actionCount === 1 ? "action" : "actions"}
                        </span>
                      </div>
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </ScrollArea>
      </aside>

      {/* Detail panel */}
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-auto p-4">
        {selectedPlugin && pluginStates[selectedPlugin.id] ? (
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
              plugin={selectedPlugin}
              actionPath={`/shells/${shell.id}/extensions`}
              taskStatusPath={`/shells/${shell.id}/extensions/status`}
              pluginStatusPath={`/shells/${shell.id}/extensions/plugin-status`}
              state={pluginStates[selectedPlugin.id]}
              onStateChange={(update) => updatePluginState(selectedPlugin.id, update)}
            />
          </Suspense>
        ) : (
          <div className="flex h-full flex-col items-center justify-center gap-3 text-muted-foreground">
            <Puzzle className="size-12 opacity-20" />
            <p className="text-sm">
              {extensionPlugins.length > 0
                ? "Select a plugin from the list to get started"
                : "No extension plugins available for this shell"}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

function PluginStatusDot({ pluginId }: { pluginId: string }) {
  const { statuses } = usePluginStatusContext();
  const status = statuses[pluginId];
  if (!status) return null;
  if (!status.loaded) {
    return <Circle className="size-2 shrink-0 fill-muted-foreground/40 text-muted-foreground/40" />;
  }
  if (status.needsUpdate) {
    return <Circle className="size-2 shrink-0 fill-amber-500 text-amber-500" />;
  }
  return <Circle className="size-2 shrink-0 fill-emerald-500 text-emerald-500" />;
}
