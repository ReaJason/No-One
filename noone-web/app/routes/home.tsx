import { Activity, Cable, Calendar, Folder, TrendingUp } from "lucide-react";
import { useState } from "react";
import { Link, type LoaderFunctionArgs } from "react-router";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { authUtils } from "@/lib/auth";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";

interface Project {
  id: string;
  name: string;
  creator: string;
  hostCount: number;
  createdAt: string;
}

interface DashboardStats {
  totalProjects: number;
  totalShells: number;
  activeConnections: number;
  recentActivity: number;
}

// 模拟数据
const mockProjects: Project[] = [
  {
    id: "1",
    name: "RASP VUL",
    creator: "张三",
    hostCount: 12,
    createdAt: "2024-01-15",
  },
  {
    id: "2",
    name: "Tomcat Server",
    creator: "李四",
    hostCount: 8,
    createdAt: "2024-01-20",
  },
  {
    id: "3",
    name: "TongWeb Server",
    creator: "王五",
    hostCount: 15,
    createdAt: "2024-02-01",
  },
  {
    id: "4",
    name: "Mobile System",
    creator: "赵六",
    hostCount: 6,
    createdAt: "2024-02-10",
  },
];

export async function loader({ request }: LoaderFunctionArgs) {
  const { user, token } = await authUtils.getAuthFromRequest(request);
  if (!user) {
    throw new Response("Unauthorized", {
      status: 302,
      headers: { Location: "/auth/login?returnTo=/" },
    });
  }
  return { user, token };
}

const dashboardStats: DashboardStats = {
  totalProjects: 4,
  totalShells: 41,
  activeConnections: 28,
  recentActivity: 15,
};

export const handle = createBreadcrumb(() => ({
  id: "home",
  label: "Home",
  to: "/",
}));

export default function HomePage() {
  const [projects] = useState<Project[]>(mockProjects);

  return (
    <div className="container mx-auto p-6 max-w-7xl">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-balance">Dashboard</h1>
        <p className="text-muted-foreground mt-2">
          Welcome to Shell Manager - Monitor and manage your shell connections
        </p>
      </div>

      {/* 统计卡片 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Total Projects
            </CardTitle>
            <Folder className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {dashboardStats.totalProjects}
            </div>
            <p className="text-xs text-muted-foreground">
              Active projects in system
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Shell Connections
            </CardTitle>
            <Cable className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {dashboardStats.totalShells}
            </div>
            <p className="text-xs text-muted-foreground">
              Total shell connections
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Active Connections
            </CardTitle>
            <Activity className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {dashboardStats.activeConnections}
            </div>
            <p className="text-xs text-muted-foreground">Currently connected</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Recent Activity
            </CardTitle>
            <TrendingUp className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {dashboardStats.recentActivity}
            </div>
            <p className="text-xs text-muted-foreground">Actions in last 24h</p>
          </CardContent>
        </Card>
      </div>

      {/* 快速操作 */}
      <div className="gap-6 mb-8">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Folder className="h-5 w-5" />
              Recent Projects
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {projects.slice(0, 4).map((project) => (
                <Link key={project.id} to={`/projects/${project.id}/shells`}>
                  <Card className="hover:shadow-md transition-shadow cursor-pointer">
                    <CardContent className="p-4">
                      <div className="flex items-center justify-between mb-2">
                        <h3 className="font-medium text-balance">
                          {project.name}
                        </h3>
                        <Badge variant="secondary">
                          {project.hostCount} shells
                        </Badge>
                      </div>
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Calendar className="h-3 w-3" />
                        <span>{project.createdAt}</span>
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              ))}
            </div>
            <div className="mt-4">
              <Link to="/projects">
                <Button variant="outline" className="w-full bg-transparent">
                  View All Projects
                </Button>
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 系统状态 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Activity className="h-5 w-5" />
            System Status
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600 mb-1">
                98.5%
              </div>
              <div className="text-sm text-muted-foreground">System Uptime</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600 mb-1">2.3s</div>
              <div className="text-sm text-muted-foreground">
                Avg Response Time
              </div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-orange-600 mb-1">
                156MB
              </div>
              <div className="text-sm text-muted-foreground">Memory Usage</div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
