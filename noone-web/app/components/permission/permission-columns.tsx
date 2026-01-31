import type {ColumnDef} from "@tanstack/react-table";
import {Edit, Loader, MoreHorizontal, Text, Trash2, Users,} from "lucide-react";
import {useState} from "react";
import {Link, useFetcher} from "react-router";
import {DataTableColumnHeader} from "@/components/data-table/data-table-column-header";
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
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {Checkbox} from "@/components/ui/checkbox";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuGroup,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {formatDate} from "@/lib/format";
import type {Permission} from "@/types/admin";

export const permissionColumns: ColumnDef<Permission>[] = [
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
      <DataTableColumnHeader column={column} title="Name" />
    ),
    cell: ({ row }) => {
      const permission = row.original;
      return (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <Badge className="bg-gray-100 text-gray-800 hover:bg-gray-100">
              {permission.name}
            </Badge>
          </div>
        </div>
      );
    },
    meta: {
      label: "Permission",
      variant: "text",
      placeholder: "Search by name...",
      icon: Text,
    },
    enableColumnFilter: true,
  },
  {
    id: "code",
    accessorKey: "code",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Code" />
    ),
    cell: ({ cell }) => cell.getValue<string>(),
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
      const permission = row.original;
      const [isOpen, setIsOpen] = useState(false);
      const fetcher = useFetcher();

      const handleViewRoles = () => {
        // TODO: Implement view roles
        console.log("View roles for permission:", permission.id);
      };

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
                <DropdownMenuItem>
                  <Link
                    to={`/admin/permissions/edit/${permission.id}`}
                    className="flex items-center gap-2"
                  >
                    <Edit className="mr-2 h-4 w-4" />
                    Edit
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleViewRoles}>
                  <Users className="mr-2 h-4 w-4" />
                  View Roles
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => setIsOpen(true)}
                  className="text-destructive"
                  disabled={permission.code === "system:manage"}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete
                </DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
          <AlertDialog open={isOpen} onOpenChange={setIsOpen}>
            <AlertDialogContent>
              <fetcher.Form
                method="post"
                action={`/admin/permissions/delete/${permission.id}`}
              >
                <AlertDialogHeader>
                  <AlertDialogTitle>
                    Are you sure you want to delete this permission?
                  </AlertDialogTitle>
                  <AlertDialogDescription>
                    Make sure you want to delete this permission.
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
        </>
      );
    },
    size: 40,
  },
];
