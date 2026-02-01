import type { ActionFunctionArgs } from "react-router";
import { redirect } from "react-router";
import { toast } from "sonner";
import { updateRole } from "@/api/role-api";

export async function action({ request, params }: ActionFunctionArgs) {
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
  if (permissionIds.length === 0)
    errors.permissionIds = "Select at least one permission";
  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    await updateRole(roleId, { name, permissionIds });
    toast.success("Role updated successfully");
    return redirect("/admin/roles");
  } catch (error: any) {
    console.error("Error updating role:", error);
    toast.error(error?.message || "Failed to update role");
    return {
      errors: { general: error?.message || "Failed to update role" },
      success: false,
    };
  }
}
