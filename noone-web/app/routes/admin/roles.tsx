import { Download, Plus } from "lucide-react";
import React, { use } from "react";
import type { LoaderFunctionArgs } from "react-router";
import { Link, useLoaderData } from "react-router";
import type { PaginatedResponse } from "@/api/api-client";
import { getRoles, loadRoleSearchParams } from "@/api/role-api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { RoleTableActionBar } from "@/components/role/role-action-bar";
import { roleColumns } from "@/components/role/role-columns";
import { Button } from "@/components/ui/button";
import { useDataTable } from "@/hooks/use-data-table";
import type { Role } from "@/types/admin";

export async function loader({ request }: LoaderFunctionArgs) {
  const { name, page, perPage, sortBy, sortOrder } =
    loadRoleSearchParams(request);
  return {
    roleResponse: getRoles({
      name,
      page,
      perPage,
      sortBy,
      sortOrder,
    }),
  };
}

export default function Roles() {
  const { roleResponse } = useLoaderData() as {
    roleResponse: Promise<PaginatedResponse<Role>>;
  };

  return (
    <div className="container mx-auto p-6 max-w-6xl">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-foreground">
            Role Management
          </h1>
          <p className="text-muted-foreground mt-1">
            Manage system roles and permission configurations
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
          <Link to="/admin/roles/create">
            <Button>
              <Plus className="w-4 h-4 mr-2" />
              Add Role
            </Button>
          </Link>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={5}
            filterCount={2}
            cellWidths={["10rem", "30rem", "10rem", "10rem", "6rem"]}
            shrinkZero
          />
        }
      >
        <RoleTable roleResponse={roleResponse} />
      </React.Suspense>
    </div>
  );
}

export function RoleTable({
  roleResponse,
}: {
  roleResponse: Promise<PaginatedResponse<Role>>;
}) {
  const roleResponseData = use(roleResponse);
  const { table } = useDataTable({
    columns: roleColumns,
    data: roleResponseData.content,
    pageCount: roleResponseData.totalPages,
    initialState: {
      pagination: {
        pageIndex: roleResponseData.page - 1,
        pageSize: roleResponseData.pageSize,
      },
    },
    shallow: false,
    clearOnDefault: true,
  });

  return (
    <DataTable table={table} actionBar={<RoleTableActionBar table={table} />}>
      <DataTableToolbar table={table} />
    </DataTable>
  );
}
