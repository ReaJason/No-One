import type { GeneratorContext } from "./generator/generator-context";
import type { LoaderFunctionArgs } from "react-router";

import { Outlet, useLoaderData, useLocation, useNavigate } from "react-router";

import { createAuthFetch } from "@/api.server";
import { getAllProfiles } from "@/api/profile-api";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";

export const handle = createBreadcrumb(() => ({
  id: "generator",
  label: "Generator",
  to: "/generator",
}));

export async function loader({ request, context }: LoaderFunctionArgs) {
  const authFetch = createAuthFetch(request, context);
  const profiles = await getAllProfiles(authFetch);
  return { profiles };
}

export default function GeneratorLayout() {
  const { profiles } = useLoaderData<typeof loader>();
  const location = useLocation();
  const navigate = useNavigate();

  const currentTab = location.pathname.includes("/webshell") ? "webshell" : "memshell";

  const handleTabChange = (value: string) => {
    navigate(value === "webshell" ? "/generator/webshell" : "/generator");
  };

  return (
    <div className="@container/page flex flex-1 flex-col gap-6 p-6">
      <Tabs value={currentTab} onValueChange={handleTabChange}>
        <TabsList>
          <TabsTrigger value="memshell">MemShell</TabsTrigger>
          <TabsTrigger value="webshell">WebShell</TabsTrigger>
        </TabsList>
      </Tabs>
      <Outlet context={{ profiles } satisfies GeneratorContext} />
    </div>
  );
}
