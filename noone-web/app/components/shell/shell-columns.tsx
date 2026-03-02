import type { ColumnDef } from "@tanstack/react-table";
import { AlertCircle, Edit, MoreHorizontal, Terminal, Trash2, Wifi, WifiOff } from "lucide-react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { formatDate } from "@/lib/format";
import { deleteShellConnection } from "@/api/shell-connection-api";
import type { ShellConnection, ShellStatus } from "@/types/shell-connection";

const statusLabels: Record<ShellStatus, string> = {
  CONNECTED: "Connected",
  DISCONNECTED: "Disconnected",
  ERROR: "Error",
};

const statusColors: Record<ShellStatus, string> = {
  CONNECTED: "bg-green-100 text-green-800 hover:bg-green-100",
  DISCONNECTED: "bg-gray-100 text-gray-800 hover:bg-gray-100",
  ERROR: "bg-red-100 text-red-800 hover:bg-red-100",
};

interface GetShellColumnsOptions {
  projectMap: Map<number, string>;
  projectOptions: Array<{ label: string; value: string }>;
}

export function getShellColumns({
  projectMap,
  projectOptions,
}: GetShellColumnsOptions): ColumnDef<ShellConnection>[] {
  return [
    {
      id: "select",
      header: ({ table }) => (
        <Checkbox
          checked={table.getIsAllPageRowsSelected() || table.getIsSomePageRowsSelected()}
          onCheckedChange={(value: any) => table.toggleAllPageRowsSelected(!!value)}
          aria-label="Select all"
          className="translate-y-0.5"
        />
      ),
      cell: ({ row }) => (
        <Checkbox
          checked={row.getIsSelected()}
          onCheckedChange={(value: any) => row.toggleSelected(!!value)}
          aria-label="Select row"
          className="translate-y-0.5"
        />
      ),
      enableSorting: false,
      enableHiding: false,
      size: 40,
    },
    {
      id: "url",
      accessorKey: "url",
      header: ({ column }) => <DataTableColumnHeader column={column} title="URL" />,
      cell: ({ row }) => {
        const url = row.getValue("url") as string;
        return (
          <div className="max-w-70 truncate font-medium" title={url}>
            {url}
          </div>
        );
      },
      size: 300,
    },
    {
      id: "projectId",
      accessorKey: "projectId",
      header: ({ column }) => <DataTableColumnHeader column={column} title="Project" />,
      cell: ({ row }) => {
        const projectId = row.getValue("projectId") as number | null | undefined;
        if (typeof projectId !== "number") {
          return <span className="text-sm text-muted-foreground">No project</span>;
        }
        const projectName = projectMap.get(projectId);
        return (
          <Badge variant="outline" className="py-1">
            {projectName ?? `#${projectId}`}
          </Badge>
        );
      },
      meta: {
        label: "Project",
        variant: "select",
        options: projectOptions,
      },
      enableColumnFilter: true,
    },
    {
      id: "status",
      accessorKey: "status",
      header: ({ column }) => <DataTableColumnHeader column={column} title="Status" />,
      cell: ({ row }) => {
        const status = row.getValue("status") as ShellStatus;
        const icon =
          status === "CONNECTED" ? (
            <Wifi className="h-4 w-4 text-green-600" />
          ) : status === "DISCONNECTED" ? (
            <WifiOff className="h-4 w-4 text-gray-600" />
          ) : (
            <AlertCircle className="h-4 w-4 text-red-600" />
          );

        return (
          <div className="flex items-center gap-2">
            {icon}
            <Badge className={statusColors[status]}>{statusLabels[status]}</Badge>
          </div>
        );
      },
      meta: {
        label: "Status",
        variant: "select",
        options: [
          { label: "Connected", value: "CONNECTED" },
          { label: "Disconnected", value: "DISCONNECTED" },
          { label: "Error", value: "ERROR" },
        ],
      },
      enableColumnFilter: true,
    },
    {
      id: "basicInfo",
      accessorKey: "basicInfo",
      header: ({ column }) => <DataTableColumnHeader column={column} title="System Info" />,
      cell: ({ row }) => {
        const basicInfo = row.getValue("basicInfo") as Record<string, string> | undefined;
        if (!basicInfo || Object.keys(basicInfo).length === 0) {
          return <span className="text-sm text-muted-foreground">Unknown</span>;
        }
        const os = basicInfo.os;
        const arch = basicInfo.arch;
        const runtime = basicInfo.runtimeType + " " + basicInfo.runtimeVersion;
        return (
          <div className="flex flex-col gap-0.5">
            {(os || arch) && (
              <div className="flex items-center gap-1">
                {os && (
                  <Badge variant="secondary" className="px-1.5 py-0 text-xs">
                    {os}
                  </Badge>
                )}
                {arch && (
                  <Badge variant="secondary" className="px-1.5 py-0 text-xs">
                    {arch}
                  </Badge>
                )}
              </div>
            )}
            {runtime && (
              <span className="text-xs text-muted-foreground">{runtime}</span>
            )}
          </div>
        );
      },
      enableSorting: false,
    },
    {
      id: "connectTime",
      accessorKey: "connectTime",
      header: ({ column }) => <DataTableColumnHeader column={column} title="Last Connected" />,
      cell: ({ row }) => {
        const connectTime = row.getValue("connectTime") as string | undefined;
        return connectTime ? (
          <span className="text-sm text-muted-foreground">{formatDate(connectTime)}</span>
        ) : (
          <span className="text-sm text-muted-foreground">Never connected</span>
        );
      },
    },
    {
      id: "createTime",
      accessorKey: "createTime",
      header: ({ column }) => <DataTableColumnHeader column={column} title="Created Time" />,
      cell: ({ row }) => {
        const createTime = row.getValue("createTime") as string | undefined;
        return <span className="text-sm text-muted-foreground">{formatDate(createTime)}</span>;
      },
    },
    {
      id: "actions",
      enableHiding: false,
      cell: ({ row }) => {
        const shell = row.original;
        const navigate = useNavigate();

        const handleEditShell = () => {
          navigate(`/shells/edit/${shell.id}`);
        };

        const handleDeleteShell = async () => {
          if (
            !confirm(
              "Are you sure you want to delete this shell connection? This action cannot be undone.",
            )
          )
            return;
          try {
            await deleteShellConnection(shell.id);
            toast.success("Shell connection deleted");
            navigate(0);
          } catch (e: any) {
            toast.error(e?.message || "Failed to delete shell connection");
          }
        };

        const handleConnectShell = () => {
          window.open(`/shells/${shell.id}/connect`, "_blank");
        };

        return (
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button variant="ghost" className="h-8 w-8 p-0">
                  <span className="sr-only">Open menu</span>
                  <MoreHorizontal className="h-4 w-4" />
                </Button>
              }
            ></DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuGroup>
                <DropdownMenuLabel>Actions</DropdownMenuLabel>
                <DropdownMenuItem onClick={handleEditShell}>
                  <Edit className="mr-2 h-4 w-4" />
                  Edit
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleConnectShell}>
                  <Terminal className="mr-2 h-4 w-4" />
                  Connect
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={handleDeleteShell} className="text-destructive">
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete
                </DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
        );
      },
      size: 40,
    },
  ];
}
