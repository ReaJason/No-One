import type { Project } from "@/types/project";
import type { ColumnDef } from "@tanstack/react-table";

import { CalendarIcon, Edit, Loader, MoreHorizontal, Terminal, Trash2 } from "lucide-react";
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

const PROJECT_STATUS_LABELS: Record<Project["status"], string> = {
  ACTIVE: "Active",
  ARCHIVED: "Archived",
  DRAFT: "Draft",
};

type DeleteFetcherData = {
  success?: boolean;
  errors?: Record<string, string>;
};

export const ProjectActionsCell = React.memo(function ProjectActionsCell({
  project,
}: {
  project: Project;
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
      toast.success("Project deleted");
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
            <DropdownMenuItem onClick={() => navigate(`/projects/edit/${project.id}`)}>
              <Edit className="mr-2 h-4 w-4" />
              Edit
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => navigate(`/shells?projectId=${project.id}`)}>
              <Terminal className="mr-2 h-4 w-4" />
              View Shells
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
          <deleteFetcher.Form method="post" action="/projects">
            <input type="hidden" name="intent" value="delete" />
            <input type="hidden" name="projectId" value={String(project.id)} />
            <AlertDialogHeader>
              <AlertDialogTitle>Are you sure you want to delete this project?</AlertDialogTitle>
              <AlertDialogDescription>
                This action cannot be undone. The project record and its metadata will be
                permanently removed.
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
    id: "bizName",
    accessorKey: "bizName",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Business Name" />,
    cell: ({ row }) => (row.getValue("bizName") as string) || "—",
  },
  {
    id: "status",
    accessorKey: "status",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Status" />,
    cell: ({ row }) => PROJECT_STATUS_LABELS[row.getValue("status") as Project["status"]],
    meta: {
      label: "Status",
      variant: "select",
      options: [
        { label: "Draft", value: "DRAFT" },
        { label: "Active", value: "ACTIVE" },
        { label: "Archived", value: "ARCHIVED" },
      ],
    },
    enableColumnFilter: true,
  },
  {
    id: "members",
    accessorKey: "members",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Members" />,
    cell: ({ row }) => {
      const members = row.original.members;
      if (!members || members.length === 0) return "—";
      const display = members
        .slice(0, 3)
        .map((m) => m.username)
        .join(", ");
      return members.length > 3 ? `${display} +${members.length - 3}` : display;
    },
    enableSorting: false,
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
    id: "updatedAt",
    accessorKey: "updatedAt",
    header: ({ column }) => <DataTableColumnHeader column={column} label="Updated Time" />,
    cell: ({ cell }) => formatDate(cell.getValue<Date>()),
  },
  {
    id: "actions",
    enableHiding: false,
    cell: ({ row }) => <ProjectActionsCell project={row.original} />,
    size: 40,
  },
];
