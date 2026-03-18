import type { PaginatedResponse } from "@/types/api";
import type { CatalogResponse, Plugin } from "@/types/plugin";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { Download, Plus } from "lucide-react";
import { parseAsString, useQueryState } from "nuqs";
import * as React from "react";
import { use } from "react";
import { Link, useLoaderData } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import {
  deletePlugin,
  getPlugins,
  getRegistryCatalog,
  installFromRegistry,
  loadPluginSearchParams,
} from "@/api/plugin-api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { PluginTableActionBar } from "@/components/plugin/plugin-action-bar";
import { pluginColumns } from "@/components/plugin/plugin-columns";
import { PluginStore } from "@/components/plugin/plugin-store";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useDataTable } from "@/hooks/use-data-table";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";

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
  const pluginResponse = getPlugins(
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

  let catalogResponse: Promise<CatalogResponse>;
  try {
    catalogResponse = getRegistryCatalog(authFetch);
  } catch {
    catalogResponse = Promise.resolve({ enabled: false, plugins: [] });
  }

  return {
    pluginResponse,
    catalogResponse,
  };
}

export async function action({ request, context }: ActionFunctionArgs) {
  const formData = await request.formData();
  const intent = String(formData.get("intent") ?? "");
  const authFetch = createAuthFetch(request, context);

  if (intent === "delete") {
    const pluginDbId = String(formData.get("pluginDbId") ?? "").trim();
    if (!pluginDbId) return { errors: { general: "Invalid plugin ID" } };
    try {
      await deletePlugin(pluginDbId, authFetch);
      return { success: true };
    } catch (error: any) {
      return { errors: { general: error?.message || "Failed to delete plugin" } };
    }
  }

  if (intent === "install") {
    const pluginId = String(formData.get("pluginId") ?? "").trim();
    const language = String(formData.get("language") ?? "").trim();
    if (!pluginId || !language) return { errors: { general: "Plugin ID and language required" } };
    try {
      await installFromRegistry(pluginId, language, authFetch);
      return { success: true };
    } catch (error: any) {
      return { errors: { general: error?.message || "Failed to install plugin" } };
    }
  }

  return { errors: { general: "Unsupported action" } };
}

export const handle = createBreadcrumb(() => ({
  id: "plugins",
  label: "Plugins",
  to: "/plugins",
}));

export default function Plugins() {
  const { pluginResponse, catalogResponse } = useLoaderData() as {
    pluginResponse: Promise<PaginatedResponse<Plugin>>;
    catalogResponse: Promise<CatalogResponse>;
  };
  const [language, setLanguage] = useQueryState(
    "language",
    parseAsString.withOptions({ shallow: false, clearOnDefault: true }),
  );
  const [activeTab, setActiveTab] = React.useState("installed");

  return (
    <div className="container mx-auto max-w-7xl p-6">
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

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="mb-4">
          <TabsTrigger value="installed">Installed</TabsTrigger>
          <TabsTrigger value="store">Store</TabsTrigger>
        </TabsList>

        <TabsContent value="installed">
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
                columnCount={8}
                filterCount={2}
                cellWidths={[
                  "3rem",
                  "12rem",
                  "6rem",
                  "6rem",
                  "6rem",
                  "6rem",
                  "10rem",
                  "3rem",
                ]}
                shrinkZero
              />
            }
          >
            <InstalledPluginTable pluginResponse={pluginResponse} />
          </React.Suspense>
        </TabsContent>

        <TabsContent value="store">
          <React.Suspense
            fallback={
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div
                    key={i}
                    className="h-48 animate-pulse rounded-lg border bg-muted/50"
                  />
                ))}
              </div>
            }
          >
            <StoreContent catalogResponse={catalogResponse} />
          </React.Suspense>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function InstalledPluginTable({
  pluginResponse,
}: {
  pluginResponse: Promise<PaginatedResponse<Plugin>>;
}) {
  const pluginResponseData = use(pluginResponse);
  const { table } = useDataTable({
    columns: pluginColumns,
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
    <DataTable table={table} actionBar={<PluginTableActionBar table={table} />}>
      <DataTableToolbar table={table} />
    </DataTable>
  );
}

function StoreContent({
  catalogResponse,
}: {
  catalogResponse: Promise<CatalogResponse>;
}) {
  const catalog = use(catalogResponse);
  return (
    <PluginStore
      catalog={catalog.plugins}
      error={catalog.error}
      enabled={catalog.enabled}
    />
  );
}
