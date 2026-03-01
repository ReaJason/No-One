import { Separator } from "@radix-ui/react-separator";
import { NuqsAdapter } from "nuqs/adapters/react-router/v7";
import React from "react";
import { Link, Outlet, useParams } from "react-router";
import { Toaster } from "sonner";
import { AppSidebar } from "@/components/app-sidebar";
import { Icons } from "@/components/icons";
import { ModeToggle } from "@/components/mode-toggle";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Button } from "@/components/ui/button";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { useBreadcrumbs } from "@/lib/breadcrumb-utils";
import {Shield} from "lucide-react";

// 模拟项目数据
const mockProjects = {
  "1": { name: "RASP VUL" },
  "2": { name: "Tomcat Server" },
  "3": { name: "TongWeb Server" },
  "4": { name: "Mobile System" },
};

export default function Layout() {
  const params = useParams();
  const projectId = params.projectId;
  const projectName = projectId
    ? mockProjects[projectId as keyof typeof mockProjects]?.name
    : undefined;

  const breadcrumbs = useBreadcrumbs();

  return (
    <SidebarProvider>
      <AppSidebar projectName={projectName} />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center justify-between gap-2">
          <div className="flex items-center gap-2 px-4">
            <SidebarTrigger className="-ml-1" />
            <Separator orientation="vertical" className="mr-2 data-[orientation=vertical]:h-4" />
            <Breadcrumb>
              <BreadcrumbList>
                <BreadcrumbItem className="hidden md:block">
                  <BreadcrumbLink render={<Link to="/">No One</Link>}></BreadcrumbLink>
                </BreadcrumbItem>
                {breadcrumbs.map((crumb, index) => (
                  <React.Fragment key={crumb.id}>
                    <BreadcrumbSeparator className="hidden md:block" />
                    <BreadcrumbItem className="hidden md:block">
                      {index === breadcrumbs.length - 1 ? (
                        <BreadcrumbPage>{crumb.label}</BreadcrumbPage>
                      ) : (
                        <BreadcrumbLink
                          render={<Link to={crumb.to ?? "#"}>{crumb.label}</Link>}
                        ></BreadcrumbLink>
                      )}
                    </BreadcrumbItem>
                  </React.Fragment>
                ))}
              </BreadcrumbList>
            </Breadcrumb>
          </div>
          <nav className="flex flex-1 items-center pr-4 md:justify-end gap-1">
            <div className="flex items-center justify-center px-4 py-2 rounded-full border h-8 transition-colors dark:bg-red-900/20 dark:border-red-800 dark:text-red-300 bg-red-50 border-red-200 text-red-700">
              <Shield className="w-4 h-4 mr-2" />
              <span className="text-sm">
                  For Security Research & Authorized Testing Only
              </span>
            </div>
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
        <main className="min-h-0 flex-1 overflow-auto">
          <NuqsAdapter>
            <Outlet />
          </NuqsAdapter>
        </main>
        <Toaster />
      </SidebarInset>
    </SidebarProvider>
  );
}
