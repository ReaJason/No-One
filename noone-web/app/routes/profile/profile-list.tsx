import { Download, Plus } from "lucide-react";
import React, { use } from "react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import { Link, useLoaderData } from "react-router";
import { createAuthFetch } from "@/api.server";
import { deleteProfile, getProfiles, loadProfileSearchParams } from "@/api/profile-api";
import type { PaginatedResponse } from "@/types/api";
import { DataTable } from "@/components/data-table/data-table";
import { DataTableSkeleton } from "@/components/data-table/data-table-skeleton";
import { DataTableToolbar } from "@/components/data-table/data-table-toolbar";
import { ProfileTableActionBar } from "@/components/profile/profile-action-bar";
import { profileColumns } from "@/components/profile/profile-columns";
import { Button } from "@/components/ui/button";
import { useDataTable } from "@/hooks/use-data-table";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import type { Profile } from "@/types/profile";

export async function loader({ request, context }: LoaderFunctionArgs) {
  const { name, protocolType, page, perPage, sortBy, sortOrder } = loadProfileSearchParams(request);
  const authFetch = createAuthFetch(request, context);
  const profileResponse = await getProfiles(
    {
      name,
      protocolType,
      page,
      perPage,
      sortBy,
      sortOrder,
    },
    authFetch,
  );

  return {
    profileResponse: Promise.resolve(profileResponse),
  };
}

export async function action({ request, context }: ActionFunctionArgs) {
  const formData = await request.formData();
  if (formData.get("intent") !== "delete") {
    return { errors: { general: "Unsupported action" } };
  }
  const profileId = String(formData.get("profileId") ?? "").trim();
  if (!profileId) {
    return { errors: { general: "Invalid profile ID" } };
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await deleteProfile(profileId, authFetch);
    return { success: true };
  } catch (error: any) {
    return { errors: { general: error?.message || "Failed to delete profile" } };
  }
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
    <div className="container mx-auto max-w-6xl p-6">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-balance">Profile Management</h1>
          <p className="mt-2 text-muted-foreground">
            Manage configuration and transformation profiles
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Link to="/profiles/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
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
            cellWidths={["10rem", "15rem", "12rem", "20rem", "10rem", "8rem", "6rem"]}
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
    <DataTable table={table} actionBar={<ProfileTableActionBar table={table} />}>
      <DataTableToolbar table={table} />
    </DataTable>
  );
}
