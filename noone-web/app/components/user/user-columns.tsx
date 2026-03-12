import type { ColumnDef } from "@tanstack/react-table";
import {
  CalendarIcon,
  KeyRound,
  Loader,
  Mail,
  MoreHorizontal,
  Settings,
  Shield,
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { formatDate } from "@/lib/format";
import type { Role, User, UserStatus } from "@/types/admin";

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
  const [isStatusOpen, setIsStatusOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [isResetOpen, setIsResetOpen] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  const [verificationPassword, setVerificationPassword] = useState("");

  const statusFetcher = useFetcher<{ errors?: Record<string, string> }>();
  const deleteFetcher = useFetcher<{ errors?: Record<string, string> }>();
  const resetPasswordFetcher = useFetcher<{ errors?: Record<string, string> }>();

  const nextStatus: UserStatus = user.status === "ENABLED" ? "DISABLED" : "ENABLED";
  const statusActionLabel = user.status === "ENABLED" ? "Disable User" : "Enable User";
  const statusError = statusFetcher.data?.errors?.general;
  const deleteError = deleteFetcher.data?.errors?.general;
  const resetPasswordError = resetPasswordFetcher.data?.errors?.general;

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
            <Link to={`/admin/users/edit-roles/${user.id}`}>
              <DropdownMenuItem>
                <Settings className="mr-2 h-4 w-4" />
                Edit Roles
              </DropdownMenuItem>
            </Link>
            <Link to={`/admin/users/security/${user.id}`}>
              <DropdownMenuItem>
                <Shield className="mr-2 h-4 w-4" />
                Security
              </DropdownMenuItem>
            </Link>
            <DropdownMenuItem onClick={() => setIsStatusOpen(true)}>
              {user.status === "ENABLED" ? (
                <>
                  <UserX className="mr-2 h-4 w-4" />
                  {statusActionLabel}
                </>
              ) : (
                <>
                  <UserCheck className="mr-2 h-4 w-4" />
                  {statusActionLabel}
                </>
              )}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setIsResetOpen(true)}>
              <KeyRound className="mr-2 h-4 w-4" />
              Reset Password
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => setIsDeleteOpen(true)} className="text-destructive">
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={isStatusOpen} onOpenChange={setIsStatusOpen}>
        <AlertDialogContent>
          <statusFetcher.Form method="post" action={`/admin/users/${user.id}/update`}>
            <input type="hidden" name="status" value={nextStatus} />
            <AlertDialogHeader>
              <AlertDialogTitle>Confirm status change</AlertDialogTitle>
              <AlertDialogDescription>
                This user status will be changed from {STATUS_META[user.status].label} to{" "}
                {STATUS_META[nextStatus].label}.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <div className="space-y-2">
              <Label htmlFor={`status-password-${user.id}`}>Current admin password</Label>
              <Input
                id={`status-password-${user.id}`}
                name="verificationPassword"
                type="password"
                required
                value={verificationPassword}
                onChange={(event) => setVerificationPassword(event.target.value)}
                disabled={statusFetcher.state !== "idle"}
              />
              {statusError ? <p className="text-sm text-destructive">{statusError}</p> : null}
            </div>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction>
                <Button type="submit" className="text-sm" disabled={statusFetcher.state !== "idle"}>
                  {statusFetcher.state !== "idle" && (
                    <Loader className="mr-2 h-4 w-4 animate-spin" />
                  )}
                  Confirm
                </Button>
              </AlertDialogAction>
            </AlertDialogFooter>
          </statusFetcher.Form>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={isResetOpen} onOpenChange={setIsResetOpen}>
        <AlertDialogContent>
          <resetPasswordFetcher.Form
            method="post"
            action={`/admin/users/${user.id}/reset-password`}
            className="space-y-4"
          >
            <input type="hidden" name="forceChangeOnNextLogin" value="true" />
            <AlertDialogHeader>
              <AlertDialogTitle>Reset user password</AlertDialogTitle>
              <AlertDialogDescription>
                A temporary password will be set for this user.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <div className="space-y-2">
              <Label htmlFor={`new-password-${user.id}`}>Temporary password</Label>
              <Input
                id={`new-password-${user.id}`}
                name="newPassword"
                type="password"
                minLength={6}
                required
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                disabled={resetPasswordFetcher.state !== "idle"}
              />
              {resetPasswordError ? (
                <p className="text-sm text-destructive">{resetPasswordError}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor={`reset-password-verify-${user.id}`}>Current admin password</Label>
              <Input
                id={`reset-password-verify-${user.id}`}
                name="verificationPassword"
                type="password"
                required
                value={verificationPassword}
                onChange={(event) => setVerificationPassword(event.target.value)}
                disabled={resetPasswordFetcher.state !== "idle"}
              />
            </div>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction>
                <Button
                  type="submit"
                  className="text-sm"
                  disabled={resetPasswordFetcher.state !== "idle" || newPassword.length < 6}
                >
                  {resetPasswordFetcher.state !== "idle" && (
                    <Loader className="mr-2 h-4 w-4 animate-spin" />
                  )}
                  Confirm
                </Button>
              </AlertDialogAction>
            </AlertDialogFooter>
          </resetPasswordFetcher.Form>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
        <AlertDialogContent>
          <deleteFetcher.Form method="post" action={`/admin/users/${user.id}/delete`}>
            <AlertDialogHeader>
              <AlertDialogTitle>Are you sure you want to delete this user?</AlertDialogTitle>
              <AlertDialogDescription>
                This action cannot be undone. The user account will be permanently removed.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <div className="space-y-2">
              <Label htmlFor={`delete-password-${user.id}`}>Current admin password</Label>
              <Input
                id={`delete-password-${user.id}`}
                name="verificationPassword"
                type="password"
                required
                value={verificationPassword}
                onChange={(event) => setVerificationPassword(event.target.value)}
                disabled={deleteFetcher.state !== "idle"}
              />
              {deleteError ? <p className="text-sm text-destructive">{deleteError}</p> : null}
            </div>
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
