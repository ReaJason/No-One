import type { PaginatedResponse } from "@/types/api";
import type { ShellOperationLog } from "@/types/shell-operation-log";
import type { LoaderFunctionArgs } from "react-router";

import React, { use, useMemo } from "react";
import { useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getShellOperationLogs } from "@/api/shell-operation-log-api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { getShellManagerOperationColumns } from "@/components/shell/shell-operation-columns";
import { useDataTable } from "@/hooks/use-data-table";
import { loadShellManagerOperationSearchParams } from "@/lib/shell-operation-search-param";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = params.shellId as string;
  const { pluginId, operation, success, page, perPage, sortBy, sortOrder } =
    loadShellManagerOperationSearchParams(request);

  const authFetch = createAuthFetch(request, context);
  const shellOperationResponse = getShellOperationLogs(
    Number(shellId),
    {
      pluginId: pluginId ?? undefined,
      operation: operation ?? undefined,
      success: success ?? undefined,
      page,
      pageSize: perPage,
      sortBy,
      sortOrder,
    },
    authFetch,
  );

  return { shellOperationResponse };
}

export default function ShellOperationsRoute() {
  const { shellOperationResponse } = useLoaderData() as {
    shellOperationResponse: Promise<PaginatedResponse<ShellOperationLog>>;
  };

  return (
    <div className="p-4">
      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={7}
            filterCount={2}
            cellWidths={["8rem", "10rem", "7rem", "6rem", "6rem", "10rem", "6rem"]}
            shrinkZero
          />
        }
      >
        <ShellManagerOperationTable shellOperationResponse={shellOperationResponse} />
      </React.Suspense>
    </div>
  );
}

function ShellManagerOperationTable({
  shellOperationResponse,
}: {
  shellOperationResponse: Promise<PaginatedResponse<ShellOperationLog>>;
}) {
  const data = use(shellOperationResponse);
  const columns = useMemo(() => getShellManagerOperationColumns(), []);
  const { table } = useDataTable({
    columns,
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
