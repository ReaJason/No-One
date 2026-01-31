import type {ColumnDef} from "@tanstack/react-table";
import {CalendarIcon, Edit, MoreHorizontal, Terminal, Trash2,} from "lucide-react";
import {useNavigate} from "react-router";
import {toast} from "sonner";
import {deleteProject} from "@/api/project-api";
import {DataTableColumnHeader} from "@/components/data-table/data-table-column-header";
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
import type {Project} from "@/types/project";

export const projectColumns: ColumnDef<Project>[] = [
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
      label: "Project",
      variant: "text",
      placeholder: "Search by name or code...",
    },
    enableColumnFilter: true,
  },
  {
    id: "code",
    accessorKey: "code",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Code" />
    ),
    cell: ({ row }) => row.getValue("code") as string,
  },
  {
    id: "status",
    accessorKey: "status",
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Status" />
    ),
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
    cell: ({ row }) => {
      const project = row.original;
      const navigate = useNavigate();
      const handleEditProject = () => {
        navigate(`/projects/edit/${project.id}`);
      };

      const handleDeleteProject = async () => {
        if (
          !confirm(
            "Are you sure you want to delete this project? This action cannot be undone.",
          )
        )
          return;
        try {
          await deleteProject(project.id);
          toast.success("Project deleted");
          // Simple refresh to reflect deletion
          navigate(0);
        } catch (e: any) {
          toast.error(e?.message || "Failed to delete project");
        }
      };

      const handleViewShells = () => {
        // TODO: Navigate to shells page
        console.log("View shells for project:", project.id);
        navigate(`/shells?projectId=${project.id}`);
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
              <DropdownMenuItem onClick={handleEditProject}>
                <Edit className="mr-2 h-4 w-4" />
                Edit
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleViewShells}>
                <Terminal className="mr-2 h-4 w-4" />
                View Shells
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={handleDeleteProject}
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
