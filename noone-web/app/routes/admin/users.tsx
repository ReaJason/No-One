import type { Role, User, UserStatus } from "@/types/admin";
import type { PaginatedResponse } from "@/types/api";
import type { LoaderFunctionArgs } from "react-router";

import { Download, Plus } from "lucide-react";
import * as React from "react";
import { use } from "react";
import { Link, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
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

export const handle = createBreadcrumb(() => ({
  id: "users",
  label: "Users",
  to: "/admin/users",
}));

export async function loader({ request, context }: LoaderFunctionArgs) {
  const {
    username,
    roleId: explicitRoleId,
    roles: roleId,
    status,
    enabled,
    page,
    perPage,
    sortBy,
    sortOrder,
  } = loadUserSearchParams(request);
  const resolvedStatus: UserStatus | null =
    status ?? (enabled == null ? null : enabled ? "ENABLED" : "DISABLED");

  const authFetch = createAuthFetch(request, context);
  const userResponse = getUsers(
    {
      username,
      roleId: explicitRoleId ?? roleId,
      status: resolvedStatus,
      enabled,
      page,
      perPage,
      sortBy,
      sortOrder,
    },
    authFetch,
  );
  const roles = getAllRoles(authFetch);

  return {
    userResponse,
    roles,
  };
}

export default function Users() {
  const { userResponse, roles } = useLoaderData() as {
    userResponse: Promise<PaginatedResponse<User>>;
    roles: Promise<Role[]>;
  };

  return (
    <div className="container mx-auto max-w-7xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground">User Management</h1>
          <p className="mt-1 text-muted-foreground">Manage system user accounts and permissions</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Link to="/admin/users/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
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
            cellWidths={["10rem", "30rem", "10rem", "10rem", "6rem", "6rem", "6rem"]}
            shrinkZero
          />
        }
      >
        <UserTable userResponse={userResponse} roles={roles} />
      </React.Suspense>
    </div>
  );
}

function UserTable({
  userResponse,
  roles,
}: {
  userResponse: Promise<PaginatedResponse<User>>;
  roles: Promise<Role[]>;
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
