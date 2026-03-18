import type { Role, User, UserStatus } from "@/types/admin";
import type { ColumnDef } from "@tanstack/react-table";

import {
  CalendarIcon,
  Loader,
  Mail,
  MoreHorizontal,
  Settings,
  Shield,
  Text,
  Trash2,
} from "lucide-react";
import React, { useMemo, useState } from "react";
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

const STATUS_META: Record<UserStatus, { label: string; className: string }> = {
  ENABLED: { label: "Enabled", className: "bg-green-100 text-green-800 hover:bg-green-100" },
  DISABLED: { label: "Disabled", className: "bg-red-100 text-red-800 hover:bg-red-100" },
  LOCKED: { label: "Locked", className: "bg-orange-100 text-orange-800 hover:bg-orange-100" },
  UNACTIVATED: { label: "Unactivated", className: "bg-zinc-100 text-zinc-800 hover:bg-zinc-100" },
};

const StatusBadge = React.memo(({ status }: { status: UserStatus }) => {
  const statusMeta = STATUS_META[status];
  return (
    <Badge variant="secondary" className={statusMeta.className}>
      {statusMeta.label}
    </Badge>
  );
});

const UserActionsCell = React.memo(({ user }: { user: User }) => {
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const deleteFetcher = useFetcher<{ errors?: Record<string, string> }>();
  const deleteError = deleteFetcher.data?.errors?.general;

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
        <DropdownMenuContent className="w-44" align="start">
          <DropdownMenuGroup>
            <DropdownMenuLabel>Actions</DropdownMenuLabel>
            <Link to={`/admin/users/edit/${user.id}`}>
              <DropdownMenuItem>
                <Settings className="mr-2 h-4 w-4" />
                Edit User
              </DropdownMenuItem>
            </Link>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => setIsDeleteOpen(true)} className="text-destructive">
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
            <input type="hidden" name="userId" value={String(user.id)} />
            <AlertDialogHeader>
              <AlertDialogTitle>Are you sure you want to delete this user?</AlertDialogTitle>
              <AlertDialogDescription>
                This user will be archived from active admin operations and hidden from standard
                queries.
              </AlertDialogDescription>
            </AlertDialogHeader>
            {deleteError ? <p className="text-sm text-destructive">{deleteError}</p> : null}
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction>
                <Button type="submit" className="text-sm" disabled={deleteFetcher.state !== "idle"}>
                  {deleteFetcher.state !== "idle" && (
                    <Loader className="mr-2 h-4 w-4 animate-spin" />
                  )}
                  Confirm
                </Button>
              </AlertDialogAction>
            </AlertDialogFooter>
          </deleteFetcher.Form>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
});

export const useUserColumns = (roles: Role[]): ColumnDef<User>[] => {
  return useMemo(
    () => [
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
        id: "username",
        accessorKey: "username",
        header: ({ column }) => <DataTableColumnHeader column={column} label="User" />,
        cell: ({ row }) => {
          const currentUser = row.original;
          return (
            <div className="space-y-1">
              <div>{currentUser.username}</div>
              {currentUser.mustChangePassword ? (
                <Badge variant="outline" className="text-[10px] tracking-wide uppercase">
                  Password reset required
                </Badge>
              ) : null}
            </div>
          );
        },
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
        id: "email",
        accessorKey: "email",
        header: ({ column }) => <DataTableColumnHeader column={column} label="Email" />,
        cell: ({ cell }) => cell.getValue<string>(),
        meta: {
          label: "Email",
          variant: "text",
          placeholder: "Search by email...",
          icon: Mail,
        },
        enableColumnFilter: false,
        size: 180,
      },
      {
        id: "roles",
        accessorKey: "roles",
        header: ({ column }) => <DataTableColumnHeader column={column} label="Role" />,
        cell: ({ row }) => {
          const userRoles = row.getValue("roles") as Role[];
          return (
            <div className="flex flex-wrap gap-1">
              {userRoles.map((role) => (
                <Badge key={role.id} variant="outline" className="text-xs">
                  {role.name}
                </Badge>
              ))}
            </div>
          );
        },
        meta: {
          label: "Role",
          variant: "select",
          options: roles.map((role) => ({
            label: role.name,
            value: String(role.id),
          })),
        },
        enableColumnFilter: true,
      },
      {
        id: "status",
        accessorKey: "status",
        header: ({ column }) => <DataTableColumnHeader column={column} label="Status" />,
        cell: ({ row }) => {
          const status = row.getValue("status") as UserStatus;
          return <StatusBadge status={status} />;
        },
        meta: {
          label: "Status",
          variant: "select",
          icon: Shield,
          options: [
            { label: "Enabled", value: "ENABLED" },
            { label: "Disabled", value: "DISABLED" },
            { label: "Locked", value: "LOCKED" },
            { label: "Unactivated", value: "UNACTIVATED" },
          ],
        },
        enableColumnFilter: true,
      },
      {
        id: "lastLogin",
        accessorKey: "lastLogin",
        header: ({ column }) => <DataTableColumnHeader column={column} label="Last Login" />,
        cell: ({ cell }) => {
          const value = cell.getValue<string | null>();
          return value ? formatDate(value) : "--";
        },
        enableColumnFilter: false,
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
        cell: ({ row }) => <UserActionsCell user={row.original} />,
        size: 40,
      },
    ],
    [roles],
  );
};
