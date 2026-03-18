import type { Permission, Role } from "@/types/admin";
import type { ColumnDef } from "@tanstack/react-table";

import { Edit, Key, Loader, MoreHorizontal, Text, Trash2 } from "lucide-react";
import { useState } from "react";
import { Link, useFetcher } from "react-router";

import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
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

export const roleColumns: ColumnDef<Role>[] = [
  {
    id: "select",
    header: ({ table }) => (
      <Checkbox
        checked={table.getIsAllPageRowsSelected()}
        onCheckedChange={(value: boolean) => table.toggleAllPageRowsSelected(Boolean(value))}
        aria-label="Select all"
        className="translate-y-0.5"
      />
    ),
    cell: ({ row }) => (
      <Checkbox
        checked={row.getIsSelected()}
        onCheckedChange={(value: boolean) => row.toggleSelected(Boolean(value))}
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
    header: ({ column }) => <DataTableColumnHeader column={column} label="Role" />,
    cell: ({ row }) => {
      const role = row.original;
      return (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <Badge className="bg-gray-100 text-gray-800 hover:bg-gray-100">{role.name}</Badge>
          </div>
        </div>
      );
    },
    meta: {
      label: "Role",
      variant: "text",
      placeholder: "Search by name...",
      icon: Text,
    },
    enableColumnFilter: true,
  },
  {
    id: "permissions",
    accessorKey: "permissions",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Permissions" />,
    cell: ({ row }) => {
      const permissions = row.getValue("permissions") as Permission[];
      return (
        <div className="flex items-center gap-2">
          <Key className="h-4 w-4 text-muted-foreground" />
          <span className="font-medium">{permissions.length}</span>
          <span className="text-sm text-muted-foreground">Permissions</span>
        </div>
      );
    },
  },
  {
    id: "createdAt",
    accessorKey: "createdAt",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Created Time" />,
    cell: ({ cell }) => formatDate(cell.getValue<Date>()),
  },
  {
    id: "actions",
    enableHiding: false,
    cell: ({ row }) => {
      const role = row.original;

      const [isDeleteOpen, setIsDeleteOpen] = useState(false);
      const deleteFetcher = useFetcher<{ errors?: Record<string, string> }>();

      return (
        <>
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
                <Link to={`/admin/roles/edit/${role.id}`}>
                  <DropdownMenuItem>
                    <Edit className="mr-2 h-4 w-4" />
                    Edit
                  </DropdownMenuItem>
                </Link>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => setIsDeleteOpen(true)}
                  className="text-destructive"
                  disabled={role.id === 1}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete
                </DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
          <AlertDialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
            <AlertDialogContent>
              <deleteFetcher.Form method="post">
                <input type="hidden" name="intent" value="delete" />
                <input type="hidden" name="roleId" value={String(role.id)} />
                <AlertDialogHeader>
                  <AlertDialogTitle>Delete this role?</AlertDialogTitle>
                  <AlertDialogDescription>
                    This role will be soft-deleted and removed from active admin listings.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                {deleteFetcher.data?.errors?.general ? (
                  <p className="text-sm text-destructive">{deleteFetcher.data.errors.general}</p>
                ) : null}
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction>
                    <Button
                      type="submit"
                      className="text-sm"
                      disabled={deleteFetcher.state !== "idle"}
                    >
                      {deleteFetcher.state !== "idle" ? (
                        <Loader className="mr-2 h-4 w-4 animate-spin" />
                      ) : null}
                      Confirm
                    </Button>
                  </AlertDialogAction>
                </AlertDialogFooter>
              </deleteFetcher.Form>
            </AlertDialogContent>
          </AlertDialog>
        </>
      );
    },
    size: 40,
  },
];
