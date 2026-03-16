import type { Profile } from "@/types/profile";
import type { ColumnDef } from "@tanstack/react-table";

import { Edit, Loader, MoreHorizontal, Trash2 } from "lucide-react";
import React, { useEffect, useRef, useState } from "react";
import { useFetcher, useNavigate } from "react-router";
import { toast } from "sonner";

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

type DeleteFetcherData = {
  success?: boolean;
  errors?: Record<string, string>;
};

export const ProfileActionsCell = React.memo(function ProfileActionsCell({
  profile,
}: {
  profile: Profile;
}) {
  const navigate = useNavigate();
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const deleteFetcher = useFetcher<DeleteFetcherData>();
  const isDeleting = deleteFetcher.state !== "idle";
  const wasDeletingRef = useRef(isDeleting);
  const deleteError = deleteFetcher.data?.errors?.general;

  useEffect(() => {
    if (!wasDeletingRef.current || isDeleting) {
      wasDeletingRef.current = isDeleting;
      return;
    }

    if (deleteFetcher.data?.success) {
      setIsDeleteOpen(false);
      toast.success("Profile deleted");
    } else if (deleteFetcher.data?.errors?.general) {
      toast.error(deleteFetcher.data.errors.general);
    }

    wasDeletingRef.current = isDeleting;
  }, [deleteFetcher.data, isDeleting]);

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
            <DropdownMenuItem onClick={() => navigate(`/profiles/edit/${profile.id}`)}>
              <Edit className="mr-2 h-4 w-4" />
              Edit
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => setIsDeleteOpen(true)}
              className="text-destructive"
              disabled={isDeleting}
            >
              {isDeleting ? (
                <Loader className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Trash2 className="mr-2 h-4 w-4" />
              )}
              Delete
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
        <AlertDialogContent>
          <deleteFetcher.Form method="post" action="/profiles">
            <input type="hidden" name="intent" value="delete" />
            <input type="hidden" name="profileId" value={String(profile.id)} />
            <AlertDialogHeader>
              <AlertDialogTitle>Are you sure you want to delete this profile?</AlertDialogTitle>
              <AlertDialogDescription>
                This action cannot be undone. The profile configuration will be permanently removed.
              </AlertDialogDescription>
            </AlertDialogHeader>
            {deleteError ? <p className="text-sm text-destructive">{deleteError}</p> : null}
            <AlertDialogFooter>
              <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
              <AlertDialogAction>
                <Button type="submit" className="text-sm" disabled={isDeleting}>
                  {isDeleting ? <Loader className="mr-2 h-4 w-4 animate-spin" /> : null}
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

export const profileColumns: ColumnDef<Profile>[] = [
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
    cell: ({ row }) => row.getValue("name") as string,
    meta: {
      label: "Profile",
      variant: "text",
      placeholder: "Search by name...",
    },
    enableColumnFilter: true,
  },
  {
    id: "protocolType",
    accessorKey: "protocolType",
    header: ({ column }) => <DataTableColumnHeader column={column} label="ProtocolType" />,
    cell: ({ row }) => row.getValue("protocolType") as string,
    meta: {
      label: "ProtocolType",
      variant: "select",
      options: [
        { label: "HTTP", value: "HTTP" },
        { label: "WEBSOCKET", value: "WEBSOCKET" },
      ],
    },
    enableColumnFilter: true,
    enableSorting: false,
    size: 100,
  },
  {
    id: "identifier",
    accessorKey: "identifier",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Identifier" />,
    cell: ({ row }) => {
      return row.getValue("identifier") ? (
        <Badge className="dark:bg-violet--950 dark:text-violet--300 bg-violet-50 text-violet-700">
          Filtered
        </Badge>
      ) : (
        <Badge className="bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300">
          Wildcard
        </Badge>
      );
    },
    size: 80,
    enableSorting: false,
  },
  {
    id: "requestTransformations",
    accessorKey: "requestTransformations",
    header: ({ column }) => <DataTableColumnHeader column={column} label="RequestTransformation" />,
    cell: ({ row }) => {
      const values = (row.getValue("requestTransformations") as Array<string>).filter(
        (v) => v !== "None",
      );
      return values.length === 0 ? "RAW" : values.join(" -> ");
    },
    enableSorting: false,
  },
  {
    id: "responseTransformations",
    accessorKey: "responseTransformations",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} label="ResponseTransformations" />
    ),
    cell: ({ row }) => {
      const values = (row.getValue("responseTransformations") as Array<string>).filter(
        (v) => v !== "None",
      );
      return values.length === 0 ? "RAW" : values.join(" -> ");
    },
    enableSorting: false,
  },
  {
    id: "updatedAt",
    accessorKey: "updatedAt",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Updated Time" />,
    cell: ({ cell }) => formatDate(cell.getValue<Date>()),
  },
  {
    id: "actions",
    enableHiding: false,
    cell: ({ row }) => <ProfileActionsCell profile={row.original} />,
    size: 40,
  },
];
