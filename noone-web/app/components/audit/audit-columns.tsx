import type { AuditLog } from "@/types/audit";
import type { ColumnDef } from "@tanstack/react-table";

import { CalendarIcon, Eye, Shield, Text } from "lucide-react";
import React, { useState } from "react";

import { AuditDetailDialog } from "@/components/audit/audit-detail-dialog";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
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

const AuditActionsCell = React.memo(({ auditLog }: { auditLog: AuditLog }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <Button variant="ghost" size="sm" className="h-8 px-2" onClick={() => setIsOpen(true)}>
        <Eye className="mr-1 h-4 w-4" />
        Details
      </Button>
      <AuditDetailDialog auditLog={auditLog} open={isOpen} onOpenChange={setIsOpen} />
    </>
  );
});

export const auditColumns: ColumnDef<AuditLog>[] = [
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
    enableColumnFilter: true,
    size: 120,
  },
  {
    id: "module",
    accessorKey: "module",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Module" />,
    cell: ({ cell }) => cell.getValue<string>(),
    meta: {
      label: "Module",
      variant: "select",
      options: [
        { label: "User", value: "USER" },
        { label: "Role", value: "ROLE" },
        { label: "Project", value: "PROJECT" },
        { label: "Shell", value: "SHELL" },
        { label: "Profile", value: "PROFILE" },
      ],
    },
    enableColumnFilter: true,
    size: 100,
  },
  {
    id: "action",
    accessorKey: "action",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Action" />,
    cell: ({ cell }) => cell.getValue<string>(),
    meta: {
      label: "Action",
      variant: "select",
      options: [
        { label: "Create", value: "CREATE" },
        { label: "Update", value: "UPDATE" },
        { label: "Delete", value: "DELETE" },
        { label: "Password Change", value: "PASSWORD_CHANGE" },
        { label: "Password Reset", value: "PASSWORD_RESET" },
        { label: "MFA Setup", value: "MFA_SETUP" },
      ],
    },
    enableColumnFilter: true,
    size: 140,
  },
  {
    id: "target",
    accessorFn: (row) =>
      row.targetType && row.targetId ? `${row.targetType}#${row.targetId}` : "",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Target" />,
    cell: ({ cell }) => cell.getValue<string>() || "--",
    enableColumnFilter: false,
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
      icon: Shield,
      options: [
        { label: "Success", value: "true" },
        { label: "Failed", value: "false" },
      ],
    },
    enableColumnFilter: true,
    size: 100,
  },
  {
    id: "ipAddress",
    accessorKey: "ipAddress",
    header: ({ column }) => <DataTableColumnHeader column={column} label="IP Address" />,
    cell: ({ cell }) => cell.getValue<string>() || "--",
    enableColumnFilter: false,
    size: 130,
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
    cell: ({ row }) => <AuditActionsCell auditLog={row.original} />,
    size: 100,
  },
];
