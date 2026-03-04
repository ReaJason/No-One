import { Download, Plus } from "lucide-react";
import React, { use, useMemo } from "react";
import type { LoaderFunctionArgs } from "react-router";
import { Link, useLoaderData } from "react-router";
import type { PaginatedResponse } from "@/api/api-client";
import { getAllProjects } from "@/api/project-api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { getShellColumns } from "@/components/shell/shell-columns";
import { Button } from "@/components/ui/button";
import { useDataTable } from "@/hooks/use-data-table";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import { getShellConnections } from "@/api/shell-connection-api";
import { loadShellConnectionSearchParams } from "@/lib/shell-connection-search-param";
import type { ShellConnection } from "@/types/shell-connection";
import type { Project } from "@/types/project";

export async function loader({ request }: LoaderFunctionArgs) {
  const params = await loadShellConnectionSearchParams(request);
  return {
    shellConnectionResponse: getShellConnections(params),
    projectsResponse: getAllProjects(),
  };
}

export const handle = createBreadcrumb(() => ({
  id: "shells",
  label: "All Shell Connections",
  to: "/shells",
}));

export default function ShellList() {
  const { shellConnectionResponse, projectsResponse } = useLoaderData() as {
    shellConnectionResponse: Promise<PaginatedResponse<ShellConnection>>;
    projectsResponse: Promise<Project[]>;
  };

  return (
    <div className="container mx-auto max-w-6xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Shell Connections</h1>
          <p className="mt-1 text-muted-foreground">Manage shell connections and remote access</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Link to="/shells/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              Add Shell
            </Button>
          </Link>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={8}
            filterCount={2}
            cellWidths={["3rem", "20rem", "8rem", "10rem", "12rem", "10rem", "10rem", "3rem"]}
            shrinkZero
          />
        }
      >
        <ShellConnectionTable
          shellConnectionResponse={shellConnectionResponse}
          projectsResponse={projectsResponse}
        />
      </React.Suspense>
    </div>
  );
}

export function ShellConnectionTable({
  shellConnectionResponse,
  projectsResponse,
}: {
  shellConnectionResponse: Promise<PaginatedResponse<ShellConnection>>;
  projectsResponse: Promise<Project[]>;
}) {
  const shellConnectionResponseData = use(shellConnectionResponse);
  const projects = use(projectsResponse);

  const { projectMap, projectOptions } = useMemo(() => {
    const map = new Map<number, string>();
    const options: Array<{ label: string; value: string }> = [];
    for (const project of projects) {
      map.set(Number(project.id), project.name);
      options.push({ label: project.name, value: project.id });
    }
    return { projectMap: map, projectOptions: options };
  }, [projects]);

  const columns = useMemo(
    () => getShellColumns({ projectMap, projectOptions }),
    [projectMap, projectOptions],
  );

  const { table } = useDataTable({
    columns,
    data: shellConnectionResponseData.content,
    pageCount: shellConnectionResponseData.totalPages,
    initialState: {
      pagination: {
        pageIndex: shellConnectionResponseData.page - 1,
        pageSize: shellConnectionResponseData.pageSize,
      },
    },
    shallow: false,
    clearOnDefault: true,
  });

  return (
    <DataTable table={table}>
      <DataTableToolbar table={table} />
    </DataTable>
  );
}
