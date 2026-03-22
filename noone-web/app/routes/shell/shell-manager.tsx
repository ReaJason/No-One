import type { ShellConnection } from "@/types/shell-connection";
import type { ComponentType } from "react";

import { Separator } from "@radix-ui/react-separator";
import {
  Activity,
  ArrowLeft,
  ClipboardList,
  Files,
  Info,
  Puzzle,
  Repeat2,
  ShieldCheck,
  Terminal,
} from "lucide-react";
import { NuqsAdapter } from "nuqs/adapters/react-router/v7";
import { Suspense, use, useEffect, useMemo } from "react";
import {
  type LoaderFunctionArgs,
  NavLink,
  Outlet,
  type ShouldRevalidateFunctionArgs,
  useLoaderData,
  useLocation,
  useNavigation,
} from "react-router";
import { Toaster } from "sonner";

import { createAuthFetch } from "@/api/api.server";
import * as pluginApi from "@/api/plugin-api";
import { getShellConnectionById } from "@/api/shell-connection-api";
import { Icons } from "@/components/icons";
import { ModeToggle } from "@/components/mode-toggle";
import { PluginStatusProvider } from "@/components/shell/plugin-status-context";
import {
  ShellManagerSkeleton,
  ShellSectionSkeleton,
  type ShellSectionSkeletonVariant,
} from "@/components/shell/shell-route-loading";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import {
  getShellNavigationDurationMs,
  getShellRouteRequestKind,
  logShellRouteDebug,
  markShellNavigationTrace,
  readShellNavigationTrace,
} from "@/lib/shell-route-debug";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = params.shellId as string | undefined;
  if (!shellId) {
    throw new Response("Invalid shell ID", { status: 400 });
  }
  const startedAt = Date.now();
  const url = new URL(request.url);
  logShellRouteDebug("shell-manager", "loader start", {
    shellId,
    kind: getShellRouteRequestKind(url),
    method: request.method,
    url: request.url,
  });
  const authFetch = createAuthFetch(request, context);
  const shellPromise = getShellConnectionById(shellId, authFetch).then((shell) => {
    if (!shell) {
      throw new Response("Shell connection not found", { status: 404 });
    }
    logShellRouteDebug("shell-manager", "shell resolved", {
      shellId,
      durationMs: Date.now() - startedAt,
    });
    return shell;
  });
  return {
    shell: shellPromise,
    standardPluginIds: shellPromise.then(async (shell) => {
      const pluginsStartedAt = Date.now();
      const result = await pluginApi.getPlugins(
        { type: "Standard", language: shell.language },
        authFetch,
      );
      logShellRouteDebug("shell-manager", "standard plugins resolved", {
        shellId,
        language: shell.language,
        count: result.content.length,
        durationMs: Date.now() - pluginsStartedAt,
        totalDurationMs: Date.now() - startedAt,
      });
      return result.content.map((p) => p.id);
    }),
  } as {
    shell: Promise<ShellConnection>;
    standardPluginIds: Promise<string[]>;
  };
}

const shellSections = [
  { id: "info", title: "Basic Info", icon: Info, pluginId: "system-info" },
  { id: "files", title: "Files", icon: Files, pluginId: "file-manager" },
  { id: "command", title: "Command Execute", icon: Terminal, pluginId: "command-execute" },
  { id: "repeater", title: "HTTP Repeater", icon: Repeat2, pluginId: "http-repeater" },
  { id: "processes", title: "Process Monitor", icon: Activity, pluginId: "process-monitor" },
  { id: "extensions", title: "Extensions", icon: Puzzle },
  { id: "status", title: "Plugin Status", icon: ShieldCheck },
  { id: "operations", title: "Operations", icon: ClipboardList },
] as const satisfies ReadonlyArray<{
  id: string;
  title: string;
  icon: ComponentType<{ className?: string }>;
  pluginId?: string;
}>;

function getStatusBadgeClassName(status: ShellConnection["status"]) {
  switch (status) {
    case "CONNECTED":
      return "bg-emerald-600 text-white";
    case "ERROR":
      return "bg-red-600 text-white";
    default:
      return "bg-zinc-500 text-white";
  }
}

