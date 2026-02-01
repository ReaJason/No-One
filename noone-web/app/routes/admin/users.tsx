import { Download, Plus } from "lucide-react";
import * as React from "react";
import { use } from "react";
import type { LoaderFunctionArgs } from "react-router";
import { Link, useLoaderData } from "react-router";
import type { PaginatedResponse } from "@/api/api-client";
import { getAllRoles } from "@/api/role-api";
import { getUsers, loadUserSearchParams } from "@/api/user-api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { Button } from "@/components/ui/button";
import { UsersTableActionBar } from "@/components/user/user-action-bar";
import { useUserColumns } from "@/components/user/user-columns";
import { useDataTable } from "@/hooks/use-data-table";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import type { User } from "@/types/admin";

export const handle = createBreadcrumb(() => ({
  id: "users",
  label: "Users",
  to: "/admin/users",
}));

export async function loader({ request }: LoaderFunctionArgs) {
  const {
    username,
    roles: roleId,
    enabled,
    page,
    perPage,
    sortBy,
    sortOrder,
  } = loadUserSearchParams(request);
  return {
    userResponse: getUsers({
      username,
      roleId,
      enabled,
      page,
      perPage,
      sortBy,
      sortOrder,
    }),
    roles: getAllRoles(),
  };
}

export default function Users() {
  const { userResponse, roles } = useLoaderData() as {
    userResponse: Promise<PaginatedResponse<User>>;
    roles: Promise<any[]>;
  };

  return (
    <div className="container mx-auto p-6 max-w-6xl">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-foreground">
            User Management
          </h1>
          <p className="text-muted-foreground mt-1">
            Manage system user accounts and permissions
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
          <Link to="/admin/users/create">
            <Button>
              <Plus className="w-4 h-4 mr-2" />
              Add User
            </Button>
          </Link>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={5}
            filterCount={3}
            cellWidths={[
              "10rem",
              "30rem",
              "10rem",
              "10rem",
              "6rem",
              "6rem",
              "6rem",
            ]}
            shrinkZero
          />
        }
      >
        <UserTable userResponse={userResponse} roles={roles} />
      </React.Suspense>
    </div>
  );
}

export function UserTable({
  userResponse,
  roles,
}: {
  userResponse: Promise<PaginatedResponse<User>>;
  roles: Promise<any[]>;
}) {
  const userResponseData = use(userResponse);
  const rolesData = use(roles);
  const columns = useUserColumns(rolesData);

  const { table } = useDataTable({
    columns,
    data: userResponseData.content,
    pageCount: userResponseData.totalPages,
    initialState: {
      pagination: {
        pageIndex: userResponseData.page - 1,
        pageSize: userResponseData.pageSize,
      },
    },
    shallow: false,
    clearOnDefault: true,
  });
  return (
    <DataTable table={table} actionBar={<UsersTableActionBar table={table} />}>
      <DataTableToolbar table={table} />
    </DataTable>
  );
}
