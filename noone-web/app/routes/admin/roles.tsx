import type { Role } from "@/types/admin";
import type { PaginatedResponse } from "@/types/api";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { Download, Plus } from "lucide-react";
import React, { use } from "react";
import { Link, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { deleteRole, getRoles, loadRoleSearchParams } from "@/api/role-api";
import { AuthRedirectErrorBoundary } from "@/components/auth-redirect-error-boundary";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { RoleTableActionBar } from "@/components/role/role-action-bar";
import { roleColumns } from "@/components/role/role-columns";
import { Button } from "@/components/ui/button";
import { useDataTable } from "@/hooks/use-data-table";

export async function loader({ request, context }: LoaderFunctionArgs) {
  const { name, page, perPage, sortBy, sortOrder } = loadRoleSearchParams(request);
  const authFetch = createAuthFetch(request, context);
  const roleResponse = getRoles(
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
    roleResponse,
  };
}

export async function action({ request, context }: ActionFunctionArgs) {
  const formData = await request.formData();
  if (formData.get("intent") !== "delete") {
    return { errors: { general: "Unsupported action" } };
  }

  const roleId = Number(String(formData.get("roleId") ?? ""));
  if (!Number.isFinite(roleId)) {
    return { errors: { general: "Invalid role ID" } };
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await deleteRole(roleId, authFetch);
    return { success: true };
  } catch (error: any) {
    return { errors: { general: error?.message || "Failed to delete role" } };
  }
}

export default function Roles() {
  const { roleResponse } = useLoaderData() as {
    roleResponse: Promise<PaginatedResponse<Role>>;
  };

  return (
    <div className="container mx-auto max-w-7xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Role Management</h1>
          <p className="mt-1 text-muted-foreground">
            Manage system roles and permission configurations
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Link to="/admin/roles/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              Add Role
            </Button>
          </Link>
        </div>
      </div>

      <AuthRedirectErrorBoundary>
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
      </AuthRedirectErrorBoundary>
    </div>
  );
}

export function RoleTable({ roleResponse }: { roleResponse: Promise<PaginatedResponse<Role>> }) {
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