function getShellSectionVariant(pathname: string): ShellSectionSkeletonVariant {
  if (pathname.endsWith("/files")) {
    return "file-manager";
  }
  if (pathname.endsWith("/command")) {
    return "command";
  }
  if (pathname.endsWith("/repeater")) {
    return "dashboard";
  }
  if (pathname.endsWith("/processes")) {
    return "list";
  }
  if (pathname.endsWith("/extensions")) {
    return "extensions";
  }
  if (pathname.endsWith("/operations") || pathname.endsWith("/status")) {
    return "list";
  }
  return "dashboard";
}

export function getPendingShellSectionPath({
  currentPathname,
  nextPathname,
  shellId,
}: {
  currentPathname: string;
  nextPathname: string | null | undefined;
  shellId: number;
}) {
  if (!nextPathname || nextPathname === currentPathname) {
    return null;
  }

  const shellPrefix = `/shells/${shellId}/`;
  if (!currentPathname.startsWith(shellPrefix) || !nextPathname.startsWith(shellPrefix)) {
    return null;
  }

  return nextPathname;
}

export function getActiveShellSectionPath({
  currentPathname,
  nextPathname,
  shellId,
}: {
  currentPathname: string;
  nextPathname: string | null | undefined;
  shellId: number;
}) {
  return (
    getPendingShellSectionPath({
      currentPathname,
      nextPathname,
      shellId,
    }) ?? currentPathname
  );
}

export function shouldRevalidateShellManagerLoader({
  currentPathname,
  nextPathname,
  currentShellId,
  nextShellId,
  defaultShouldRevalidate,
}: {
  currentPathname: string;
  nextPathname: string;
  currentShellId: string | undefined;
  nextShellId: string | undefined;
  defaultShouldRevalidate: boolean;
}) {
  if (!currentShellId || !nextShellId || currentShellId !== nextShellId) {
    return defaultShouldRevalidate;
  }

  const currentPrefix = `/shells/${currentShellId}/`;
  const nextPrefix = `/shells/${nextShellId}/`;
  if (currentPathname.startsWith(currentPrefix) && nextPathname.startsWith(nextPrefix)) {
    return false;
  }

  return defaultShouldRevalidate;
}

export function shouldRevalidate(args: ShouldRevalidateFunctionArgs) {
  return shouldRevalidateShellManagerLoader({
    currentPathname: args.currentUrl.pathname,
    nextPathname: args.nextUrl.pathname,
    currentShellId: args.currentParams.shellId,
    nextShellId: args.nextParams.shellId,
    defaultShouldRevalidate: args.defaultShouldRevalidate,
  });
}

function ShellManagerSidebar({
  shell,
  standardPluginIds,
}: {
  shell: ShellConnection;
  standardPluginIds: string[];
}) {
  const location = useLocation();
  const navigation = useNavigation();
  const activePathname = getActiveShellSectionPath({
    currentPathname: location.pathname,
    nextPathname: navigation.location?.pathname,
    shellId: shell.id,
  });
  const visibleSections = useMemo(() => {
    const pluginIdSet = new Set(standardPluginIds);
    return shellSections.filter(
      (section) => !("pluginId" in section) || pluginIdSet.has(section.pluginId),
    );
  }, [standardPluginIds]);

  return (
    <Sidebar variant="inset">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" disabled>
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                <Terminal className="size-4" />
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">Shell</span>
                <span className="truncate text-xs">#{shell.id}</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              {visibleSections.map((item) => {
                const url = `/shells/${shell.id}/${item.id}`;
                const isActive = activePathname.endsWith(`/${item.id}`);
                return (
                  <SidebarMenuItem key={item.id}>
                    <SidebarMenuButton
                      isActive={isActive}
                      render={
                        isActive ? (
                          <div className="flex items-center gap-2" />
                        ) : (
                          <NavLink
                            to={url}
                            viewTransition
                            discover="render"
                            onClick={() => {
                              const trace = markShellNavigationTrace({
                                from: location.pathname,
                                to: url,
                              });
                              logShellRouteDebug("shell-manager", "section click", {
                                traceId: trace?.traceId,
                                shellId: shell.id,
                                from: location.pathname,
                                to: url,
                                targetSection: item.id,
                              });
                            }}
                          />
                        )
                      }
                    >
                      <item.icon />
                      <span>{item.title}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                );
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="Back to Shells">
                  <NavLink
                    className="flex items-center gap-2"
                    to="/shells"
                    viewTransition
                    discover="render"
                    prefetch="intent"
                  >
                    <ArrowLeft />
                    <span>Back to Shells</span>
                  </NavLink>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
    </Sidebar>
  );
}

