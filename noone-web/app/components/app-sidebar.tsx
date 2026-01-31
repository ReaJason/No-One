import {
    ArrowLeft,
    Cable,
    Command,
    Folder,
    Home,
    Key,
    Loader,
    PlugZap2,
    Settings,
    Shield,
    Sparkles,
    Sprout,
    UserCheck,
    Users,
} from "lucide-react";
import {NavLink, useLocation, useParams} from "react-router";
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
import {NavUser} from "./nav-user";

interface NavItem {
  title: string;
  url: string;
  icon: React.ComponentType<{ className?: string }>;
  condition?: () => boolean;
}

interface NavItemRendererProps {
  item: NavItem;
  location: ReturnType<typeof useLocation>;
}

function NavItemRenderer({ item, location }: NavItemRendererProps) {
  const isActive = location.pathname === item.url;

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
              {({ isPending }) => (
                <>
                  {isPending ? (
                    <Loader className="animate-spin" />
                  ) : (
                    <item.icon />
                  )}
                  <span>{item.title}</span>
                </>
              )}
            </NavLink>
          )
        }
      ></SidebarMenuButton>
    </SidebarMenuItem>
  );
}

const adminItems: NavItem[] = [
  {
    title: "Users",
    url: "/admin/users",
    icon: Users,
  },
  {
    title: "Roles",
    url: "/admin/roles",
    icon: UserCheck,
  },
  {
    title: "Permissions",
    url: "/admin/permissions",
    icon: Key,
  },
];

const globalItems: NavItem[] = [
  {
    title: "Generator",
    url: `/generator`,
    icon: Sprout,
  },
  {
    title: "Profiles",
    url: "/profiles",
    icon: Sparkles,
  },
  {
    title: "Plugins",
    url: "/plugins",
    icon: PlugZap2,
  },
  {
    title: "Audit",
    url: "/audit",
    icon: Shield,
  },
  {
    title: "Settings",
    url: "/settings",
    icon: Settings,
  },
];

interface AppSidebarProps {
  projectName?: string;
}

export function AppSidebar({ projectName }: AppSidebarProps) {
  const params = useParams();
  const location = useLocation();
  const projectId = params.projectId;

  const dashboardItem: NavItem = {
    title: "Dashboard",
    url: "/",
    icon: Home,
  };

  const projectItems: NavItem[] = projectId
    ? [
        {
          title: "Shells",
          url: `/projects/${projectId}/shells`,
          icon: Cable,
        },
      ]
    : [];

  const backToProjectsItem: NavItem = {
    title: "Back to Projects",
    url: "/projects",
    icon: ArrowLeft,
    condition: () => !!projectId,
  };

  const projectsItem: NavItem = {
    title: "Projects",
    url: "/projects",
    icon: Folder,
    condition: () => !projectId,
  };

  const shellsItem: NavItem = {
    title: "Shells",
    url: "/shells",
    icon: Cable,
    condition: () => !projectId,
  };

  return (
    <Sidebar variant="inset">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <a href="/" rel="noopener noreferrer">
              <SidebarMenuButton size="lg">
                <div className="bg-sidebar-primary text-sidebar-primary-foreground flex aspect-square size-8 items-center justify-center rounded-lg">
                  <Command className="size-4" />
                </div>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-medium">No One</span>
                  <span className="truncate text-xs">Professional</span>
                </div>
              </SidebarMenuButton>
            </a>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <NavItemRenderer item={dashboardItem} location={location} />
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        <SidebarGroup>
          <SidebarGroupLabel>Admin</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {adminItems.map((item) => (
                <NavItemRenderer
                  key={item.title}
                  item={item}
                  location={location}
                />
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        {projectId && (
          <>
            <SidebarGroup>
              <SidebarGroupContent>
                <SidebarMenu>
                  <NavItemRenderer
                    item={backToProjectsItem}
                    location={location}
                  />
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>

            <SidebarGroup>
              <SidebarGroupLabel>
                {projectName || `Project ${projectId}`}
              </SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {projectItems.map((item) => (
                    <NavItemRenderer
                      key={item.title}
                      item={item}
                      location={location}
                    />
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          </>
        )}
        <SidebarGroup>
          <SidebarGroupLabel>Global</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {[projectsItem, shellsItem].map((item) =>
                item.condition?.() ? (
                  <NavItemRenderer
                    key={item.title}
                    item={item}
                    location={location}
                  />
                ) : null,
              )}
              {globalItems.map((item) => (
                <NavItemRenderer
                  key={item.title}
                  item={item}
                  location={location}
                />
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <NavUser />
      </SidebarFooter>
    </Sidebar>
  );
}
