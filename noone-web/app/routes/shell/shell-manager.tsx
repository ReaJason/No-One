import type { ShellConnection } from "@/types/shell-connection";
import type { ComponentType } from "react";

import { Separator } from "@radix-ui/react-separator";
import { ArrowLeft, ClipboardList, Files, Info, Loader, Puzzle, Terminal } from "lucide-react";
import { Suspense, use, useMemo } from "react";
import { type LoaderFunctionArgs, NavLink, Outlet, useLoaderData, useLocation } from "react-router";
import { Toaster } from "sonner";

import { createAuthFetch } from "@/api/api.server";
import { getShellConnectionById } from "@/api/shell-connection-api";
import { Icons } from "@/components/icons";
import { ModeToggle } from "@/components/mode-toggle";
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
  return {
    shell: getShellConnectionById(shellId, authFetch).then((shell) => {
      if (!shell) {
        throw new Response("Shell connection not found", { status: 404 });
      }
      return shell;
    }),
  } as {
    shell: Promise<ShellConnection>;
  };
}

const shellSections = [
  { id: "info", title: "Basic Info", icon: Info },
  { id: "files", title: "Files", icon: Files },
  { id: "command", title: "Command Execute", icon: Terminal },
  { id: "extensions", title: "Extensions", icon: Puzzle },
  { id: "operations", title: "Operations", icon: ClipboardList },
] as const satisfies ReadonlyArray<{
  id: string;
  title: string;
  icon: ComponentType<{ className?: string }>;
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
  if (pathname.endsWith("/extensions")) {
    return "extensions";
  }
  if (pathname.endsWith("/operations")) {
    return "list";
  }
  return "dashboard";
}

function ShellManagerSidebar({ shell }: { shell: ShellConnection }) {
  const location = useLocation();

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
              {shellSections.map((item) => {
                const url = `/shells/${shell.id}/${item.id}`;
                const isActive = location.pathname.endsWith(`/${item.id}`);
                return (
                  <SidebarMenuItem key={item.id}>
                    <SidebarMenuButton
                      isActive={isActive}
                      render={
                        isActive ? (
                          <div className="flex items-center gap-2">
                            <item.icon />
                            <span>{item.title}</span>
                          </div>
                        ) : (
                          <NavLink to={url} viewTransition discover="render" prefetch="intent">
                            {({ isPending }) => (
                              <>
                                {isPending ? <Loader className="animate-spin" /> : <item.icon />}
                                <span>{item.title}</span>
                              </>
                            )}
                          </NavLink>
                        )
                      }
                    />
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
                <SidebarMenuButton
                  tooltip="Back to Shells"
                  render={
                    <NavLink to="/shells" viewTransition discover="render" prefetch="intent">
                      <ArrowLeft />
                      <span>Back to Shells</span>
                    </NavLink>
                  }
                />
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
    </Sidebar>
  );
}

export default function ShellManagerPage() {
  const { shell } = useLoaderData() as {
    shell: Promise<ShellConnection>;
  };

  return (
    <SidebarProvider>
      <Suspense fallback={<ShellManagerSkeleton />}>
        <ShellManagerLayout shellPromise={shell} />
      </Suspense>
    </SidebarProvider>
  );
}

function ShellManagerLayout({ shellPromise }: { shellPromise: Promise<ShellConnection> }) {
  const shell = use(shellPromise);
  const location = useLocation();
  const outletContext = useMemo(() => ({ shell }), [shell]);

  return (
    <>
      <ShellManagerSidebar shell={shell} />
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

        <main className="min-h-0 flex-1 overflow-hidden">
          <Suspense
            key={location.pathname}
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
        <Toaster />
      </SidebarInset>
    </>
  );
}
