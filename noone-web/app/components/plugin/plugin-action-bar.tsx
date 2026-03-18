import type { Plugin } from "@/types/plugin";
import type { Table } from "@tanstack/react-table";

import { Trash2 } from "lucide-react";
import { toast } from "sonner";

import {
  DataTableActionBar,
  DataTableActionBarAction,
  DataTableActionBarSelection,
} from "@/components/data-table/data-table-action-bar";
import { Separator } from "@/components/ui/separator";

interface PluginTableActionBarProps {
  table: Table<Plugin>;
}

export function PluginTableActionBar({ table }: PluginTableActionBarProps) {
  const rows = table.getFilteredSelectedRowModel().rows;
  return (
    <DataTableActionBar table={table} visible={rows.length > 0}>
      <DataTableActionBarSelection table={table} />
      <Separator
        orientation="vertical"
        className="hidden data-[orientation=vertical]:h-5 sm:block"
      />
      <div className="flex items-center gap-1.5">
        <DataTableActionBarAction
          size="icon"
          tooltip="Delete"
          onClick={() => toast.info("Will be implemented in the future")}
        >
          <Trash2 />
        </DataTableActionBarAction>
      </div>
    </DataTableActionBar>
  );
}
