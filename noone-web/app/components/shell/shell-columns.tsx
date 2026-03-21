import type { PingShellResponse } from "@/api/shell-connection-api";
import type { ShellConnection, ShellLanguage, ShellStatus } from "@/types/shell-connection";
import type { ColumnDef } from "@tanstack/react-table";

import {
  AlertCircle,
  ClipboardList,
  Edit,
  Loader,
  MoreHorizontal,
  Terminal,
  Trash2,
  Wifi,
  WifiOff,
  Zap,
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
import { Spinner } from "@/components/ui/spinner";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";

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

function PingButton({ shellId }: { shellId: number }) {
  const fetcher = useFetcher<{
    success?: boolean;
    ping?: PingShellResponse;
    errors?: Record<string, string>;
  }>();
  const isPinging = fetcher.state !== "idle";
  const lastStateRef = useRef(fetcher.state);
  const result = fetcher.data?.ping;

  useEffect(() => {
    if (lastStateRef.current !== "submitting" || fetcher.state !== "idle") {
      lastStateRef.current = fetcher.state;
      return;
    }

    if (fetcher.data?.errors?.general) {
      toast.error(fetcher.data.errors.general);
    } else if (fetcher.data?.ping?.connected) {
      toast.success(
        fetcher.data.ping.recovered ? "Ping succeeded after core recovery" : "Ping succeeded",
      );
    } else {
      toast.error(fetcher.data?.ping?.error ?? "Ping failed");
    }
    lastStateRef.current = fetcher.state;
  }, [fetcher.data, fetcher.state]);

  const handlePing = () => {
    const formData = new FormData();
    formData.set("intent", "ping");
    formData.set("shellId", String(shellId));
    fetcher.submit(formData, { method: "post", action: "/shells" });
  };

  return (
    <div className="flex items-center gap-1">
      <Button
        variant="ghost"
        size="icon-xs"
        onClick={handlePing}
        disabled={isPinging}
        title="Quick ping (status first, auto-recover core if needed)"
      >
        {isPinging ? <Spinner className="size-3" /> : <Zap className="size-3" />}
      </Button>
      {result && (
        <span className={cn("text-xs", result.connected ? "text-emerald-500" : "text-red-500")}>
          {result.connected
            ? result.recovered
              ? `recovered ${result.latencyMs}ms`
              : `${result.latencyMs}ms`
            : "fail"}
        </span>
      )}
    </div>
  );
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

  const handleViewOperations = () => {
    navigate(`/shell-operations?shellId=${shell.id}`);
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
          <DropdownMenuItem onClick={handleViewOperations}>
            <ClipboardList className="mr-2 h-4 w-4" />
            Operations
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
      id: "name",
      accessorKey: "name",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Name" />,
      cell: ({ cell }) => cell.getValue(),
      enableColumnFilter: false,
      enableSorting: false,
      size: 150,
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
      enableSorting: false,
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
      size: 200,
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
        const version = row.original.runtimeVersion;
        const labels: Record<ShellLanguage, string> = {
          java: "Java",
          nodejs: "NodeJs",
          dotnet: "DotNet",
        };
        return (
          <span className={"space-x-2"}>
            <Badge variant="secondary">{labels[language] ?? language}</Badge>
            <Badge variant="outline">{version ?? "Unknown"}</Badge>
          </span>
        );
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
      size: 200,
    },
    {
      id: "os",
      accessorKey: "os",
      header: ({ column }) => <DataTableColumnHeader column={column} label="OS" />,
      cell: ({ row }) => {
        const os = row.getValue("os");
        if (os) {
          return `${os} ${row.original.arch ?? ""}`.trim();
        }
        return "Unknown";
      },
      meta: {
        label: "OS",
      },
      enableSorting: false,
      enableColumnFilter: false,
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
            <PingButton shellId={Number(row.original.id)} />
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
      size: 200,
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
