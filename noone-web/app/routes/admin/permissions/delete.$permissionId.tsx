import type {ActionFunctionArgs} from "react-router";
import {redirect} from "react-router";
import {deletePermission} from "@/api/permission-api";

export async function action({ params }: ActionFunctionArgs) {
  const permissionId = parseInt(params.permissionId as string, 10);
  if (Number.isNaN(permissionId)) {
    throw new Response("Invalid permission ID", { status: 400 });
  }
  try {
    await deletePermission(permissionId);
    return redirect("/admin/permissions");
  } catch (error: any) {
    console.error("Error deleting permission:", error);
    return redirect("/admin/permissions");
  }
}
