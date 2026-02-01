import type { Table } from "@tanstack/react-table";
import { ArrowUp, CheckCircle2, Download, Trash2, X } from "lucide-react";
import React from "react";
import {
  ActionBar,
  ActionBarClose,
  ActionBarGroup,
  ActionBarItem,
  ActionBarSelection,
  ActionBarSeparator,
} from "@/components/ui/action-bar";
import type { User } from "@/types/admin";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../ui/dropdown-menu";

interface UsersTableActionBarProps {
  table: Table<User>;
}

export function UsersTableActionBar({ table }: UsersTableActionBarProps) {
  const rows = table.getFilteredSelectedRowModel().rows;

  const onOpenChange = React.useCallback(
    (open: boolean) => {
      if (!open) {
        table.toggleAllRowsSelected(false);
      }
    },
    [table],
  );
  return (
    <ActionBar open={rows.length > 0} onOpenChange={onOpenChange}>
      <ActionBar open={rows.length > 0} onOpenChange={onOpenChange}>
        <ActionBarSelection>
          <span className="font-medium">{rows.length}</span>
          <span>selected</span>
          <ActionBarSeparator />
          <ActionBarClose>
            <X />
          </ActionBarClose>
        </ActionBarSelection>
        <ActionBarSeparator />
        <ActionBarGroup>
          <DropdownMenu>
            <DropdownMenuTrigger>
              <ActionBarItem>
                <CheckCircle2 />
                Status
              </ActionBarItem>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem className="capitalize">
                {"hello"}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <DropdownMenu>
            <DropdownMenuTrigger>
              <ActionBarItem>
                <ArrowUp />
                Priority
              </ActionBarItem>
            </DropdownMenuTrigger>
          </DropdownMenu>
          <ActionBarItem>
            <Download />
            Export
          </ActionBarItem>
          <ActionBarItem variant="destructive">
            <Trash2 />
            Delete
          </ActionBarItem>
        </ActionBarGroup>
      </ActionBar>
    </ActionBar>
  );
}
