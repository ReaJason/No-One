import { useState } from "react";
import { type LoaderFunctionArgs, useLoaderData, useNavigate } from "react-router";

import { createAuthFetch } from "@/api.server";
import * as shellApi from "@/api/shell-api";
import * as opLogApi from "@/api/shell-operation-log-api";
import SystemDashboard from "@/components/shell/system-info";

export async function loader({ context, params, request }: LoaderFunctionArgs) {
  const shellId = params.shellId as string;
  const url = new URL(request.url);
  const forceRefresh = url.searchParams.has("forceRefresh");
  const authFetch = createAuthFetch(request, context);

  try {
    if (!forceRefresh) {
      try {
        const cached = await opLogApi.getLatestShellOperation(
          Number(shellId),
          "system-info",
          authFetch,
        );
        if (cached?.result) {
          return { systemInfo: cached.result, error: null };
        }
      } catch {
        // Fall through to fresh request
      }
    }

    const data = await shellApi.dispatchPlugin(
      {
        id: Number(shellId),
        pluginId: "system-info",
      },
      authFetch,
    );
    return { systemInfo: data, error: null };
  } catch (err: any) {
    return {
      systemInfo: null,
      error: `Failed to load system info: ${err.message || "Unknown error"}`,
    };
  }
}

export default function ShellInfoRoute() {
  const { systemInfo, error } = useLoaderData<typeof loader>();
  const navigate = useNavigate();
  const [refreshing, setRefreshing] = useState(false);

  const handleRefresh = () => {
    setRefreshing(true);
    navigate(`?forceRefresh=${Date.now()}`, { replace: true });
    // refreshing state will reset on re-render from navigation
    setTimeout(() => setRefreshing(false), 500);
  };

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      {error && (
        <div className="shrink-0 rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
          {error}
        </div>
      )}
      <div className="min-h-0 flex-1 space-y-4 overflow-auto">
        <SystemDashboard
          data={(systemInfo as any)?.data}
          onRefresh={handleRefresh}
          refreshing={refreshing}
        />
      </div>
    </div>
  );
}
