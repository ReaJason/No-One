import type { ColumnDef } from "@tanstack/react-table";
import {
  AlertCircle,
  Edit,
  MoreHorizontal,
  Terminal,
  Trash2,
  Wifi,
  WifiOff,
} from "lucide-react";
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
import { deleteShellConnection } from "@/lib/shell-connection-api";
import type {
  ShellConnection,
  ShellStatus,
  ShellType,
} from "@/types/shell-connection";

const shellTypeLabels: Record<ShellType, string> = {
  WEBSHELL: "Webshell",
  REVERSE: "Reverse",
  BIND: "Bind",
};

const shellTypeColors: Record<ShellType, string> = {
  WEBSHELL: "bg-blue-100 text-blue-800 hover:bg-blue-100",
  REVERSE: "bg-green-100 text-green-800 hover:bg-green-100",
  BIND: "bg-purple-100 text-purple-800 hover:bg-purple-100",
};

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

export const shellConnectionColumns: ColumnDef<ShellConnection>[] = [
  {
    id: "select",
    header: ({ table }) => (
      <Checkbox
        checked={
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && "indeterminate")
        }
        onCheckedChange={(value: any) =>
          table.toggleAllPageRowsSelected(!!value)
        }
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
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="URL" />
    ),
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
    id: "group",
    accessorKey: "group",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Group" />
    ),
    cell: ({ row }) => {
      const group = row.getValue("group") as string | undefined;
      return group ? (
        <Badge variant="secondary" className="py-1">
          {group}
        </Badge>
      ) : (
        <span className="text-sm text-muted-foreground">Unassigned</span>
      );
    },
    meta: {
      label: "Group",
      variant: "text",
      placeholder: "Filter by group...",
    },
    enableColumnFilter: true,
  },
  {
    id: "projectId",
    accessorKey: "projectId",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Project" />
    ),
    cell: ({ row }) => {
      const projectId = row.getValue("projectId") as number | null | undefined;
      return typeof projectId === "number" ? (
        <Badge variant="outline" className="py-1">
          #{projectId}
        </Badge>
      ) : (
        <span className="text-sm text-muted-foreground">No project</span>
      );
    },
  },
  {
    id: "shellType",
    accessorKey: "shellType",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Type" />
    ),
    cell: ({ row }) => {
      const shellType = row.getValue("shellType") as ShellType;
      return (
        <Badge className={shellTypeColors[shellType]}>
          {shellTypeLabels[shellType]}
        </Badge>
      );
    },
  },
  {
    id: "status",
    accessorKey: "status",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Status" />
    ),
    cell: ({ row }) => {
      const status = row.getValue("status") as ShellStatus;
      const icon =
        status === "CONNECTED" ? (
          <Wifi className="w-4 h-4 text-green-600" />
        ) : status === "DISCONNECTED" ? (
          <WifiOff className="w-4 h-4 text-gray-600" />
        ) : (
          <AlertCircle className="w-4 h-4 text-red-600" />
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
    id: "connectTime",
    accessorKey: "connectTime",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Last Connected" />
    ),
    cell: ({ row }) => {
      const connectTime = row.getValue("connectTime") as string | undefined;
      return connectTime ? (
        <span className="text-sm text-muted-foreground">
          {formatDate(connectTime)}
        </span>
      ) : (
        <span className="text-sm text-muted-foreground">Never connected</span>
      );
    },
  },
  {
    id: "createTime",
    accessorKey: "createTime",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Created Time" />
    ),
    cell: ({ row }) => {
      const createTime = row.getValue("createTime") as string | undefined;
      return (
        <span className="text-sm text-muted-foreground">
          {formatDate(createTime)}
        </span>
      );
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
        window.open(`/shells/${shell.id}`, "_blank");
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
              <DropdownMenuItem
                onClick={handleDeleteShell}
                className="text-destructive"
              >
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
