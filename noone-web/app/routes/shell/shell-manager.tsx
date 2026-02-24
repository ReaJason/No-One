import { Separator } from "@radix-ui/react-separator";
import { ArrowLeft, Info, Puzzle, Terminal } from "lucide-react";
import type { ComponentType } from "react";
import { Link, type LoaderFunctionArgs, useLoaderData, useSearchParams } from "react-router";
import { Toaster } from "sonner";
import { Icons } from "@/components/icons";
import { ModeToggle } from "@/components/mode-toggle";
import ShellManager, { type ShellManagerSection } from "@/components/shell/shell-manager";
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
import { getShellConnectionById } from "@/api/shell-connection-api";
import type { ShellConnection } from "@/types/shell-connection";

export async function loader({ params }: LoaderFunctionArgs) {
  const shellId = params.shellId as string | undefined;
  if (!shellId) {
    throw new Response("Invalid shell ID", { status: 400 });
  }
  const shell = await getShellConnectionById(shellId);
  if (!shell) {
    throw new Response("Shell connection not found", { status: 404 });
  }
  return { shell } as {
    shell: ShellConnection;
  };
}

const shellSections = [
  { id: "info", title: "Basic Info", icon: Info },
  { id: "command", title: "Command Execute", icon: Terminal },
  { id: "extensions", title: "Extensions", icon: Puzzle },
] as const satisfies ReadonlyArray<{
  id: ShellManagerSection;
  title: string;
  icon: ComponentType<{ className?: string }>;
}>;

function coerceShellSection(value: string | null): ShellManagerSection {
  const ids = new Set<ShellManagerSection>(shellSections.map((s) => s.id));
  if (value && ids.has(value as ShellManagerSection)) {
    return value as ShellManagerSection;
  }
  return "info";
}

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

function ShellManagerSidebar({
  shell,
  section,
  onSectionChange,
}: {
  shell: ShellConnection;
  section: ShellManagerSection;
  onSectionChange: (section: ShellManagerSection) => void;
}) {
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
              {shellSections.map((item) => (
                <SidebarMenuItem key={item.id}>
                  <SidebarMenuButton
                    isActive={section === item.id}
                    onClick={() => onSectionChange(item.id)}
                  >
                    <item.icon />
                    <span>{item.title}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="Back to Shells">
                  <ArrowLeft />
                  <Link to="/shells" viewTransition>
                    Back to Shells
                  </Link>
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
  const { shell } = useLoaderData() as {
    shell: ShellConnection;
  };

  const [searchParams, setSearchParams] = useSearchParams();
  const section = coerceShellSection(searchParams.get("section"));

  const handleSectionChange = (next: ShellManagerSection) => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("section", next);
    setSearchParams(nextParams, { replace: true });
  };

  return (
    <SidebarProvider>
      <ShellManagerSidebar shell={shell} section={section} onSectionChange={handleSectionChange} />
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
            <Button variant="ghost" size="icon" className="size-8">
              <Link
                aria-label="GitHub repo"
                to="https://github.com/ReaJason/No-one"
                target="_blank"
                rel="noopener noreferrer"
              >
                <Icons.gitHub className="size-4" aria-hidden="true" />
              </Link>
            </Button>
          </nav>
        </header>

        <main className="min-h-0 flex-1 overflow-hidden">
          <ShellManager shell={shell} section={section} />
        </main>
        <Toaster />
      </SidebarInset>
    </SidebarProvider>
  );
}
