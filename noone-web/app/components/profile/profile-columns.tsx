import type { ColumnDef } from "@tanstack/react-table";
import { Edit, Loader, MoreHorizontal, Trash2 } from "lucide-react";
import { useEffect, useRef } from "react";
import { useFetcher, useNavigate } from "react-router";
import { toast } from "sonner";
import { DataTableColumnHeader } from "@/components/data-table/data-table-column-header";
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
import type { Profile } from "@/types/profile";

function ProfileActionsCell({ profile }: { profile: Profile }) {
  const navigate = useNavigate();
  const deleteFetcher = useFetcher<{ success?: boolean; errors?: Record<string, string> }>();
  const lastStateRef = useRef(deleteFetcher.state);

  useEffect(() => {
    if (lastStateRef.current !== "submitting" || deleteFetcher.state !== "idle") {
      lastStateRef.current = deleteFetcher.state;
      return;
    }
    if (deleteFetcher.data?.success) {
      toast.success("Profile deleted");
    } else if (deleteFetcher.data?.errors?.general) {
      toast.error(deleteFetcher.data.errors.general);
    }
    lastStateRef.current = deleteFetcher.state;
  }, [deleteFetcher.data, deleteFetcher.state]);

  const handleDelete = () => {
    if (!confirm("Are you sure you want to delete this profile?")) return;
    const formData = new FormData();
    formData.set("intent", "delete");
    formData.set("profileId", String(profile.id));
    deleteFetcher.submit(formData, { method: "post", action: "/profiles" });
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
          <DropdownMenuItem onClick={() => navigate(`/profiles/edit/${profile.id}`)}>
            <Edit className="mr-2 h-4 w-4" />
            Edit
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={handleDelete}
            className="text-destructive"
            disabled={deleteFetcher.state !== "idle"}
          >
            {deleteFetcher.state !== "idle" ? (
              <Loader className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Trash2 className="mr-2 h-4 w-4" />
            )}
            Delete
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

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
