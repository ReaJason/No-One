import type { ShellOperationLog } from "@/types/shell-operation-log";
import type { ColumnDef } from "@tanstack/react-table";

import { CalendarIcon, Eye, Text } from "lucide-react";
import React, { useState } from "react";

import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { ShellOperationDetailDialog } from "@/components/shell/shell-operation-detail-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/format";

const SuccessBadge = React.memo(({ success }: { success: boolean }) => (
  <Badge
    variant="secondary"
    className={
      success
        ? "bg-green-100 text-green-800 hover:bg-green-100"
        : "bg-red-100 text-red-800 hover:bg-red-100"
    }
  >
    {success ? "Success" : "Failed"}
  </Badge>
));

const operationBadgeStyles: Record<string, string> = {
  TEST: "bg-blue-100 text-blue-800 hover:bg-blue-100",
  DISPATCH: "bg-purple-100 text-purple-800 hover:bg-purple-100",
  LOAD_PLUGIN: "bg-amber-100 text-amber-800 hover:bg-amber-100",
  LOAD_CORE: "bg-teal-100 text-teal-800 hover:bg-teal-100",
};

const OperationBadge = React.memo(({ operation }: { operation: string }) => (
  <Badge variant="secondary" className={operationBadgeStyles[operation] ?? ""}>
    {operation}
  </Badge>
));

const ActionsCell = React.memo(({ log }: { log: ShellOperationLog }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => setIsOpen(true)}>
        <Eye className="mr-1 h-4 w-4" />
        Details
      </Button>
      <ShellOperationDetailDialog log={log} open={isOpen} onOpenChange={setIsOpen} />
    </>
  );
});

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

const sharedColumns: ColumnDef<ShellOperationLog>[] = [
  {
    id: "operation",
    accessorKey: "operation",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Operation" />,
    cell: ({ row }) => <OperationBadge operation={row.getValue<string>("operation")} />,
    meta: {
      label: "Operation",
      variant: "select",
      options: [
        { label: "Test", value: "TEST" },
        { label: "Dispatch", value: "DISPATCH" },
        { label: "Load Plugin", value: "LOAD_PLUGIN" },
        { label: "Load Core", value: "LOAD_CORE" },
      ],
    },
    enableColumnFilter: true,
    size: 120,
  },
  {
    id: "pluginId",
    accessorKey: "pluginId",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Plugin" />,
    cell: ({ row }) => {
      const pluginId = row.getValue("pluginId") as string | null;
      return pluginId ? (
        <span className="font-mono text-sm">{pluginId}</span>
      ) : (
        <span className="text-sm text-muted-foreground">--</span>
      );
    },
    meta: {
      label: "Plugin",
      variant: "text",
      placeholder: "Search by plugin...",
      icon: Text,
    },
    enableColumnFilter: true,
    size: 160,
  },
  {
    id: "action",
    accessorKey: "action",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Action" />,
    cell: ({ row }) => {
      const action = row.getValue("action") as string | null;
      return action ? (
        <span className="text-sm">{action}</span>
      ) : (
        <span className="text-sm text-muted-foreground">--</span>
      );
    },
    meta: { label: "Action" },
    size: 120,
  },
  {
    id: "success",
    accessorKey: "success",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Status" />,
    cell: ({ row }) => <SuccessBadge success={row.getValue<boolean>("success")} />,
    meta: {
      label: "Status",
      variant: "select",
      options: [
        { label: "Success", value: "true" },
        { label: "Failed", value: "false" },
      ],
    },
    enableColumnFilter: true,
    size: 100,
  },
  {
    id: "durationMs",
    accessorKey: "durationMs",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Duration" />,
    cell: ({ row }) => formatDuration(row.getValue<number>("durationMs")),
    meta: { label: "Duration" },
    size: 100,
  },
  {
    id: "createdAt",
    accessorKey: "createdAt",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Created Time" />,
    cell: ({ cell }) => formatDate(cell.getValue<string>()),
    meta: {
      label: "Created At",
      variant: "dateRange",
      icon: CalendarIcon,
    },
    enableColumnFilter: true,
  },
  {
    id: "actions",
    enableHiding: false,
    enableSorting: false,
    cell: ({ row }) => <ActionsCell log={row.original} />,
    size: 100,
  },
];

export const shellOperationColumns: ColumnDef<ShellOperationLog>[] = [
  {
    id: "shellId",
    accessorKey: "shellId",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Shell ID" />,
    cell: ({ row }) => (
      <span className="font-mono text-sm">#{row.getValue<number>("shellId")}</span>
    ),
    meta: {
      label: "Shell ID",
      variant: "text",
      placeholder: "Search by shell ID...",
      icon: Text,
    },
    enableColumnFilter: true,
    size: 100,
  },
  {
    id: "username",
    accessorKey: "username",
    header: ({ column }) => <DataTableColumnHeader column={column} label="User" />,
    cell: ({ cell }) => cell.getValue<string>(),
    meta: {
      label: "User",
      variant: "text",
      placeholder: "Search by username...",
      icon: Text,
    },
    enableColumnFilter: false,
    size: 120,
  },
  ...sharedColumns,
];

export function getShellManagerOperationColumns(): ColumnDef<ShellOperationLog>[] {
  return [...sharedColumns];
}
