import type { ColumnDef } from "@tanstack/react-table";
import { Edit, MoreHorizontal, Trash2 } from "lucide-react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { deleteProfile } from "@/api/profile-api";
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

export const profileColumns: ColumnDef<Profile>[] = [
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
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="ProtocolType" />
    ),
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
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Updated Time" />
    ),
    cell: ({ cell }) => formatDate(cell.getValue<Date>()),
  },
  {
    id: "actions",
    enableHiding: false,
    cell: ({ row }) => {
      const profile = row.original;
      const navigate = useNavigate();

      const handleEdit = () => {
        navigate(`/profiles/edit/${profile.id}`);
      };

      const handleDelete = async () => {
        if (!confirm("Are you sure you want to delete this profile?")) return;
        try {
          await deleteProfile(profile.id);
          toast.success("Profile deleted");
          navigate(0);
        } catch (e: any) {
          toast.error(e?.message || "Failed to delete profile");
        }
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
              <DropdownMenuItem onClick={handleEdit}>
                <Edit className="mr-2 h-4 w-4" />
                Edit
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={handleDelete}
                className="text-destructive"
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
