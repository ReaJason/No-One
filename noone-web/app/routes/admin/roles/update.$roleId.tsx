import type { ActionFunctionArgs } from "react-router";

import { redirect } from "react-router";

import { createAuthFetch } from "@/api.server";
import { updateRole } from "@/api/role-api";
import { parseRoleFormData } from "@/routes/admin/roles/role-form.shared";

export async function action({ request, context, params }: ActionFunctionArgs) {
  const roleId = parseInt(params.roleId as string, 10);
  if (Number.isNaN(roleId)) {
    throw new Response("Invalid role ID", { status: 400 });
  }

  const parsed = parseRoleFormData(await request.formData());
  if (parsed.errors) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    };
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await updateRole(roleId, parsed.payload, authFetch);
    return redirect("/admin/roles");
  } catch (error: any) {
    console.error("Error updating role:", error);
    return {
      errors: { general: error?.message || "Failed to update role" },
      success: false,
      values: parsed.values,
    };
  }
}
