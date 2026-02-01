import {
  Activity,
  Calendar,
  Clock,
  Filter,
  Globe,
  Plus,
  Search,
  Server,
  Terminal,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useParams } from "react-router";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface ShellConnection {
  id: string;
  url: string;
  shellType: "webshell" | "reverse" | "bind";
  createTime: string;
  connectTime: string;
  status: "connected" | "disconnected" | "error";
  updateTime: string;
  group: string;
  projectId?: string;
}

// 模拟数据
const mockShells: ShellConnection[] = [
  {
    id: "1",
    url: "http://192.168.1.100:8080/shell.jsp",
    shellType: "webshell",
    createTime: "2024-01-15 10:30:00",
    connectTime: "2024-01-15 10:32:15",
    status: "connected",
    updateTime: "2024-01-15 15:45:30",
    group: "Production",
    projectId: "1",
  },
  {
    id: "2",
    url: "192.168.1.101:4444",
    shellType: "reverse",
    createTime: "2024-01-16 09:15:00",
    connectTime: "2024-01-16 09:16:22",
    status: "connected",
    updateTime: "2024-01-16 14:20:10",
    group: "Testing",
    projectId: "1",
  },
  {
    id: "3",
    url: "http://10.0.0.50:9090/cmd.php",
    shellType: "webshell",
    createTime: "2024-01-17 14:20:00",
    connectTime: "2024-01-17 14:21:05",
    status: "error",
    updateTime: "2024-01-17 16:30:45",
    group: "Development",
    projectId: "2",
  },
  {
    id: "4",
    url: "192.168.2.200:5555",
    shellType: "bind",
    createTime: "2024-01-18 11:45:00",
    connectTime: "2024-01-18 11:46:30",
    status: "disconnected",
    updateTime: "2024-01-18 13:15:20",
    group: "Production",
    projectId: "1",
  },
];

const statusColors = {
  connected: "bg-green-500",
  disconnected: "bg-gray-500",
  error: "bg-red-500",
};

const statusLabels = {
  connected: "Connected",
  disconnected: "Disconnected",
  error: "Error",
};

const shellTypeLabels = {
  webshell: "Web Shell",
  reverse: "Reverse Shell",
  bind: "Bind Shell",
};

interface ShellListProps {
  onConnectShell: (shell: ShellConnection) => void;
}

