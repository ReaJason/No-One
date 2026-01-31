import type {ActionFunctionArgs} from "react-router";
import {redirect} from "react-router";
import {updateUser} from "@/api/user-api";

export async function action({ request, params }: ActionFunctionArgs) {
  const userId = parseInt(params.userId as string, 10);
  if (Number.isNaN(userId)) {
    throw new Response("Invalid user ID", { status: 400 });
  }
  const formData = await request.formData();
  const roleIds = formData.getAll("roleIds") as string[];
  const roleIdsArray = roleIds?.length > 0 ? roleIds : undefined;
  const enabled = formData.get("enabled") as boolean | null;
  try {
    await updateUser(userId, { roleIds: roleIdsArray, enabled });
    console.log("updateUser success");
    return redirect("/admin/users");
  } catch (error: any) {
    console.error("Error updating user roles:", error);
    return {
      errors: { general: error.message || "Failed to update user" },
      success: false,
    };
  }
}
