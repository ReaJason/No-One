import type { Permission } from "@/types/admin";
import type { PaginatedResponse } from "@/types/api";
import type { LoaderFunctionArgs } from "react-router";

import { Download, Plus } from "lucide-react";
import React, { use } from "react";
import { Link, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getPermissions, loadPermissionSearchParams } from "@/api/permission-api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { PermissionTableActionBar } from "@/components/permission/permission-action-bar";
import { permissionColumns } from "@/components/permission/permission-columns";
import { Button } from "@/components/ui/button";
import { useDataTable } from "@/hooks/use-data-table";

export async function loader({ request, context }: LoaderFunctionArgs) {
  const { name, page, perPage, sortBy, sortOrder } = loadPermissionSearchParams(request);
  const authFetch = createAuthFetch(request, context);
  const permissionResponse = getPermissions(
    {
      name,
      page,
      perPage,
      sortBy,
      sortOrder,
    },
    authFetch,
  );

  return {
    permissionResponse,
  };
}

export default function Permissions() {
  const { permissionResponse } = useLoaderData() as {
    permissionResponse: Promise<PaginatedResponse<Permission>>;
  };

  return (
    <div className="container mx-auto max-w-7xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Permission Management</h1>
          <p className="mt-1 text-muted-foreground">
            Manage system permissions and access controls
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Link to="/admin/permissions/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              Add Permission
            </Button>
          </Link>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={7}
            filterCount={4}
            cellWidths={["10rem", "25rem", "8rem", "8rem", "12rem", "8rem", "6rem"]}
            shrinkZero
          />
        }
      >
        <PermissionTable permissionResponse={permissionResponse} />
      </React.Suspense>
    </div>
  );
}

export function PermissionTable({
  permissionResponse,
}: {
  permissionResponse: Promise<PaginatedResponse<Permission>>;
}) {
  const permissionResponseData = use(permissionResponse);
  const { table } = useDataTable({
    columns: permissionColumns,
    data: permissionResponseData.content,
    pageCount: permissionResponseData.totalPages,
    initialState: {
      pagination: {
        pageIndex: permissionResponseData.page - 1,
        pageSize: permissionResponseData.pageSize,
      },
    },
    shallow: false,
    clearOnDefault: true,
  });

  return (
    <DataTable table={table} actionBar={<PermissionTableActionBar table={table} />}>
      <DataTableToolbar table={table} />
    </DataTable>
  );
}
