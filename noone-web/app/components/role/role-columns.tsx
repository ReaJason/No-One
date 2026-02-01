import type { ColumnDef } from "@tanstack/react-table";
import { Edit, Key, MoreHorizontal, Text, Trash2, Users } from "lucide-react";
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
import { formatDate } from "@/lib/format";
import type { Permission, Role } from "@/types/admin";

export const roleColumns: ColumnDef<Role>[] = [
  {
    id: "select",
    header: ({ table }) => (
      <Checkbox
        checked={
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && "indeterminate")
        }
        onCheckedChange={(value: any) =>
          table.toggleAllPageRowsSelected(!!value)
        }
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
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Role" />
    ),
    cell: ({ row }) => {
      const role = row.original;
      return (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <Badge className="bg-gray-100 text-gray-800 hover:bg-gray-100">
              {role.name}
            </Badge>
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
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Permissions" />
    ),
    cell: ({ row }) => {
      const permissions = row.getValue("permissions") as Permission[];
      return (
        <div className="flex items-center gap-2">
          <Key className="w-4 h-4 text-muted-foreground" />
          <span className="font-medium">{permissions.length}</span>
          <span className="text-sm text-muted-foreground">Permissions</span>
        </div>
      );
    },
  },
  {
    id: "createdAt",
    accessorKey: "createdAt",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Created Time" />
    ),
    cell: ({ cell }) => formatDate(cell.getValue<Date>()),
  },
  {
    id: "actions",
    enableHiding: false,
    cell: ({ row }) => {
      const role = row.original;

      const handleEditRole = () => {
        window.location.href = `/admin/roles/edit/${role.id}`;
      };

      const handleDeleteRole = async () => {
        if (
          !confirm(
            "Are you sure you want to delete this role? This action cannot be undone.",
          )
        )
          return;
        // TODO: Implement delete role
        console.log("Delete role:", role.id);
      };

      const handleViewUsers = () => {
        // TODO: Implement view users
        console.log("View users for role:", role.id);
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
              <DropdownMenuItem onClick={handleEditRole}>
                <Edit className="mr-2 h-4 w-4" />
                Edit
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleViewUsers}>
                <Users className="mr-2 h-4 w-4" />
                View Users
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={handleDeleteRole}
                className="text-destructive"
                disabled={role.id === "1"}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                Delete
              </DropdownMenuItem>
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      );
    },
    size: 40,
  },
];
