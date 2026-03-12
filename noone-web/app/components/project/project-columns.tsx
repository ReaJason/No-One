import type { ColumnDef } from "@tanstack/react-table";
import { CalendarIcon, Edit, Loader, MoreHorizontal, Terminal, Trash2 } from "lucide-react";
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
import type { Project } from "@/types/project";

function ProjectActionsCell({ project }: { project: Project }) {
  const navigate = useNavigate();
  const deleteFetcher = useFetcher<{ success?: boolean; errors?: Record<string, string> }>();
  const lastStateRef = useRef(deleteFetcher.state);

  useEffect(() => {
    if (lastStateRef.current !== "submitting" || deleteFetcher.state !== "idle") {
      lastStateRef.current = deleteFetcher.state;
      return;
    }
    if (deleteFetcher.data?.success) {
      toast.success("Project deleted");
    } else if (deleteFetcher.data?.errors?.general) {
      toast.error(deleteFetcher.data.errors.general);
    }
    lastStateRef.current = deleteFetcher.state;
  }, [deleteFetcher.data, deleteFetcher.state]);

  const handleDeleteProject = () => {
    if (!confirm("Are you sure you want to delete this project? This action cannot be undone."))
      return;
    const formData = new FormData();
    formData.set("intent", "delete");
    formData.set("projectId", String(project.id));
    deleteFetcher.submit(formData, { method: "post", action: "/projects" });
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
          <DropdownMenuItem onClick={() => navigate(`/projects/${project.id}/edit`)}>
            <Edit className="mr-2 h-4 w-4" />
            Edit
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => navigate(`/shells?projectId=${project.id}`)}>
            <Terminal className="mr-2 h-4 w-4" />
            View Shells
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={handleDeleteProject}
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

export const projectColumns: ColumnDef<Project>[] = [
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
      label: "Project",
      variant: "text",
      placeholder: "Search by name or code...",
    },
    enableColumnFilter: true,
  },
  {
    id: "code",
    accessorKey: "code",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Code" />,
    cell: ({ row }) => row.getValue("code") as string,
  },
  {
    id: "status",
    accessorKey: "status",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Status" />,
    cell: ({ row }) => row.getValue("status") as string,
    meta: {
      label: "Status",
      variant: "select",
      options: [
        { label: "Active", value: "active" },
        { label: "Inactive", value: "inactive" },
        { label: "Archived", value: "archived" },
      ],
    },
    enableColumnFilter: true,
  },
  {
    id: "createdAt",
    accessorKey: "createdAt",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Created Time" />,
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
    cell: ({ row }) => <ProjectActionsCell project={row.original} />,
    size: 40,
  },
];
