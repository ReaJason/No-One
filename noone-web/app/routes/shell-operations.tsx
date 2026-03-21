import type { PaginatedResponse } from "@/types/api";
import type { ShellOperationLog } from "@/types/shell-operation-log";
import type { LoaderFunctionArgs } from "react-router";

import React, { use } from "react";
import { useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import {
  getAllShellOperationLogs,
  loadShellOperationSearchParams,
} from "@/api/shell-operation-log-api";
import { AuthRedirectErrorBoundary } from "@/components/auth-redirect-error-boundary";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { shellOperationColumns } from "@/components/shell/shell-operation-columns";
import { useDataTable } from "@/hooks/use-data-table";

export async function loader({ request, context }: LoaderFunctionArgs) {
  const { shellId, pluginId, operation, success, page, perPage, sortBy, sortOrder } =
    loadShellOperationSearchParams(request);
  const authFetch = createAuthFetch(request, context);
  const shellOperationResponse = getAllShellOperationLogs(
    { shellId, pluginId, operation, success, page, perPage, sortBy, sortOrder },
    authFetch,
  );

  return {
    shellOperationResponse,
  };
}

export default function ShellOperations() {
  const { shellOperationResponse } = useLoaderData() as {
    shellOperationResponse: Promise<PaginatedResponse<ShellOperationLog>>;
  };

  return (
    <div className="container mx-auto max-w-7xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Shell Operations</h1>
          <p className="mt-1 text-muted-foreground">View shell operation logs across all shells</p>
        </div>
      </div>

      <AuthRedirectErrorBoundary>
        <React.Suspense
          fallback={
            <DataTableSkeleton
              columnCount={9}
              filterCount={4}
              cellWidths={[
                "6rem",
                "7rem",
                "8rem",
                "10rem",
                "7rem",
                "6rem",
                "6rem",
                "10rem",
                "6rem",
              ]}
              shrinkZero
            />
          }
        >
          <ShellOperationTable shellOperationResponse={shellOperationResponse} />
        </React.Suspense>
      </AuthRedirectErrorBoundary>
    </div>
  );
}

export function ShellOperationTable({
  shellOperationResponse,
}: {
  shellOperationResponse: Promise<PaginatedResponse<ShellOperationLog>>;
}) {
  const data = use(shellOperationResponse);
  const { table } = useDataTable({
    columns: shellOperationColumns,
    data: data.content,
    pageCount: data.totalPages,
    initialState: {
      pagination: {
        pageIndex: data.page - 1,
        pageSize: data.pageSize,
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
