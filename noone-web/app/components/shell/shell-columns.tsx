import type { ColumnDef } from "@tanstack/react-table";
import {
  AlertCircle,
  Edit,
  Loader,
  MoreHorizontal,
  Terminal,
  Trash2,
  Wifi,
  WifiOff,
} from "lucide-react";
import { useEffect, useRef } from "react";
import { useFetcher, useNavigate } from "react-router";
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
import type { ShellConnection, ShellLanguage, ShellStatus } from "@/types/shell-connection";

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

function ShellActionsCell({ shell }: { shell: ShellConnection }) {
  const navigate = useNavigate();
  const deleteFetcher = useFetcher<{ success?: boolean; errors?: Record<string, string> }>();
  const lastDeleteStateRef = useRef(deleteFetcher.state);

  useEffect(() => {
    if (lastDeleteStateRef.current !== "submitting" || deleteFetcher.state !== "idle") {
      lastDeleteStateRef.current = deleteFetcher.state;
      return;
    }

    if (deleteFetcher.data?.success) {
      toast.success("Shell connection deleted");
    } else if (deleteFetcher.data?.errors?.general) {
      toast.error(deleteFetcher.data.errors.general);
    }
    lastDeleteStateRef.current = deleteFetcher.state;
  }, [deleteFetcher.data, deleteFetcher.state]);

  const handleEditShell = () => {
    navigate(`/shells/edit/${shell.id}`);
  };

  const handleDeleteShell = () => {
    if (
      !confirm(
        "Are you sure you want to delete this shell connection? This action cannot be undone.",
      )
    )
      return;

    const formData = new FormData();
    formData.set("intent", "delete");
    formData.set("shellId", String(shell.id));
    deleteFetcher.submit(formData, { method: "post", action: "/shells" });
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
          <DropdownMenuItem
            onClick={handleDeleteShell}
            className="text-destructive"
            disabled={deleteFetcher.state !== "idle"}
          >
            {deleteFetcher.state !== "idle" ? (
              <Loader className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Trash2 className="mr-2 h-4 w-4" />
            )}
            Delete
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
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
      header: ({ column }) => <DataTableColumnHeader column={column} label="URL" />,
      cell: ({ row }) => {
        const url = row.getValue("url") as string;
        return (
          <div className="max-w-70 truncate font-medium" title={url}>
            {url}
          </div>
        );
      },
      meta: {
        label: "URL",
        variant: "text",
        placeholder: "Search by URL...",
      },
      enableColumnFilter: true,
      size: 300,
    },
    {
      id: "profileName",
      accessorKey: "profileName",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Profile" />,
      cell: ({ row }) => {
        const profileName = row.getValue("profileName") as string | undefined;
        return profileName ? (
          <Badge variant="outline" className="py-1">
            {profileName}
          </Badge>
        ) : (
          <span className="text-sm text-muted-foreground">--</span>
        );
      },
      size: 120,
      meta: {
        label: "Profile",
      },
    },
    {
      id: "language",
      accessorKey: "language",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Language" />,
      cell: ({ row }) => {
        const language = row.getValue("language") as ShellLanguage;
        const labels: Record<ShellLanguage, string> = {
          java: "Java",
          nodejs: "NodeJs",
          dotnet: "DotNet",
        };
        return <Badge variant="secondary">{labels[language] ?? language}</Badge>;
      },
      meta: {
        label: "Language",
        variant: "select",
        options: [
          { label: "Java", value: "java" },
          { label: "NodeJs", value: "nodejs" },
          { label: "DotNet", value: "dotnet" },
        ],
      },
      enableColumnFilter: true,
      size: 100,
    },
    {
      id: "projectId",
      accessorKey: "projectId",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Project" />,
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
      header: ({ column }) => <DataTableColumnHeader column={column} label="Status" />,
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
      header: ({ column }) => <DataTableColumnHeader column={column} label="Basic Info" />,
      cell: ({ row }) => {
        const basicInfo = row.getValue("basicInfo") as Record<string, any> | undefined;
        if (!basicInfo || Object.keys(basicInfo).length === 0) {
          return <span className="text-sm text-muted-foreground">Unknown</span>;
        }
        const os = basicInfo.os as Record<string, string> | undefined;
        const runtime = basicInfo.runtime as Record<string, string> | undefined;
        const proc = basicInfo.process as Record<string, any> | undefined;

        const items: [string, string][] = [];
        if (os?.name) items.push(["OS", os.name]);
        if (os?.hostname) items.push(["Host", os.hostname]);
        if (runtime?.type)
          items.push(["Runtime", `${runtime.type} ${runtime.version ?? ""}`.trim()]);
        if (proc?.pid) items.push(["PID", String(proc.pid)]);

        if (items.length === 0) {
          return <span className="text-sm text-muted-foreground">Unknown</span>;
        }

        return (
          <table className="text-xs">
            <tbody>
              {items.map(([label, value]) => (
                <tr key={label}>
                  <td className="pr-2 whitespace-nowrap text-muted-foreground">{label}</td>
                  <td className="max-w-40 truncate" title={value}>
                    {value}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        );
      },
      enableSorting: false,
      size: 200,
      meta: {
        label: "Basic Info",
      },
    },
    {
      id: "lastOnlineAt",
      accessorKey: "lastOnlineAt",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Last Online" />,
      cell: ({ row }) => {
        const lastOnlineAt = row.getValue("lastOnlineAt") as string | undefined;
        return lastOnlineAt ? (
          <span className="text-sm text-muted-foreground">{formatDate(lastOnlineAt)}</span>
        ) : (
          <span className="text-sm text-muted-foreground">Never online</span>
        );
      },
      meta: {
        label: "Last Online",
      },
    },
    {
      id: "createdAt",
      accessorKey: "createdAt",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Created Time" />,
      cell: ({ row }) => {
        const createdAt = row.getValue("createdAt") as string | undefined;
        return <span className="text-sm text-muted-foreground">{formatDate(createdAt)}</span>;
      },
      meta: {
        label: "Created At",
      },
    },
    {
      id: "actions",
      enableHiding: false,
      cell: ({ row }) => <ShellActionsCell shell={row.original} />,
      size: 40,
    },
  ];
}
