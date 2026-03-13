import type { PaginatedResponse } from "@/types/api";
import type { Plugin } from "@/types/plugin";
import type { LoaderFunctionArgs } from "react-router";

import { Download, Plus } from "lucide-react";
import { parseAsString, useQueryState } from "nuqs";
import * as React from "react";
import { use } from "react";
import { Link, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api.server";
import { getPlugins, loadPluginSearchParams } from "@/api/plugin-api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useDataTable } from "@/hooks/use-data-table";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import { formatDate } from "@/lib/format";

const LANGUAGE_TABS = [
  { value: "all", label: "All" },
  { value: "java", label: "Java" },
  { value: "nodejs", label: "Node.js" },
  { value: "dotnet", label: ".NET" },
] as const;

export async function loader({ request, context }: LoaderFunctionArgs) {
  const { name, language, type, page, perPage, sortBy, sortOrder } =
    loadPluginSearchParams(request);
  const authFetch = createAuthFetch(request, context);
  const pluginResponse = await getPlugins(
    {
      name,
      language,
      type,
      page,
      perPage,
      sortBy,
      sortOrder,
    },
    authFetch,
  );

  return {
    pluginResponse: Promise.resolve(pluginResponse),
  };
}

export const handle = createBreadcrumb(() => ({
  id: "plugins",
  label: "Plugins",
  to: "/plugins",
}));

export default function Plugins() {
  const { pluginResponse } = useLoaderData() as {
    pluginResponse: Promise<PaginatedResponse<Plugin>>;
  };
  const [language, setLanguage] = useQueryState(
    "language",
    parseAsString.withOptions({ shallow: false, clearOnDefault: true }),
  );

  return (
    <div className="container mx-auto max-w-6xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Plugin Management</h1>
          <p className="mt-1 text-muted-foreground">Manage system plugins and extensions</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Link to="/plugins/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              Add Plugin
            </Button>
          </Link>
        </div>
      </div>

      <Tabs
        value={language ?? "all"}
        onValueChange={(v: string | number | null) => {
          const val = String(v);
          void setLanguage(val === "all" ? null : val);
        }}
      >
        <TabsList className="mb-4">
          {LANGUAGE_TABS.map((tab) => (
            <TabsTrigger key={tab.value} value={tab.value}>
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={4}
            filterCount={2}
            cellWidths={["20rem", "8rem", "8rem", "10rem"]}
            shrinkZero
          />
        }
      >
        <PluginTable pluginResponse={pluginResponse} />
      </React.Suspense>
    </div>
  );
}

function getTypeColor(type: string) {
  switch (type.toLowerCase()) {
    case "reconnaissance":
      return "bg-blue-100 text-blue-800 hover:bg-blue-100";
    case "exploitation":
      return "bg-red-100 text-red-800 hover:bg-red-100";
    case "post-exploitation":
      return "bg-orange-100 text-orange-800 hover:bg-orange-100";
    case "utility":
      return "bg-green-100 text-green-800 hover:bg-green-100";
    default:
      return "bg-gray-100 text-gray-800 hover:bg-gray-100";
  }
}

export function PluginTable({
  pluginResponse,
}: {
  pluginResponse: Promise<PaginatedResponse<Plugin>>;
}) {
  const pluginResponseData = use(pluginResponse);
  const { table } = useDataTable({
    columns: [
      {
        id: "name",
        accessorKey: "name",
        header: "Name",
        meta: {
          label: "Name",
          variant: "text",
          placeholder: "Search by name...",
        },
        enableColumnFilter: true,
        cell: ({ row }) => {
          const plugin = row.original;
          return (
            <div className="space-y-1">
              <div className="font-medium">{plugin.name}</div>
              <p className="text-sm text-muted-foreground">{plugin.id}</p>
            </div>
          );
        },
      },
      {
        id: "version",
        accessorKey: "version",
        header: "Version",
      },
      {
        id: "type",
        accessorKey: "type",
        header: "Type",
        meta: {
          label: "Type",
          variant: "text",
          placeholder: "Search by type...",
        },
        enableColumnFilter: true,
        cell: ({ row }) => {
          const type = row.getValue("type") as string;
          return <Badge className={getTypeColor(type)}>{type}</Badge>;
        },
      },
      {
        id: "createdAt",
        accessorKey: "createdAt",
        header: "Created",
        cell: ({ cell }) => formatDate(cell.getValue<Date>()),
      },
    ],
    data: pluginResponseData.content,
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
