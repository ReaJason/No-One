import type { User, UserIpWhitelistEntry } from "@/types/admin";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import React, { use } from "react";
import { isRouteErrorResponse, redirect, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getUserById } from "@/api/user-api";
import {
  createUserIpWhitelistEntry,
  deleteUserIpWhitelistEntry,
  getUserIpWhitelist,
} from "@/api/user-ip-whitelist-api";
import { AuthRedirectErrorBoundary } from "@/components/auth-redirect-error-boundary";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { createUserIpWhitelistColumns } from "@/components/user/user-ip-whitelist-columns";
import { UserIpWhitelistForm } from "@/components/user/user-ip-whitelist-form";
import { useDataTable } from "@/hooks/use-data-table";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  readWhitelistEntryId,
  readWhitelistIntent,
  readWhitelistIpAddress,
} from "@/routes/admin/users/user-ip-whitelist-form.shared";

type LoaderData = {
  user: Promise<Pick<User, "id" | "username" | "email">>;
  entriesPromise: Promise<UserIpWhitelistEntry[]>;
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
  const userPromise = getUserById(userId, authFetch);
  const entriesPromise = getUserIpWhitelist(userId, authFetch);
  const user = await userPromise;
  if (!user) {
    throw new Response("User not found", { status: 404 });
  }

  return {
    user: Promise.resolve({ id: user.id, username: user.username, email: user.email }),
    entriesPromise,
  };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const userId = Number(params.userId);
  if (!Number.isFinite(userId)) {
    throw new Response("User not found", { status: 404 });
  }

  const formData = await request.formData();
  const intent = readWhitelistIntent(formData);
  const authFetch = createAuthFetch(request, context);

  try {
    if (intent === "add") {
      const ipAddress = readWhitelistIpAddress(formData);
      if (!ipAddress) {
        return { errors: { ipAddress: "IP address is required" } };
      }
      await createUserIpWhitelistEntry(userId, { ipAddress }, authFetch);
    } else if (intent === "delete") {
      const entryId = readWhitelistEntryId(formData);
      if (!Number.isFinite(entryId)) {
        return { errors: { general: "Invalid whitelist entry" } };
      }
      await deleteUserIpWhitelistEntry(userId, entryId, authFetch);
    } else {
      return { errors: { general: "Unsupported action" } };
    }
  } catch (error: any) {
    return { errors: { general: error?.message || "Failed to update whitelist" } };
  }

  const url = new URL(request.url);
  return redirect(`${url.pathname}${url.search}`);
}

export const handle = createBreadcrumb(({ params }) => ({
  id: "users-ip-whitelist",
  label: "IP Whitelist",
  to: `/admin/users/${params.userId}/ip-whitelist`,
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

export default function UserIpWhitelist() {
  const { entriesPromise } = useLoaderData() as LoaderData;

  return (
    <FormPageShell
      backHref="/admin/users"
      backLabel="Return to user list"
      title="Login IP Whitelist"
      description="Restrict this user to explicit IPv4 or IPv6 login sources."
      contentClassName="max-w-7xl space-y-6"
    >
      <UserIpWhitelistForm />

      <AuthRedirectErrorBoundary>
        <React.Suspense
          fallback={
            <DataTableSkeleton
              columnCount={3}
              filterCount={2}
              cellWidths={["12rem", "12rem", "8rem"]}
              shrinkZero
            />
          }
        >
          <UserIpWhitelistTable entriesPromise={entriesPromise} />
        </React.Suspense>
      </AuthRedirectErrorBoundary>
    </FormPageShell>
  );
}

function UserIpWhitelistTable({
  entriesPromise,
}: {
  entriesPromise: Promise<UserIpWhitelistEntry[]>;
}) {
  const entries = use(entriesPromise);
  const columns = React.useMemo(() => createUserIpWhitelistColumns(0), []);
  const { table } = useDataTable({
    columns,
    data: entries,
    pageCount: 1,
    initialState: {
      pagination: {
        pageIndex: 0,
        pageSize: Math.max(entries.length, 10),
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
