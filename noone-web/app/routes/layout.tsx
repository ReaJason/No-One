import type { Route } from "../+types/root";
import type { User } from "@/types/admin";

import { Separator } from "@radix-ui/react-separator";
import { Shield } from "lucide-react";
import { NuqsAdapter } from "nuqs/adapters/react-router/v7";
import React from "react";
import { data, Link, type LoaderFunctionArgs, Outlet } from "react-router";
import { Toaster } from "sonner";

import { getSession } from "@/api/sessions.server";
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
import { buildAuthState } from "@/lib/authz";
import { useBreadcrumbs } from "@/lib/breadcrumb-utils";
import { authMiddleware } from "@/middleware/auth.server";

export const middleware: Route.MiddlewareFunction[] = [authMiddleware];

export async function loader({ request }: LoaderFunctionArgs) {
  const session = await getSession(request.headers.get("Cookie"));
  const user = (session.get("user") as User | null | undefined) ?? null;

  return data({
    auth: buildAuthState(user),
  });
}

export default function Layout() {
  const breadcrumbs = useBreadcrumbs();
  return (
    <SidebarProvider>
      <AppSidebar />
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
          <nav className="flex flex-1 items-center gap-1 pr-4 md:justify-end">
            <div className="flex h-8 items-center justify-center rounded-full border border-red-200 bg-red-50 px-4 py-2 text-red-700 transition-colors dark:border-red-800 dark:bg-red-900/20 dark:text-red-300">
              <Shield className="mr-2 h-4 w-4" />
              <span className="text-sm">For Security Research & Authorized Testing Only</span>
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
