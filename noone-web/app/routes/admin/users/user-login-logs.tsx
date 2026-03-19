import type { LoginLog, User } from "@/types/admin";
import type { PaginatedResponse } from "@/types/api";
import type { LoaderFunctionArgs } from "react-router";

import React, { use } from "react";
import { isRouteErrorResponse, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getUserById, getUserLoginLogs, loadUserLoginLogSearchParams } from "@/api/user-api";
import { AuthRedirectErrorBoundary } from "@/components/auth-redirect-error-boundary";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { loginLogColumns } from "@/components/user/user-login-log-columns";
import { useDataTable } from "@/hooks/use-data-table";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";

type LoaderData = {
  user: Promise<Pick<User, "id" | "username" | "email">>;
  loginLogResponse: Promise<PaginatedResponse<LoginLog>>;
};

export async function loader({
  request,
  context,
  params,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const userId = Number(params.userId);
  if (!Number.isFinite(userId)) {
    throw new Response("User not found", { status: 404 });
  }

  const authFetch = createAuthFetch(request, context);
  const filters = loadUserLoginLogSearchParams(request);
  const userPromise = getUserById(userId, authFetch);
  const loginLogResponse = getUserLoginLogs(userId, filters, authFetch);
  const user = await userPromise;
  if (!user) {
    throw new Response("User not found", { status: 404 });
  }

  return {
    user: Promise.resolve({ id: user.id, username: user.username, email: user.email }),
    loginLogResponse,
  };
}

export const handle = createBreadcrumb(({ params }) => ({
  id: "users-login-logs",
  label: "Login Logs",
  to: `/admin/users/${params.userId}/login-logs`,
}));

export function ErrorBoundary({ error, params }: { error: unknown; params: { userId?: string } }) {
  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <NotFoundErrorBoundary
        title="User not found"
        backLabel="Back to Users"
        backHref="/admin/users"
        resourceType="User"
        resourceId={params.userId}
      />
    );
  }

  throw error;
}

export default function UserLoginLogs() {
  const { loginLogResponse } = useLoaderData() as LoaderData;

  return (
    <FormPageShell
      backHref="/admin/users"
      backLabel="Return to user list"
      title="Login Logs"
      description="Inspect sign-in outcomes, source IPs, and session IDs for this user."
      contentClassName="max-w-7xl"
    >
      <AuthRedirectErrorBoundary>
        <React.Suspense
          fallback={
            <DataTableSkeleton
              columnCount={5}
              filterCount={4}
              cellWidths={["10rem", "14rem", "10rem", "18rem", "12rem"]}
              shrinkZero
            />
          }
        >
          <LoginLogTable loginLogResponse={loginLogResponse} />
        </React.Suspense>
      </AuthRedirectErrorBoundary>
    </FormPageShell>
  );
}

function LoginLogTable({
  loginLogResponse,
}: {
  loginLogResponse: Promise<PaginatedResponse<LoginLog>>;
}) {
  const response = use(loginLogResponse);
  const { table } = useDataTable({
    columns: loginLogColumns,
    data: response.content,
    pageCount: response.totalPages,
    initialState: {
      pagination: {
        pageIndex: response.page - 1,
        pageSize: response.pageSize,
      },
      sorting: {
        sortBy: "loginTime",
        sortOrder: "desc",
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
