import type { ActionFunctionArgs } from "react-router";
import { redirect } from "react-router";
import { createAuthFetch } from "@/api.server";
import { resetUserPassword } from "@/api/user-api";
import { createPasswordChallenge } from "@/lib/security-challenge";

export async function action({ request, context, params }: ActionFunctionArgs) {
  const userId = Number.parseInt(params.userId as string, 10);
  if (Number.isNaN(userId)) {
    throw new Response("Invalid user ID", { status: 400 });
  }

  const formData = await request.formData();
  const newPassword = formData.get("newPassword") as string;
  const forceChangeOnNextLogin = formData.get("forceChangeOnNextLogin") === "true";
  const verificationPassword = (formData.get("verificationPassword") as string | null) ?? "";

  if (!newPassword?.trim()) {
    return {
      errors: { general: "New password is required" },
      success: false,
    };
  }
  if (newPassword.length < 6) {
    return {
      errors: { general: "New password must be at least 6 characters" },
      success: false,
    };
  }

  try {
    const authFetch = createAuthFetch(request, context);
    const challengeToken = await createPasswordChallenge({
      request,
      password: verificationPassword,
      action: "user.reset-password",
      targetType: "user",
      targetId: String(userId),
    });
    await resetUserPassword(
      userId,
      {
        newPassword,
        forceChangeOnNextLogin,
      },
      authFetch,
      { challengeToken },
    );
    return redirect("/admin/users");
  } catch (error: any) {
    return {
      errors: { general: error.message || "Failed to reset password" },
      success: false,
    };
  }
}
