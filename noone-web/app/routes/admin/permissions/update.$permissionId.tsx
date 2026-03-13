import type { ActionFunctionArgs } from "react-router";

import { redirect } from "react-router";

import { createAuthFetch } from "@/api.server";
import { updatePermission } from "@/api/permission-api";
import { parsePermissionFormData } from "@/routes/admin/permissions/permission-form.shared";

export async function action({ request, context, params }: ActionFunctionArgs) {
  const permissionId = parseInt(params.permissionId as string, 10);

  if (Number.isNaN(permissionId)) {
    throw new Response("Invalid permission ID", { status: 400 });
  }

  const parsed = parsePermissionFormData(await request.formData());
  if (parsed.errors) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    };
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await updatePermission(permissionId, parsed.payload, authFetch);
    return redirect("/admin/permissions");
  } catch (error: any) {
    console.error("Error updating permission:", error);
    return {
      errors: { general: error.message || "Failed to update permission" },
      success: false,
      values: parsed.values,
    };
  }
}
