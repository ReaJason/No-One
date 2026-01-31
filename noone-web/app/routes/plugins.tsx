import {Download, Plus} from "lucide-react";
import React, {use} from "react";
import type {LoaderFunctionArgs} from "react-router";
import {useLoaderData} from "react-router";
import {DataTable} from "@/components/data-table/data-table";
import {DataTableSkeleton} from "@/components/data-table/data-table-skeleton";
import {DataTableToolbar} from "@/components/data-table/data-table-toolbar";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {useDataTable} from "@/hooks/use-data-table";
import {createBreadcrumb} from "@/lib/breadcrumb-utils";
import {formatDate} from "@/lib/format";

// Mock plugin data
interface Plugin {
  id: string;
  name: string;
  description: string;
  version: string;
  author: string;
  status: "active" | "inactive" | "error";
  category: string;
  createdAt: string;
}

const mockPlugins: Plugin[] = [
  {
    id: "1",
    name: "Security Scanner",
    description: "Advanced security vulnerability scanner",
    version: "1.2.0",
    author: "Security Team",
    status: "active",
    category: "Security",
    createdAt: "2024-01-15T08:00:00Z",
  },
  {
    id: "2",
    name: "Performance Monitor",
    description: "Real-time performance monitoring tool",
    version: "2.1.0",
    author: "DevOps Team",
    status: "active",
    category: "Monitoring",
    createdAt: "2024-01-20T09:00:00Z",
  },
  {
    id: "3",
    name: "Data Backup",
    description: "Automated data backup solution",
    version: "1.0.5",
    author: "IT Team",
    status: "inactive",
    category: "Backup",
    createdAt: "2024-02-01T10:00:00Z",
  },
];

export async function loader(_args: LoaderFunctionArgs) {
  return {
    pluginResponse: Promise.resolve({
      data: mockPlugins,
      total: mockPlugins.length,
      page: 1,
      pageSize: 10,
      totalPages: 1,
    }),
  };
}

export const handle = createBreadcrumb(() => ({
  id: "plugins",
  label: "Plugins",
  to: "/plugins",
}));

export default function Plugins() {
  const { pluginResponse } = useLoaderData() as {
    pluginResponse: Promise<{
      data: Plugin[];
      total: number;
      page: number;
      pageSize: number;
      totalPages: number;
    }>;
  };

  const handleCreatePlugin = () => {
    console.log("Create plugin");
  };

  return (
    <div className="container mx-auto p-6 max-w-6xl">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-foreground">
            Plugin Management
          </h1>
          <p className="text-muted-foreground mt-1">
            Manage system plugins and extensions
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
          <Button onClick={handleCreatePlugin}>
            <Plus className="w-4 h-4 mr-2" />
            Add Plugin
          </Button>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={6}
            filterCount={2}
            cellWidths={["20rem", "15rem", "8rem", "12rem", "10rem", "6rem"]}
            shrinkZero
          />
        }
      >
        <PluginTable pluginResponse={pluginResponse} />
      </React.Suspense>
    </div>
  );
}

export function PluginTable({
  pluginResponse,
}: {
  pluginResponse: Promise<{
    data: Plugin[];
    total: number;
    page: number;
    pageSize: number;
    totalPages: number;
  }>;
}) {
  const pluginResponseData = use(pluginResponse);
  const { table } = useDataTable({
    columns: [
      {
        id: "name",
        accessorKey: "name",
        header: "Plugin",
        cell: ({ row }) => {
          const plugin = row.original;
          return (
            <div className="space-y-1">
              <div className="font-medium">{plugin.name}</div>
              <p className="text-sm text-muted-foreground">
                {plugin.description}
              </p>
            </div>
          );
        },
        size: 400,
      },
      {
        id: "version",
        accessorKey: "version",
        header: "Version",
      },
      {
        id: "author",
        accessorKey: "author",
        header: "Author",
      },
      {
        id: "category",
        accessorKey: "category",
        header: "Category",
        cell: ({ row }) => {
          const category = row.getValue("category") as string;
          return (
            <Badge variant="outline" className="text-xs">
              {category}
            </Badge>
          );
        },
      },
      {
        id: "status",
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => {
          const status = row.getValue("status") as string;
          const getStatusColor = (status: string) => {
            switch (status) {
              case "active":
                return "bg-green-100 text-green-800 hover:bg-green-100";
              case "inactive":
                return "bg-gray-100 text-gray-800 hover:bg-gray-100";
              case "error":
                return "bg-red-100 text-red-800 hover:bg-red-100";
              default:
                return "bg-gray-100 text-gray-800 hover:bg-gray-100";
            }
          };

          return (
            <Badge className={getStatusColor(status)}>
              {status.charAt(0).toUpperCase() + status.slice(1)}
            </Badge>
          );
        },
      },
      {
        id: "createdAt",
        accessorKey: "createdAt",
        header: "Created",
        cell: ({ cell }) => formatDate(cell.getValue<Date>()),
      },
    ],
    data: pluginResponseData.data,
    pageCount: pluginResponseData.totalPages,
    initialState: {
      pagination: {
        pageIndex: pluginResponseData.page - 1,
        pageSize: pluginResponseData.pageSize,
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
