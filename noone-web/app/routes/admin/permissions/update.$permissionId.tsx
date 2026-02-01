import type { ActionFunctionArgs } from "react-router";
import { redirect } from "react-router";
import { toast } from "sonner";
import { updatePermission } from "@/api/permission-api";

export async function action({ request, params }: ActionFunctionArgs) {
  const permissionId = parseInt(params.permissionId as string, 10);

  if (Number.isNaN(permissionId)) {
    throw new Response("Invalid permission ID", { status: 400 });
  }

  const formData = await request.formData();
  const name = formData.get("name") as string;
  const code = formData.get("code") as string;

  // Validation
  const errors: Record<string, string> = {};

  if (!name?.trim()) {
    errors.name = "Permission name is required";
  }
  if (!code?.trim()) {
    errors.code = "Permission code is required";
  } else if (!/^[a-zA-Z0-9:_-]+$/.test(code.trim())) {
    errors.code =
      "Permission code can only contain letters, numbers, colons, underscores, and hyphens";
  }

  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    const permissionData = {
      name: name.trim(),
      code: code.trim(),
    };

    await updatePermission(permissionId, permissionData);
    toast.success("Permission updated successfully");
    return redirect("/admin/permissions");
  } catch (error: any) {
    console.error("Error updating permission:", error);
    toast.error(error.message || "Failed to update permission");
    return {
      errors: { general: error.message || "Failed to update permission" },
      success: false,
    };
  }
}
