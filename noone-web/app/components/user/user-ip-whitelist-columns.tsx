import type { UserIpWhitelistEntry } from "@/types/admin";
import type { ColumnDef } from "@tanstack/react-table";

import { CalendarIcon, Trash2 } from "lucide-react";

import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/format";

function DeleteWhitelistEntryCell({ entry }: { entry: UserIpWhitelistEntry }) {
  return (
    <form method="post">
      <input type="hidden" name="intent" value="delete" />
      <input type="hidden" name="entryId" value={String(entry.id)} />
      <Button type="submit" size="sm" variant="destructive">
        <Trash2 />
        Delete
      </Button>
    </form>
  );
}

export function createUserIpWhitelistColumns(_userId: number): ColumnDef<UserIpWhitelistEntry>[] {
  return [
    {
      id: "ipAddress",
      accessorKey: "ipAddress",
      header: ({ column }) => <DataTableColumnHeader column={column} label="IP Address" />,
      cell: ({ cell }) => cell.getValue<string>(),
      meta: {
        label: "IP Address",
        variant: "text",
        placeholder: "Search by IP...",
      },
      enableColumnFilter: true,
      size: 180,
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
      enableSorting: false,
      enableHiding: false,
      cell: ({ row }) => <DeleteWhitelistEntryCell entry={row.original} />,
      size: 110,
    },
  ];
}
