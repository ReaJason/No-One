import type { ActionFunctionArgs } from "react-router";
import { redirect } from "react-router";
import { createAuthFetch } from "@/api.server";
import { updateRole } from "@/api/role-api";

export async function action({ request, context, params }: ActionFunctionArgs) {
  const roleId = parseInt(params.roleId as string, 10);
  if (Number.isNaN(roleId)) {
    throw new Response("Invalid role ID", { status: 400 });
  }

  const formData = await request.formData();
  const name = (formData.get("name") as string)?.trim();
  const permissionIds = (formData.getAll("permissionIds") as string[])
    .map((id) => Number(id))
    .filter((n) => Number.isFinite(n));

  const errors: Record<string, string> = {};
  if (!name) errors.name = "Role name is required";
  if (permissionIds.length === 0) errors.permissionIds = "Select at least one permission";
  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await updateRole(roleId, { name, permissionIds }, authFetch);
    return redirect("/admin/roles");
  } catch (error: any) {
    console.error("Error updating role:", error);
    return {
      errors: { general: error?.message || "Failed to update role" },
      success: false,
    };
  }
}
