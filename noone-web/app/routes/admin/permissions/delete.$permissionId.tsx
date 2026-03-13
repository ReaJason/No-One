import type { ActionFunctionArgs } from "react-router";

import { redirect } from "react-router";

import { createAuthFetch } from "@/api.server";
import { deletePermission } from "@/api/permission-api";

export async function action({ request, context, params }: ActionFunctionArgs) {
  const permissionId = parseInt(params.permissionId as string, 10);
  if (Number.isNaN(permissionId)) {
    throw new Response("Invalid permission ID", { status: 400 });
  }
  try {
    const authFetch = createAuthFetch(request, context);
    await deletePermission(permissionId, authFetch);
    return redirect("/admin/permissions");
  } catch (error: any) {
    console.error("Error deleting permission:", error);
    return redirect("/admin/permissions");
  }
}
