import type { Route } from "./+types/permission-editor";
import type { Permission } from "@/types/admin";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { Edit, Plus } from "lucide-react";
import {
  isRouteErrorResponse,
  redirect,
  useActionData,
  useLoaderData,
  useNavigate,
  useParams,
} from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { createPermission, getPermissionById, updatePermission } from "@/api/permission-api";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { PermissionForm } from "@/components/permission/permission-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getPermissionFormSeed,
  parsePermissionFormData,
  type PermissionActionData,
} from "@/routes/admin/permissions/permission-form.shared";

type LoaderData = {
  permission?: Permission;
};

export async function loader({
  context,
  params,
  request,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const permissionId = params.permissionId;
  if (!permissionId) {
    return {};
  }

  const authFetch = createAuthFetch(request, context);
  const permission = await getPermissionById(Number(permissionId), authFetch);
  if (!permission) {
    throw new Response("Permission not found", { status: 404 });
  }

  return { permission };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const permissionId = params.permissionId;
  const parsed = parsePermissionFormData(await request.formData());
  if (parsed.errors) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    } satisfies PermissionActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);
    if (permissionId) {
      await updatePermission(Number(permissionId), parsed.payload, authFetch);
    } else {
      await createPermission(parsed.payload, authFetch);
    }

    return redirect("/admin/permissions");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to save permission" },
      success: false,
      values: parsed.values,
    } satisfies PermissionActionData;
  }
}

export const handle = createBreadcrumb(({ params }) => {
  if (params.permissionId) {
    return {
      id: "permissions-edit",
      label: "Edit Permission",
      to: `/admin/permissions/edit/${params.permissionId}`,
    };
  }

  return {
    id: "permissions-create",
    label: "Create Permission",
    to: "/admin/permissions/create",
  };
});

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  const params = useParams();
  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <NotFoundErrorBoundary
        title={"Permission not found"}
        backLabel={"Back to Permissions"}
        backHref={"/admin/permissions"}
        resourceType={"Permission"}
        resourceId={params.permissionId}
      />
    );
  }
  throw error;
}

export default function PermissionEditor() {
  const { permission } = useLoaderData() as LoaderData;
  const actionData = useActionData() as PermissionActionData | undefined;
  const navigate = useNavigate();
  const isEdit = Boolean(permission);
  const initialValues = actionData?.values ?? getPermissionFormSeed(permission);

  return (
    <FormPageShell
      backHref="/admin/permissions"
      backLabel="Return to permission list"
      badges={[
        {
          label: isEdit ? "Edit mode" : "New permission",
          variant: isEdit ? "secondary" : "default",
        },
      ]}
      title={isEdit ? "Edit Permission" : "Create Permission"}
      description={
        isEdit
          ? `Update the permission definition for ${permission?.name}.`
          : "Add a new permission to the system and expose it to role assignment."
      }
    >
      <PermissionForm
        key={`${isEdit ? "edit" : "create"}:${JSON.stringify(initialValues)}`}
        mode={isEdit ? "edit" : "create"}
        icon={isEdit ? Edit : Plus}
        submitLabel={isEdit ? "Update Permission" : "Create Permission"}
        initialValues={initialValues}
        errors={actionData?.errors}
        onCancel={() => navigate("/admin/permissions")}
      />
    </FormPageShell>
  );
}