export default function ShellList({ onConnectShell }: ShellListProps) {
  const params = useParams();
  const projectId = params.projectId;

  const [shells] = useState<ShellConnection[]>(mockShells);
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedGroup, setSelectedGroup] = useState<string>("all");
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 8;

  // 根据项目ID过滤shells
  const projectShells = useMemo(() => {
    if (projectId) {
      return shells.filter((shell) => shell.projectId === projectId);
    }
    return shells;
  }, [shells, projectId]);

  // 获取所有分组
  const groups = useMemo(() => {
    const uniqueGroups = [
      ...new Set(projectShells.map((shell) => shell.group)),
    ];
    return uniqueGroups;
  }, [projectShells]);

  const filteredAndPaginatedShells = useMemo(() => {
    // 根据搜索词和分组过滤
    const filtered = projectShells.filter((shell) => {
      const matchesSearch =
        shell.url.toLowerCase().includes(searchTerm.toLowerCase()) ||
        shell.group.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesGroup =
        selectedGroup === "all" || shell.group === selectedGroup;
      return matchesSearch && matchesGroup;
    });

    // 计算分页
    const totalPages = Math.ceil(filtered.length / itemsPerPage);
    const startIndex = (currentPage - 1) * itemsPerPage;
    const endIndex = startIndex + itemsPerPage;
    const paginatedShells = filtered.slice(startIndex, endIndex);

    return {
      shells: paginatedShells,
      totalPages,
      totalCount: filtered.length,
    };
  }, [projectShells, searchTerm, selectedGroup, currentPage]);

  const handleSearch = (value: string) => {
    setSearchTerm(value);
    setCurrentPage(1);
  };

  const handleGroupFilter = (value: string) => {
    setSelectedGroup(value);
    setCurrentPage(1);
  };

  return (
    <div className="container mx-auto p-6 max-w-7xl">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-balance">
            {projectId ? "Project Shell Connections" : "All Shell Connections"}
          </h1>
          <p className="text-muted-foreground mt-2">
            Manage and monitor your shell connections
          </p>
        </div>
        <Button className="flex items-center gap-2">
          <Plus className="h-4 w-4" />
          Add Connection
        </Button>
      </div>

      {/* 搜索和过滤 */}
      <div className="flex flex-col sm:flex-row gap-4 mb-6">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by URL or group..."
            value={searchTerm}
            onChange={(e) => handleSearch(e.target.value)}
            className="pl-10"
          />
        </div>

        <div className="flex items-center gap-2">
          <Filter className="h-4 w-4 text-muted-foreground" />
          <Select value={selectedGroup} onValueChange={handleGroupFilter}>
            <SelectTrigger className="w-40">
              <SelectValue placeholder="Filter by group" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Groups</SelectItem>
              {groups.map((group) => (
                <SelectItem key={group} value={group}>
                  {group}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {searchTerm || selectedGroup !== "all" ? (
        <p className="text-sm text-muted-foreground mb-4">
          Found {filteredAndPaginatedShells.totalCount} connections
        </p>
      ) : null}

      {/* Shell连接列表 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {filteredAndPaginatedShells.shells.map((shell) => (
          <Card
            key={shell.id}
            className="hover:shadow-lg transition-all duration-200 cursor-pointer border-l-4"
            style={{ borderLeftColor: statusColors[shell.status] }}
            onClick={() => onConnectShell(shell)}
          >
            <CardHeader className="pb-3">
              <div className="flex items-start justify-between">
                <CardTitle className="text-lg text-balance flex items-center gap-2">
                  <Globe className="h-5 w-5" />
                  {shell.url}
                </CardTitle>
                <Badge
                  variant="secondary"
                  className={`${statusColors[shell.status]} text-white`}
                >
                  {statusLabels[shell.status]}
                </Badge>
              </div>
            </CardHeader>

            <CardContent className="space-y-3">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div className="flex items-center gap-2">
                  <Terminal className="h-4 w-4 text-muted-foreground" />
                  <span className="font-medium">Type:</span>
                  <Badge variant="outline">
                    {shellTypeLabels[shell.shellType]}
                  </Badge>
                </div>

                <div className="flex items-center gap-2">
                  <Server className="h-4 w-4 text-muted-foreground" />
                  <span className="font-medium">Group:</span>
                  <Badge variant="secondary">{shell.group}</Badge>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-2 text-sm text-muted-foreground">
                <div className="flex items-center gap-2">
                  <Calendar className="h-4 w-4" />
                  <span>Created: {shell.createTime}</span>
                </div>

                <div className="flex items-center gap-2">
                  <Activity className="h-4 w-4" />
                  <span>Connected: {shell.connectTime}</span>
                </div>

                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4" />
                  <span>Updated: {shell.updateTime}</span>
                </div>
              </div>

              <div className="pt-2 border-t">
                <Button
                  variant="outline"
                  size="sm"
                  className="w-full bg-transparent"
                  onClick={(e) => {
                    e.stopPropagation();
                    onConnectShell(shell);
                  }}
                >
                  <Terminal className="h-4 w-4 mr-2" />
                  Connect
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 空状态 */}
      {filteredAndPaginatedShells.shells.length === 0 && (
        <div className="text-center py-12">
          <div className="text-muted-foreground mb-4">
            <Terminal className="h-12 w-12 mx-auto mb-4 opacity-50" />
            {searchTerm || selectedGroup !== "all" ? (
              <>
                <p className="text-lg">No matching connections found</p>
                <p className="text-sm">
                  Try adjusting your search or filter criteria
                </p>
              </>
            ) : (
              <>
                <p className="text-lg">No shell connections found</p>
                <p className="text-sm">
                  Click the button above to add your first connection
                </p>
              </>
            )}
          </div>
        </div>
      )}

      {/* 分页 */}
      {filteredAndPaginatedShells.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-8">
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    if (currentPage > 1) setCurrentPage(currentPage - 1);
                  }}
                />
              </PaginationItem>

              {Array.from(
                { length: filteredAndPaginatedShells.totalPages },
                (_, i) => i + 1,
              )
                .filter(
                  (page) =>
                    page === 1 ||
                    page === filteredAndPaginatedShells.totalPages ||
                    Math.abs(page - currentPage) <= 1,
                )
                .map((page, index, array) => (
                  <PaginationItem key={page}>
                    {index > 0 && array[index - 1] !== page - 1 && (
                      <span className="px-2">...</span>
                    )}
                    <PaginationLink
                      href="#"
                      isActive={page === currentPage}
                      onClick={(e) => {
                        e.preventDefault();
                        setCurrentPage(page);
                      }}
                    >
                      {page}
                    </PaginationLink>
                  </PaginationItem>
                ))}

              <PaginationItem>
                <PaginationNext
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    if (currentPage < filteredAndPaginatedShells.totalPages) {
                      setCurrentPage(currentPage + 1);
                    }
                  }}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
      )}
    </div>
  );
}
