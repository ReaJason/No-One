import { Download, Plus } from "lucide-react";
import React, { use } from "react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import { Link, useLoaderData } from "react-router";
import { createAuthFetch } from "@/api.server";
import { deleteProject, getProjects, loadProjectSearchParams } from "@/api/project-api";
import type { PaginatedResponse } from "@/types/api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { ProjectTableActionBar } from "@/components/project/project-action-bar";
import { projectColumns } from "@/components/project/project-columns";
import { Button } from "@/components/ui/button";
import { useDataTable } from "@/hooks/use-data-table";
import type { Project } from "@/types/project";

export async function loader({ request, context }: LoaderFunctionArgs) {
  const { name, page, perPage, sortBy, sortOrder } = loadProjectSearchParams(request);
  const authFetch = createAuthFetch(request, context);
  const projectResponse = await getProjects({ name, page, perPage, sortBy, sortOrder }, authFetch);

  return {
    projectResponse: Promise.resolve(projectResponse),
  };
}

export async function action({ request, context }: ActionFunctionArgs) {
  const formData = await request.formData();
  if (formData.get("intent") !== "delete") {
    return { errors: { general: "Unsupported action" } };
  }
  const projectId = String(formData.get("projectId") ?? "").trim();
  if (!projectId) {
    return { errors: { general: "Invalid project ID" } };
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await deleteProject(projectId, authFetch);
    return { success: true };
  } catch (error: any) {
    return { errors: { general: error?.message || "Failed to delete project" } };
  }
}

export default function ProjectList() {
  const { projectResponse } = useLoaderData() as {
    projectResponse: Promise<PaginatedResponse<Project>>;
  };

  return (
    <div className="container mx-auto max-w-6xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-balance">Project Management</h1>
          <p className="mt-2 text-muted-foreground">Manage your all projects</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Link to="/projects/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              Create Project
            </Button>
          </Link>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={8}
            filterCount={4}
            cellWidths={["10rem", "25rem", "12rem", "8rem", "8rem", "8rem", "15rem", "6rem"]}
            shrinkZero
          />
        }
      >
        <ProjectTable projectResponse={projectResponse} />
      </React.Suspense>
    </div>
  );
}

function ProjectTable({
  projectResponse,
}: {
  projectResponse: Promise<PaginatedResponse<Project>>;
}) {
  const projectResponseData = use(projectResponse);
  const { table } = useDataTable({
    columns: projectColumns,
    data: projectResponseData.content,
    pageCount: projectResponseData.totalPages,
    initialState: {
      pagination: {
        pageIndex: projectResponseData.page - 1,
        pageSize: projectResponseData.pageSize,
      },
    },
    shallow: false,
    clearOnDefault: true,
  });

  return (
    <DataTable table={table} actionBar={<ProjectTableActionBar table={table} />}>
      <DataTableToolbar table={table} />
    </DataTable>
  );
}
