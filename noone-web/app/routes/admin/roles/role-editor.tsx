import type { Route } from "./+types/role-editor";
import type { Permission, Role } from "@/types/admin";
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
import { getAllPermissions } from "@/api/permission-api";
import { createRole, getRoleById, syncRolePermissions, updateRole } from "@/api/role-api";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { RoleForm } from "@/components/role/role-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getRoleFormSeed,
  parseRoleFormData,
  type RoleActionData,
} from "@/routes/admin/roles/role-form.shared";

type LoaderData = {
  permissions: Permission[];
  role?: Role;
};

export async function loader({
  context,
  params,
  request,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const authFetch = createAuthFetch(request, context);
  const roleId = params.roleId;
  const permissionsPromise = getAllPermissions(authFetch);

  if (!roleId) {
    return { permissions: await permissionsPromise };
  }

  const [role, permissions] = await Promise.all([
    getRoleById(Number(roleId), authFetch),
    permissionsPromise,
  ]);
  if (!role) {
    throw new Response("Role not found", { status: 404 });
  }

  return { role, permissions };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const parsed = parseRoleFormData(await request.formData());
  if (parsed.errors) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    } satisfies RoleActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);
    if (params.roleId) {
      await updateRole(Number(params.roleId), parsed.payload, authFetch);
      await syncRolePermissions(Number(params.roleId), parsed.permissionIds, authFetch);
    } else {
      const created = await createRole(parsed.payload, authFetch);
      await syncRolePermissions(created.id, parsed.permissionIds, authFetch);
    }

    return redirect("/admin/roles");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to save role" },
      success: false,
      values: parsed.values,
    } satisfies RoleActionData;
  }
}

export const handle = createBreadcrumb(({ params }) => {
  if (params.roleId) {
    return {
      id: "roles-edit",
      label: "Edit Role",
      to: `/admin/roles/edit/${params.roleId}`,
    };
  }

  return {
    id: "roles-create",
    label: "Create Role",
    to: "/admin/roles/create",
  };
});

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  const params = useParams();
  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <NotFoundErrorBoundary
        title={"Role not found"}
        backLabel={"Back to Roles"}
        backHref={"/admin/roles"}
        resourceType={"Role"}
        resourceId={params.roleId}
      />
    );
  }
  throw error;
}

export default function RoleEditor() {
  const { permissions, role } = useLoaderData() as LoaderData;
  const actionData = useActionData() as RoleActionData | undefined;
  const navigate = useNavigate();
  const isEdit = Boolean(role);
  const initialValues = actionData?.values ?? getRoleFormSeed(role);

  return (
    <FormPageShell
      backHref="/admin/roles"
      backLabel="Return to role list"
      badges={[
        { label: isEdit ? "Edit mode" : "New role", variant: isEdit ? "secondary" : "default" },
      ]}
      title={isEdit ? "Edit Role" : "Create Role"}
      description={
        isEdit
          ? `Adjust role scope and permission mappings for ${role?.name}.`
          : "Create a reusable role by combining a stable name with an explicit permission set."
      }
    >
      <RoleForm
        key={`${isEdit ? "edit" : "create"}:${JSON.stringify(initialValues)}`}
        mode={isEdit ? "edit" : "create"}
        icon={isEdit ? Edit : Plus}
        submitLabel={isEdit ? "Update Role" : "Create Role"}
        permissions={permissions}
        initialValues={initialValues}
        errors={actionData?.errors}
        onCancel={() => navigate("/admin/roles")}
      />
    </FormPageShell>
  );
}
