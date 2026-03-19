import type { User, UserSession } from "@/types/admin";
import type { PaginatedResponse } from "@/types/api";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import React, { use } from "react";
import { isRouteErrorResponse, redirect, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import {
  getUserById,
  getUserSessions,
  loadUserSessionSearchParams,
  revokeAllUserSessions,
  revokeUserSession,
} from "@/api/user-api";
import { AuthRedirectErrorBoundary } from "@/components/auth-redirect-error-boundary";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { Button } from "@/components/ui/button";
import { createUserSessionColumns } from "@/components/user/user-session-columns";
import { useDataTable } from "@/hooks/use-data-table";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";

type LoaderData = {
  userId: number;
  user: Promise<Pick<User, "id" | "username" | "email">>;
  sessionResponse: Promise<PaginatedResponse<UserSession>>;
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
  const filters = loadUserSessionSearchParams(request);
  const userPromise = getUserById(userId, authFetch);
  const sessionResponse = getUserSessions(userId, filters, authFetch);
  const user = await userPromise;
  if (!user) {
    throw new Response("User not found", { status: 404 });
  }

  return {
    userId,
    user: Promise.resolve({ id: user.id, username: user.username, email: user.email }),
    sessionResponse,
  };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const userId = Number(params.userId);
  if (!Number.isFinite(userId)) {
    throw new Response("User not found", { status: 404 });
  }

  const formData = await request.formData();
  const intent = String(formData.get("intent") ?? "");
  const authFetch = createAuthFetch(request, context);

  try {
    if (intent === "revoke-all") {
      await revokeAllUserSessions(userId, authFetch);
    } else if (intent === "revoke-session") {
      const sessionId = String(formData.get("sessionId") ?? "");
      if (!sessionId) {
        return { errors: { general: "Session ID is required" } };
      }
      await revokeUserSession(userId, sessionId, authFetch);
    } else {
      return { errors: { general: "Unsupported action" } };
    }
  } catch (error: any) {
    return { errors: { general: error?.message || "Failed to update sessions" } };
  }

  const url = new URL(request.url);
  return redirect(`${url.pathname}${url.search}`);
}

export const handle = createBreadcrumb(({ params }) => ({
  id: "users-sessions",
  label: "Sessions",
  to: `/admin/users/${params.userId}/sessions`,
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

export default function UserSessions() {
  const { userId, sessionResponse } = useLoaderData() as LoaderData;

  return (
    <FormPageShell
      backHref="/admin/users"
      backLabel="Return to user list"
      title="Sessions"
      description="Review active and revoked sessions for this user, then revoke risky sessions immediately."
      contentClassName="max-w-7xl"
    >
      <div className="mb-4 flex justify-end">
        <form method="post">
          <input type="hidden" name="intent" value="revoke-all" />
          <Button type="submit" variant="destructive">
            Revoke all sessions
          </Button>
        </form>
      </div>

      <AuthRedirectErrorBoundary>
        <React.Suspense
          fallback={
            <DataTableSkeleton
              columnCount={6}
              filterCount={4}
              cellWidths={["14rem", "10rem", "12rem", "8rem", "12rem", "8rem"]}
              shrinkZero
            />
          }
        >
          <SessionTable userId={userId} sessionResponse={sessionResponse} />
        </React.Suspense>
      </AuthRedirectErrorBoundary>
    </FormPageShell>
  );
}

function SessionTable({
  userId,
  sessionResponse,
}: {
  userId: number;
  sessionResponse: Promise<PaginatedResponse<UserSession>>;
}) {
  const response = use(sessionResponse);
  const columns = React.useMemo(() => createUserSessionColumns(userId), [userId]);
  const { table } = useDataTable({
    columns,
    data: response.content,
    pageCount: response.totalPages,
    initialState: {
      pagination: {
        pageIndex: response.page - 1,
        pageSize: response.pageSize,
      },
      sorting: {
        sortBy: "createdAt",
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
