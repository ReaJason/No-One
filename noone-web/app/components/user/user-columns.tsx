import type { ColumnDef } from "@tanstack/react-table";
import {
  CalendarIcon,
  Loader,
  MoreHorizontal,
  Settings,
  Text,
  Trash2,
  UserCheck,
  UserX,
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
import type { Role, User } from "@/types/admin";

const StatusBadge = React.memo(({ status }: { status: boolean }) => {
  return status === true ? (
    <Badge
      variant="default"
      className="bg-green-100 text-green-800 hover:bg-green-100"
    >
      Active
    </Badge>
  ) : (
    <Badge
      variant="secondary"
      className="bg-red-100 text-red-800 hover:bg-red-100"
    >
      Inactive
    </Badge>
  );
});

const UserActionsCell = React.memo(({ user }: { user: User }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const fetcher = useFetcher();
  const deleteFetcher = useFetcher();
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
        <DropdownMenuContent className="w-38" align="start">
          <DropdownMenuGroup>
            <DropdownMenuLabel>Actions</DropdownMenuLabel>
            <Link to={`/admin/users/edit-roles/${user.id}`}>
              <DropdownMenuItem>
                <Settings className="mr-2 h-4 w-4" />
                Edit Roles
              </DropdownMenuItem>
            </Link>
            <DropdownMenuItem onClick={() => setIsOpen(true)}>
              {user.enabled === true ? (
                <>
                  <UserX className="mr-2 h-4 w-4" />
                  Disable User
                </>
              ) : (
                <>
                  <UserCheck className="mr-2 h-4 w-4" />
                  Enable User
                </>
              )}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => setIsDeleteOpen(true)}
              className="text-destructive"
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuContent>
      </DropdownMenu>
      <AlertDialog open={isOpen} onOpenChange={setIsOpen}>
        <AlertDialogContent>
          <fetcher.Form method="post" action={`/admin/users/update/${user.id}`}>
            <input
              type="hidden"
              name="enabled"
              value={(!user.enabled).toString()}
            />
            <AlertDialogHeader>
              <AlertDialogTitle>
                Are you sure you want to {user.enabled ? "disable" : "enable"}{" "}
                this user?
              </AlertDialogTitle>
              <AlertDialogDescription>
                Make sure you want to {user.enabled ? "disable" : "enable"} this
                user.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction>
                <Button
                  type="submit"
                  className="text-sm"
                  disabled={fetcher.state !== "idle"}
                >
                  {fetcher.state !== "idle" && (
                    <Loader className="mr-2 h-4 w-4 animate-spin" />
                  )}
                  Confirm
                </Button>
              </AlertDialogAction>
            </AlertDialogFooter>
          </fetcher.Form>
        </AlertDialogContent>
      </AlertDialog>
      <AlertDialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
        <AlertDialogContent>
          <deleteFetcher.Form
            method="post"
            action={`/admin/users/delete/${user.id}`}
          >
            <AlertDialogHeader>
              <AlertDialogTitle>
                Are you sure you want to delete this user?
              </AlertDialogTitle>
              <AlertDialogDescription>
                This action cannot be undone. This will permanently delete your
                account and remove your data from our servers.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction>
                <Button
                  type="submit"
                  className="text-sm"
                  disabled={deleteFetcher.state !== "idle"}
                >
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
        id: "username",
        accessorKey: "username",
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="User" />
        ),
        cell: ({ cell }) => cell.getValue<string>(),
        meta: {
          label: "User",
          variant: "text",
          placeholder: "Search by username...",
          icon: Text,
        },
        enableColumnFilter: true,
        size: 80,
      },
      {
        id: "roles",
        accessorKey: "roles",
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="Role" />
        ),
        cell: ({ row }) => {
          const roles = row.getValue("roles") as Role[];
          return (
            <div className="flex flex-wrap gap-1">
              {roles.map((role) => (
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
            value: role.id,
          })),
        },
        enableColumnFilter: true,
      },
      {
        id: "enabled",
        accessorKey: "enabled",
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="Status" />
        ),
        cell: ({ row }) => {
          const status = row.getValue("enabled") as boolean;
          return <StatusBadge status={status} />;
        },
        meta: {
          label: "Status",
          variant: "select",
          options: [
            { label: "Active", value: "true" },
            { label: "Inactive", value: "false" },
          ],
        },
        enableColumnFilter: true,
      },
      {
        id: "createdAt",
        accessorKey: "createdAt",
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="Created Time" />
        ),
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
        cell: ({ row }) => <UserActionsCell user={row.original} />,
        size: 40,
      },
    ],
    [roles],
  );
};
