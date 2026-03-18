import type { Route } from "./+types/user-editor";
import type { Role, User as UserType } from "@/types/admin";
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
import { getAllRoles } from "@/api/role-api";
import { createUser, getUserById, syncUserRoles, updateUser } from "@/api/user-api";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { UserForm } from "@/components/user/user-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getRoleSummary,
  getUserFormSeed,
  parseUserFormData,
  type UserActionData,
} from "@/routes/admin/users/user-form.shared";

type LoaderData = {
  roles: Pick<Role, "id" | "name">[];
  user?: UserType;
};

export async function loader({
  request,
  context,
  params,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const authFetch = createAuthFetch(request, context);
  const rolesPromise = getAllRoles(authFetch);

  if (!params.userId) {
    return { roles: getRoleSummary(await rolesPromise) };
  }

  const [user, roles] = await Promise.all([
    getUserById(Number(params.userId), authFetch),
    rolesPromise,
  ]);
  if (!user) {
    throw new Response("User not found", { status: 404 });
  }

  return {
    user,
    roles: getRoleSummary(roles),
  };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const userId = params.userId;
  const formData = await request.formData();
  const mode = userId ? "edit" : "create";
  const parsed = parseUserFormData(formData, { mode });
  if (parsed.errors) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    } satisfies UserActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);

    if (!userId) {
      await createUser(parsed.createPayload!, authFetch);
      return redirect("/admin/users");
    }

    const currentUser = await getUserById(Number(userId), authFetch);
    if (!currentUser) {
      throw new Response("User not found", { status: 404 });
    }

    await updateUser(Number(userId), parsed.updatePayload!, authFetch);
    await syncUserRoles(Number(userId), parsed.roleIds, authFetch);
    return redirect("/admin/users");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to save user" },
      success: false,
      values: parsed.values,
    } satisfies UserActionData;
  }
}

export const handle = createBreadcrumb(({ params }) => {
  if (params.userId) {
    return {
      id: "users-edit",
      label: "Edit User",
      to: `/admin/users/edit/${params.userId}`,
    };
  }

  return {
    id: "users-create",
    label: "Create User",
    to: "/admin/users/create",
  };
});

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  const params = useParams();
  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <NotFoundErrorBoundary
        title={"User not found"}
        backLabel={"Back to Users"}
        backHref={"/admin/users"}
        resourceType={"User"}
        resourceId={params.userId}
      />
    );
  }
  throw error;
}

export default function UserEditor() {
  const { roles, user } = useLoaderData() as LoaderData;
  const actionData = useActionData() as UserActionData | undefined;
  const navigate = useNavigate();
  const isEdit = Boolean(user);
  const initialValues = actionData?.values ?? getUserFormSeed(user);

  return (
    <FormPageShell
      backHref="/admin/users"
      backLabel="Return to user list"
      badges={[
        { label: isEdit ? "Edit mode" : "New user", variant: isEdit ? "secondary" : "default" },
        ...(isEdit && user ? [{ label: user.status, variant: "outline" as const }] : []),
      ]}
      title={isEdit ? "Edit User" : "Create User"}
      description={
        isEdit
          ? `Manage account details, status, and roles for ${user?.username}.`
          : "Provision a new administrator-managed account with roles and onboarding safeguards."
      }
    >
      <UserForm
        key={`${isEdit ? "edit" : "create"}:${JSON.stringify(initialValues)}`}
        mode={isEdit ? "edit" : "create"}
        icon={isEdit ? Edit : Plus}
        submitLabel={isEdit ? "Update User" : "Create User"}
        roles={roles}
        initialValues={initialValues}
        errors={actionData?.errors}
        onCancel={() => navigate("/admin/users")}
      />
    </FormPageShell>
  );
}
