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
import { Suspense, use, useMemo } from "react";
import { type LoaderFunctionArgs, NavLink, Outlet, useLoaderData, useLocation } from "react-router";
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

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = params.shellId as string | undefined;
  if (!shellId) {
    throw new Response("Invalid shell ID", { status: 400 });
  }
  const authFetch = createAuthFetch(request, context);
  const shellPromise = getShellConnectionById(shellId, authFetch).then((shell) => {
    if (!shell) {
      throw new Response("Shell connection not found", { status: 404 });
    }
    return shell;
  });
  return {
    shell: shellPromise,
    standardPluginIds: shellPromise.then(async (shell) => {
      const result = await pluginApi.getPlugins(
        { type: "Standard", language: shell.language },
        authFetch,
      );
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

function ShellManagerSidebar({
  shell,
  standardPluginIds,
}: {
  shell: ShellConnection;
  standardPluginIds: string[];
}) {
  const location = useLocation();
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
                const isActive = location.pathname.endsWith(`/${item.id}`);
                return (
                  <SidebarMenuItem key={item.id}>
                    <SidebarMenuButton
                      isActive={isActive}
                      render={
                        isActive ? (
                          <div className="flex items-center gap-2" />
                        ) : (
                          <NavLink to={url} viewTransition discover="render" prefetch="intent" />
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
  const outletContext = useMemo(() => ({ shell }), [shell]);

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
          </main>
        </NuqsAdapter>
        <Toaster />
      </SidebarInset>
    </PluginStatusProvider>
  );
}
