import React, { use } from "react";
import type { LoaderFunctionArgs } from "react-router";
import { useLoaderData } from "react-router";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { useDataTable } from "@/hooks/use-data-table";

// Mock audit data
interface AuditLog {
  id: string;
  userId: string;
  userName: string;
  action: string;
  resource: string;
  ipAddress: string;
  userAgent: string;
  timestamp: string;
  status: "success" | "failed";
}

const mockAuditLogs: AuditLog[] = [
  {
    id: "1",
    userId: "user_1",
    userName: "张三",
    action: "login",
    resource: "system",
    ipAddress: "192.168.1.100",
    userAgent: "Mozilla/5.0...",
    timestamp: "2024-01-20T10:00:00Z",
    status: "success",
  },
  {
    id: "2",
    userId: "user_2",
    userName: "李四",
    action: "create_user",
    resource: "user",
    ipAddress: "192.168.1.101",
    userAgent: "Mozilla/5.0...",
    timestamp: "2024-01-20T10:30:00Z",
    status: "success",
  },
  {
    id: "3",
    userId: "user_3",
    userName: "王五",
    action: "delete_role",
    resource: "role",
    ipAddress: "192.168.1.102",
    userAgent: "Mozilla/5.0...",
    timestamp: "2024-01-20T11:00:00Z",
    status: "failed",
  },
];

export async function loader(_args: LoaderFunctionArgs) {
  // Mock loader - in real app, this would fetch from API
  return {
    auditResponse: Promise.resolve({
      data: mockAuditLogs,
      total: mockAuditLogs.length,
      page: 1,
      pageSize: 10,
      totalPages: 1,
    }),
  };
}

export default function Audit() {
  const { auditResponse } = useLoaderData() as {
    auditResponse: Promise<{
      data: AuditLog[];
      total: number;
      page: number;
      pageSize: number;
      totalPages: number;
    }>;
  };

  return (
    <div className="container mx-auto p-6 max-w-6xl">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Audit Logs</h1>
          <p className="text-muted-foreground mt-1">
            View and manage system audit logs
          </p>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={7}
            filterCount={3}
            cellWidths={[
              "10rem",
              "12rem",
              "15rem",
              "12rem",
              "15rem",
              "12rem",
              "6rem",
            ]}
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
  auditResponse: Promise<{
    data: AuditLog[];
    total: number;
    page: number;
    pageSize: number;
    totalPages: number;
  }>;
}) {
  const auditResponseData = use(auditResponse);
  const { table } = useDataTable({
    columns: [
      {
        id: "userName",
        accessorKey: "userName",
        header: "User",
      },
      {
        id: "action",
        accessorKey: "action",
        header: "Action",
      },
      {
        id: "resource",
        accessorKey: "resource",
        header: "Resource",
      },
      {
        id: "ipAddress",
        accessorKey: "ipAddress",
        header: "IP Address",
      },
      {
        id: "timestamp",
        accessorKey: "timestamp",
        header: "Timestamp",
        cell: ({ row }) => {
          const timestamp = row.getValue("timestamp") as string;
          return new Date(timestamp).toLocaleString();
        },
      },
      {
        id: "status",
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => {
          const status = row.getValue("status") as string;
          return (
            <span
              className={`px-2 py-1 rounded text-xs ${
                status === "success"
                  ? "bg-green-100 text-green-800"
                  : "bg-red-100 text-red-800"
              }`}
            >
              {status}
            </span>
          );
        },
      },
    ],
    data: auditResponseData.data,
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
