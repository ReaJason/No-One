import type { ActionFunctionArgs } from "react-router";
import { redirect } from "react-router";
import { createAuthFetch } from "@/api.server";
import { updateUser } from "@/api/user-api";
import { createPasswordChallenge } from "@/lib/security-challenge";
import type { UserStatus } from "@/types/admin";

export async function action({ request, context, params }: ActionFunctionArgs) {
  const userId = parseInt(params.userId as string, 10);
  if (Number.isNaN(userId)) {
    throw new Response("Invalid user ID", { status: 400 });
  }
  const formData = await request.formData();
  const roleIds = (formData.getAll("roleIds") as string[]).map((id) => Number.parseInt(id, 10));
  const roleIdsArray = roleIds.length > 0 ? roleIds : undefined;
  const status = formData.get("status") as UserStatus | null;
  const email = formData.get("email") as string | null;
  const verificationPassword = (formData.get("verificationPassword") as string | null) ?? "";

  const payload: { roleIds?: number[]; status?: UserStatus; email?: string } = {};
  if (roleIdsArray && !roleIdsArray.some(Number.isNaN)) {
    payload.roleIds = roleIdsArray;
  }
  if (status) {
    payload.status = status;
  }
  if (email?.trim()) {
    payload.email = email.trim();
  }

  try {
    const authFetch = createAuthFetch(request, context);
    const needsChallenge = payload.status != null || payload.roleIds != null;
    const challengeToken = needsChallenge
      ? await createPasswordChallenge({
          request,
          password: verificationPassword,
          action: "user.update-security",
          targetType: "user",
          targetId: String(userId),
        })
      : undefined;
    await updateUser(userId, payload, authFetch, { challengeToken });
    return redirect("/admin/users");
  } catch (error: any) {
    return {
      errors: { general: error.message || "Failed to update user" },
      success: false,
    };
  }
}
