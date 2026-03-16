import type { PaginatedResponse } from "@/types/api";
import type { AuditLog } from "@/types/audit";
import type { LoaderFunctionArgs } from "react-router";

import React, { use } from "react";
import { useLoaderData } from "react-router";

import { createAuthFetch } from "@/api.server";
import { getAuditLogs, loadAuditSearchParams } from "@/api/audit-api";
import { auditColumns } from "@/components/audit/audit-columns";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { useDataTable } from "@/hooks/use-data-table";

export async function loader({ request, context }: LoaderFunctionArgs) {
  const { module, action, username, success, page, perPage, sortBy, sortOrder } =
    loadAuditSearchParams(request);
  const authFetch = createAuthFetch(request, context);
  const auditResponse = await getAuditLogs(
    { module, action, username, success, page, perPage, sortBy, sortOrder },
    authFetch,
  );

  return {
    auditResponse: Promise.resolve(auditResponse),
  };
}

export default function Audit() {
  const { auditResponse } = useLoaderData() as {
    auditResponse: Promise<PaginatedResponse<AuditLog>>;
  };

  return (
    <div className="container mx-auto max-w-6xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Audit Logs</h1>
          <p className="mt-1 text-muted-foreground">View and manage system audit logs</p>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={8}
            filterCount={3}
            cellWidths={["8rem", "7rem", "9rem", "8rem", "7rem", "9rem", "10rem", "6rem"]}
            shrinkZero
          />
        }
      >
        <AuditTable auditResponse={auditResponse} />
      </React.Suspense>
    </div>
  );
}

export function AuditTable({
  auditResponse,
}: {
  auditResponse: Promise<PaginatedResponse<AuditLog>>;
}) {
  const auditResponseData = use(auditResponse);
  const { table } = useDataTable({
    columns: auditColumns,
    data: auditResponseData.content,
    pageCount: auditResponseData.totalPages,
    initialState: {
      pagination: {
        pageIndex: auditResponseData.page - 1,
        pageSize: auditResponseData.pageSize,
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
