import type { Route } from "./+types/edit";

import { PencilLine } from "lucide-react";
import {
  type ActionFunctionArgs,
  isRouteErrorResponse,
  type LoaderFunctionArgs,
  redirect,
  useLoaderData,
  useNavigate,
  useParams,
} from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getPlugin, updatePlugin } from "@/api/plugin-api";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { PluginEditForm } from "@/components/plugin/plugin-edit-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import type { Plugin } from "@/types/plugin";
import {
  type PluginEditActionData,
  pluginEditSchema,
} from "@/routes/plugins/plugin-edit-form.shared";

type LoaderData = {
  plugin: Plugin;
};

export async function loader({ context, params, request }: LoaderFunctionArgs): Promise<LoaderData> {
  const pluginId = params.pluginId;
  if (!pluginId) {
    throw new Response("Plugin ID is required", { status: 400 });
  }

  const authFetch = createAuthFetch(request, context);

  try {
    const plugin = await getPlugin(pluginId, authFetch);
    if (!plugin) {
      throw new Response("Plugin not found", { status: 404 });
    }
    return { plugin };
  } catch (error: any) {
    if (error instanceof Response) {
      throw error;
    }
    throw new Response("Plugin not found", { status: 404 });
  }
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const pluginId = params.pluginId;
  if (!pluginId) {
    throw new Response("Plugin ID is required", { status: 400 });
  }

  const formData = await request.formData();
  const raw = {
    name: formData.get("name") as string,
    type: formData.get("type") as string,
    description: formData.get("description") as string,
    author: formData.get("author") as string,
    runMode: formData.get("runMode") as string,
  };

  const result = pluginEditSchema.safeParse(raw);
  if (!result.success) {
    const errors: Record<string, string> = {};
    for (const issue of result.error.issues) {
      const key = issue.path[0];
      if (typeof key === "string") {
        errors[key] = issue.message;
      }
    }
    return { errors, success: false } satisfies PluginEditActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await updatePlugin(pluginId, result.data, authFetch);
    return redirect("/plugins");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to update plugin" },
      success: false,
    } satisfies PluginEditActionData;
  }
}

export const handle = createBreadcrumb(({ params }) => ({
  id: "plugins-edit",
  label: "Edit Plugin",
  to: `/plugins/edit/${params.pluginId}`,
}));

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  const params = useParams();
  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <NotFoundErrorBoundary
        title="Plugin not found"
        backLabel="Back to Plugins"
        backHref="/plugins"
        resourceType="Plugin"
        resourceId={params.pluginId}
      />
    );
  }
  throw error;
}

export default function PluginEdit() {
  const { plugin } = useLoaderData() as LoaderData;
  const navigate = useNavigate();

  return (
    <FormPageShell
      backHref="/plugins"
      backLabel="Return to plugin list"
      badges={[
        { label: "Edit mode", variant: "secondary" as const },
        ...(plugin.source ? [{ label: plugin.source, variant: "outline" as const }] : []),
      ]}
      title="Edit Plugin"
      description={`Update metadata for ${plugin.name}.`}
    >
      <PluginEditForm plugin={plugin} onCancel={() => navigate(-1)} />
    </FormPageShell>
  );
}
