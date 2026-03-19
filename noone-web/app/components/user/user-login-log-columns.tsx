import type { LoginLog, LoginLogStatus } from "@/types/admin";
import type { ColumnDef } from "@tanstack/react-table";

import { CalendarIcon, Shield, Text } from "lucide-react";

import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";

const STATUS_META: Record<LoginLogStatus, { label: string; className: string }> = {
  SUCCESS: {
    label: "Success",
    className: "bg-green-100 text-green-800 hover:bg-green-100",
  },
  INVALID_CREDENTIALS: {
    label: "Invalid credentials",
    className: "bg-red-100 text-red-800 hover:bg-red-100",
  },
  REQUIRE_2FA: {
    label: "2FA required",
    className: "bg-amber-100 text-amber-800 hover:bg-amber-100",
  },
  REQUIRE_SETUP: {
    label: "Setup required",
    className: "bg-sky-100 text-sky-800 hover:bg-sky-100",
  },
  REQUIRE_PASSWORD_CHANGE: {
    label: "Password change required",
    className: "bg-violet-100 text-violet-800 hover:bg-violet-100",
  },
  LOCKED: {
    label: "Locked",
    className: "bg-orange-100 text-orange-800 hover:bg-orange-100",
  },
  DISABLED: {
    label: "Disabled",
    className: "bg-zinc-100 text-zinc-800 hover:bg-zinc-100",
  },
};

const LOGIN_LOG_STATUS_OPTIONS = Object.entries(STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}));

export const loginLogColumns: ColumnDef<LoginLog>[] = [
  {
    id: "sessionId",
    accessorKey: "sessionId",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Session ID" />,
    cell: ({ cell }) => cell.getValue<string | null>() || "--",
    enableColumnFilter: true,
    size: 250,
  },
  {
    id: "ipAddress",
    accessorKey: "ipAddress",
    header: ({ column }) => <DataTableColumnHeader column={column} label="IP Address" />,
    cell: ({ cell }) => cell.getValue<string | null>() || "--",
    meta: {
      label: "IP Address",
      variant: "text",
      placeholder: "Search by IP...",
      icon: Text,
    },
    enableColumnFilter: true,
    size: 80,
  },
  {
    id: "status",
    accessorKey: "status",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Status" />,
    cell: ({ row }) => {
      const status = row.getValue<LoginLogStatus>("status");
      const statusMeta = STATUS_META[status];
      return (
        <Badge variant="secondary" className={statusMeta.className}>
          {statusMeta.label}
        </Badge>
      );
    },
    meta: {
      label: "Status",
      variant: "select",
      icon: Shield,
      options: LOGIN_LOG_STATUS_OPTIONS,
    },
    enableColumnFilter: true,
    size: 100,
  },
  {
    id: "os",
    accessorKey: "os",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Operating System" />,
    cell: ({ cell }) => cell.getValue<string | null>(),
    enableColumnFilter: false,
    size: 120,
  },
  {
    id: "browser",
    accessorKey: "browser",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Browser" />,
    cell: ({ cell }) => cell.getValue<string | null>(),
    enableColumnFilter: false,
    size: 80,
  },
  {
    id: "loginTime",
    accessorKey: "loginTime",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Login Time" />,
    cell: ({ cell }) =>
      formatDate(cell.getValue<string>(), {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }),
    meta: {
      label: "Login Time",
      variant: "dateRange",
      icon: CalendarIcon,
    },
    enableColumnFilter: true,
    size: 150,
  },
];