export default function ShellManagerPage() {
  const { shell, standardPluginIds } = useLoaderData() as {
    shell: Promise<ShellConnection>;
    standardPluginIds: Promise<string[]>;
  };

  return (
    <SidebarProvider>
      <Suspense fallback={<ShellManagerSkeleton />}>
        <ShellManagerLayout shellPromise={shell} standardPluginIdsPromise={standardPluginIds} />
      </Suspense>
    </SidebarProvider>
  );
}

function ShellManagerLayout({
  shellPromise,
  standardPluginIdsPromise,
}: {
  shellPromise: Promise<ShellConnection>;
  standardPluginIdsPromise: Promise<string[]>;
}) {
  const shell = use(shellPromise);
  const standardPluginIds = use(standardPluginIdsPromise);
  const location = useLocation();
  const navigation = useNavigation();
  const outletContext = useMemo(() => ({ shell }), [shell]);
  const pendingShellSectionPath =
    navigation.state === "idle"
      ? null
      : getPendingShellSectionPath({
          currentPathname: location.pathname,
          nextPathname: navigation.location?.pathname,
          shellId: shell.id,
        });
  useEffect(() => {
    const trace = readShellNavigationTrace(location.pathname);
    logShellRouteDebug("shell-manager", "layout commit", {
      traceId: trace?.traceId,
      pathname: location.pathname,
      search: location.search,
      shellId: shell.id,
      navDurationMs: getShellNavigationDurationMs(location.pathname),
    });
  }, [location.pathname, location.search, shell.id]);
  useEffect(() => {
    if (!pendingShellSectionPath) {
      return;
    }

    const trace =
      readShellNavigationTrace(pendingShellSectionPath) ??
      markShellNavigationTrace({
        from: location.pathname,
        to: pendingShellSectionPath,
      });
    logShellRouteDebug("shell-manager", "navigation pending", {
      traceId: trace?.traceId,
      shellId: shell.id,
      from: location.pathname,
      to: pendingShellSectionPath,
      navigationState: navigation.state,
    });
  }, [location.pathname, navigation.state, pendingShellSectionPath, shell.id]);
  return (
    <PluginStatusProvider shellId={shell.id}>
      <ShellManagerSidebar shell={shell} standardPluginIds={standardPluginIds} />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-2 px-4">
            <SidebarTrigger className="-ml-1" />
            <Separator orientation="vertical" className="mr-2 data-[orientation=vertical]:h-4" />
            <div className="flex min-w-0 items-center gap-3">
              <Terminal className="h-5 w-5 shrink-0" />
              <div className="min-w-0">
                <div className="flex min-w-0 items-center gap-2">
                  <span className="truncate font-semibold">{shell.url}</span>
                  <Badge variant="secondary" className={getStatusBadgeClassName(shell.status)}>
                    {shell.status}
                  </Badge>
                  <Badge variant="outline">{shell.language}</Badge>
                </div>
              </div>
            </div>
          </div>
          <nav className="flex flex-1 items-center justify-end gap-2 pr-4">
            <ModeToggle />
            <Button
              variant="ghost"
              size="icon"
              className="size-8"
              nativeButton={false}
              render={
                <a
                  aria-label="GitHub repo"
                  href="https://github.com/ReaJason/No-one"
                  target="_blank"
                  rel="noopener noreferrer"
                />
              }
            >
              <Icons.gitHub className="size-4" aria-hidden="true" />
            </Button>
          </nav>
        </header>
        <NuqsAdapter>
          <main className="min-h-0 flex-1 overflow-hidden">
            {pendingShellSectionPath ? (
              <ShellSectionSkeleton
                label="Loading shell section"
                variant={getShellSectionVariant(pendingShellSectionPath)}
                showStatusCard={false}
              />
            ) : (
              <Suspense
                fallback={
                  <ShellSectionSkeleton
                    label="Loading shell section"
                    variant={getShellSectionVariant(location.pathname)}
                    showStatusCard={false}
                  />
                }
              >
                <Outlet context={outletContext} />
              </Suspense>
            )}
          </main>
        </NuqsAdapter>
        <Toaster />
      </SidebarInset>
    </PluginStatusProvider>
  );
}
