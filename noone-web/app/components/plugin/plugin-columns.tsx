import type { Plugin, PluginSource } from "@/types/plugin";
import type { ColumnDef } from "@tanstack/react-table";

import { CalendarIcon } from "lucide-react";

import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import { PluginActionsCell } from "@/components/plugin/plugin-actions-cell";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { formatDate } from "@/lib/format";

const PLUGIN_SOURCE_LABELS: Record<PluginSource, string> = {
  BUILTIN: "Builtin",
  UPLOADED: "Uploaded",
  REGISTRY: "Registry",
};

export const pluginColumns: ColumnDef<Plugin>[] = [
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
    cell: ({ row }) => (
      <div>
        <div>{row.original.name}</div>
        <div className="text-xs text-muted-foreground">{row.original.id}</div>
      </div>
    ),
    meta: {
      label: "Plugin",
      variant: "text",
      placeholder: "Search by name...",
    },
    enableColumnFilter: true,
  },
  {
    id: "version",
    accessorKey: "version",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Version" />,
    cell: ({ row }) => row.getValue("version") as string,
  },
  {
    id: "type",
    accessorKey: "type",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Type" />,
    cell: ({ row }) => <Badge variant="outline">{row.getValue("type") as string}</Badge>,
    meta: {
      label: "Type",
      variant: "text",
      placeholder: "Search by type...",
    },
    enableColumnFilter: true,
  },
  {
    id: "author",
    accessorKey: "author",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Author" />,
    cell: ({ row }) => (row.getValue("author") as string) || "—",
  },
  {
    id: "source",
    accessorKey: "source",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Source" />,
    cell: ({ row }) => {
      const source = row.getValue("source") as PluginSource | undefined;
      if (!source) return "—";
      return <Badge variant="secondary">{PLUGIN_SOURCE_LABELS[source]}</Badge>;
    },
  },
  {
    id: "createdAt",
    accessorKey: "createdAt",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Created Time" />,
    cell: ({ cell }) => formatDate(cell.getValue<Date>()),
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
    cell: ({ row }) => <PluginActionsCell plugin={row.original} />,
    size: 40,
  },
];
