import {Download, Plus} from "lucide-react";
import React, {use} from "react";
import type {LoaderFunctionArgs} from "react-router";
import {Link, useLoaderData} from "react-router";
import type {PaginatedResponse} from "@/api/api-client";
import {getProfiles, loadProfileSearchParams} from "@/api/profile-api";
import {DataTable} from "@/components/data-table/data-table";
import {DataTableSkeleton} from "@/components/data-table/data-table-skeleton";
import {DataTableToolbar} from "@/components/data-table/data-table-toolbar";
import {ProfileTableActionBar} from "@/components/profile/profile-action-bar";
import {profileColumns} from "@/components/profile/profile-columns";
import {Button} from "@/components/ui/button";
import {useDataTable} from "@/hooks/use-data-table";
import {createBreadcrumb} from "@/lib/breadcrumb-utils";
import type {Profile} from "@/types/profile";

export async function loader({ request }: LoaderFunctionArgs) {
  const { name, protocolType, page, perPage, sortBy, sortOrder } =
    loadProfileSearchParams(request);
  return {
    profileResponse: getProfiles({
      name,
      protocolType,
      page,
      perPage,
      sortBy,
      sortOrder,
    }),
  };
}

export const handle = createBreadcrumb(() => ({
  id: "profiles",
  label: "Profiles",
  to: "/profiles",
}));

export default function ProfileList() {
  const { profileResponse } = useLoaderData() as {
    profileResponse: Promise<PaginatedResponse<Profile>>;
  };

  return (
    <div className="container mx-auto p-6 max-w-6xl">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-balance">
            Profile Management
          </h1>
          <p className="text-muted-foreground mt-2">
            Manage configuration and transformation profiles
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
          <Link to="/profiles/create">
            <Button>
              <Plus className="w-4 h-4 mr-2" />
              Create Profile
            </Button>
          </Link>
        </div>
      </div>

      <React.Suspense
        fallback={
          <DataTableSkeleton
            columnCount={7}
            filterCount={3}
            cellWidths={[
              "10rem",
              "15rem",
              "12rem",
              "20rem",
              "10rem",
              "8rem",
              "6rem",
            ]}
            shrinkZero
          />
        }
      >
        <ProfileTable profileResponse={profileResponse} />
      </React.Suspense>
    </div>
  );
}

function ProfileTable({
  profileResponse,
}: {
  profileResponse: Promise<PaginatedResponse<Profile>>;
}) {
  const profileResponseData = use(profileResponse);
  const { table } = useDataTable({
    columns: profileColumns,
    data: profileResponseData.content,
    pageCount: profileResponseData.totalPages,
    initialState: {
      pagination: {
        pageIndex: profileResponseData.page - 1,
        pageSize: profileResponseData.pageSize,
      },
    },
    shallow: false,
    clearOnDefault: true,
  });

  return (
    <DataTable
      table={table}
      actionBar={<ProfileTableActionBar table={table} />}
    >
      <DataTableToolbar table={table} />
    </DataTable>
  );
}
