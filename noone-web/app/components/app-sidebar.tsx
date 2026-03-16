import {
  Cable,
  Command,
  Folder,
  Home,
  Key,
  PlugZap2,
  Settings,
  Shield,
  Sparkles,
  Sprout,
  UserCheck,
  Users,
} from "lucide-react";
import { Link, NavLink, useLocation } from "react-router";

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import { useAuth } from "@/contexts/auth-context";
import { hasAnyAuthority } from "@/lib/authz";

import { NavUser } from "./nav-user";

interface NavItem {
  title: string;
  url: string;
  icon: React.ComponentType<{ className?: string }>;
  visible?: boolean;
  matchPrefix?: boolean;
}

interface NavItemRendererProps {
  item: NavItem;
  location: ReturnType<typeof useLocation>;
}

function NavItemRenderer({ item, location }: NavItemRendererProps) {
  const isActive =
    location.pathname === item.url ||
    (item.matchPrefix !== false &&
      item.url !== "/" &&
      location.pathname.startsWith(`${item.url}/`));

  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        isActive={isActive}
        render={
          isActive ? (
            <div className="flex items-center gap-2">
              <item.icon />
              <span>{item.title}</span>
            </div>
          ) : (
            <NavLink to={item.url} viewTransition>
              <item.icon />
              <span>{item.title}</span>
            </NavLink>
          )
        }
      ></SidebarMenuButton>
    </SidebarMenuItem>
  );
}

function filterVisibleItems(items: NavItem[]): NavItem[] {
  return items.filter((item) => item.visible ?? true);
}

export function AppSidebar() {
  const location = useLocation();
  const { authorities, isAdmin } = useAuth();
  const canAccessSystemMenu = (...codes: string[]) =>
    isAdmin || hasAnyAuthority(authorities, codes);

  const adminItems = filterVisibleItems([
    {
      title: "Users",
      url: "/admin/users",
      icon: Users,
      visible: canAccessSystemMenu("user:list"),
    },
    {
      title: "Roles",
      url: "/admin/roles",
      icon: UserCheck,
      visible: canAccessSystemMenu("role:list"),
    },
    {
      title: "Permissions",
      url: "/admin/permissions",
      icon: Key,
      visible: canAccessSystemMenu("permission:list"),
    },
  ]);

  const topItems = filterVisibleItems([
    {
      title: "Dashboard",
      url: "/",
      icon: Home,
      matchPrefix: false,
    },
    {
      title: "Generator",
      url: `/generator`,
      icon: Sprout,
      visible: canAccessSystemMenu("shell:generate"),
    },
  ]);

  const projectsItem: NavItem = {
    title: "Projects",
    url: "/projects",
    icon: Folder,
  };

  const shellsItem: NavItem = {
    title: "Shells",
    url: "/shells",
    icon: Cable,
  };

  const managementItems = filterVisibleItems([
    projectsItem,
    shellsItem,
    {
      title: "Profiles",
      url: "/profiles",
      icon: Sparkles,
      visible: canAccessSystemMenu("profile:list"),
    },
    {
      title: "Plugins",
      url: "/plugins",
      icon: PlugZap2,
      visible: canAccessSystemMenu("plugin:list"),
    },
    {
      title: "Audit",
      url: "/audit",
      icon: Shield,
      visible: canAccessSystemMenu("auth:log:read"),
    },
    {
      title: "Settings",
      url: "/settings",
      icon: Settings,
    },
  ]);

  return (
    <Sidebar variant="inset">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" render={<Link to="/" viewTransition />}>
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                <Command className="size-4" />
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">No One</span>
                <span className="truncate text-xs">Professional</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              {topItems.map((item) => (
                <NavItemRenderer key={item.title} item={item} location={location} />
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        {adminItems.length > 0 && (
          <SidebarGroup>
            <SidebarGroupLabel>Admin</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {adminItems.map((item) => (
                  <NavItemRenderer key={item.title} item={item} location={location} />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        )}

        {managementItems.length > 0 && (
          <SidebarGroup>
            <SidebarGroupLabel>Management</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {managementItems.map((item) => (
                  <NavItemRenderer key={item.title} item={item} location={location} />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        )}
      </SidebarContent>

      <SidebarFooter>
        <NavUser />
      </SidebarFooter>
    </Sidebar>
  );
}
