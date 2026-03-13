import type { ActionFunctionArgs } from "react-router";

import { redirect } from "react-router";

import { createAuthFetch } from "@/api.server";
import { deleteUser } from "@/api/user-api";

export async function action({ request, context, params }: ActionFunctionArgs) {
  const userId = parseInt(params.userId as string, 10);
  if (Number.isNaN(userId)) {
    throw new Response("Invalid user ID", { status: 400 });
  }
  try {
    const authFetch = createAuthFetch(request, context);
    await request.formData();
    await deleteUser(userId, authFetch);
    return redirect("/admin/users");
  } catch (error: any) {
    return {
      errors: { general: error.message || "Failed to delete user" },
      success: false,
    };
  }
}
