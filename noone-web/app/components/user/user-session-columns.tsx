import type { UserSession } from "@/types/admin";
import type { ColumnDef } from "@tanstack/react-table";

import { CalendarIcon, Loader, Shield, Text } from "lucide-react";
import { useFetcher } from "react-router";

import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/format";

const REVOCATION_OPTIONS = [
  { label: "Active", value: "false" },
  { label: "Revoked", value: "true" },
];

function SessionActionsCell({ session }: { session: UserSession }) {
  const fetcher = useFetcher<{ errors?: Record<string, string> }>();
  const isSubmitting = fetcher.state !== "idle";

  return (
    <fetcher.Form method="post">
      <input type="hidden" name="intent" value="revoke-session" />
      <input type="hidden" name="sessionId" value={session.sessionId} />
      <Button
        type="submit"
        size="sm"
        variant={session.revoked ? "outline" : "destructive"}
        disabled={session.revoked || isSubmitting}
      >
        {isSubmitting ? <Loader className="animate-spin" /> : null}
        {session.revoked ? "Revoked" : "Revoke"}
      </Button>
    </fetcher.Form>
  );
}

export function createUserSessionColumns(_userId: number): ColumnDef<UserSession>[] {
  return [
    {
      id: "sessionId",
      accessorKey: "sessionId",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Session ID" />,
      cell: ({ cell }) => cell.getValue<string>(),
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
      id: "lastSeenAt",
      accessorKey: "lastSeenAt",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Last Seen" />,
      cell: ({ cell }) => {
        const value = cell.getValue<string | null>();
        return value
          ? formatDate(value, {
              month: "short",
              day: "numeric",
              year: "numeric",
              hour: "2-digit",
              minute: "2-digit",
            })
          : "--";
      },
      enableColumnFilter: false,
      size: 180,
    },
    {
      id: "revoked",
      accessorKey: "revoked",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Status" />,
      cell: ({ row }) => {
        const revoked = row.getValue<boolean>("revoked");
        return (
          <Badge
            variant="secondary"
            className={
              revoked
                ? "bg-red-100 text-red-800 hover:bg-red-100"
                : "bg-green-100 text-green-800 hover:bg-green-100"
            }
          >
            {revoked ? "Revoked" : "Active"}
          </Badge>
        );
      },
      meta: {
        label: "Status",
        variant: "select",
        icon: Shield,
        options: REVOCATION_OPTIONS,
      },
      enableColumnFilter: true,
      size: 120,
    },
    {
      id: "createdAt",
      accessorKey: "createdAt",
      header: ({ column }) => <DataTableColumnHeader column={column} label="Created Time" />,
      cell: ({ cell }) =>
        formatDate(cell.getValue<string>(), {
          month: "short",
          day: "numeric",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        }),
      meta: {
        label: "Created Time",
        variant: "dateRange",
        icon: CalendarIcon,
      },
      enableColumnFilter: true,
      size: 180,
    },
    {
      id: "actions",
      enableHiding: false,
      enableSorting: false,
      cell: ({ row }) => <SessionActionsCell session={row.original} />,
      size: 110,
    },
  ];
}
