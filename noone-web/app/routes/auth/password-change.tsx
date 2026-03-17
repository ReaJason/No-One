import type { User } from "@/types/admin";
import type { ComponentProps } from "react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { Form, redirect, useActionData, useLoaderData, useNavigation } from "react-router";

import { publicApi } from "@/api/api.server";
import { commitSession, getSession } from "@/api/sessions.server";
import {
  authInputClassName,
  authLabelClassName,
  AuthPage,
  authPrimaryButtonClassName,
  AuthShell,
  AuthStatusMessage,
} from "@/components/auth/auth-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

type LoginApiSuccess = {
  token?: string;
  refreshToken?: string;
  user?: User;
};

function isLoginSuccessResponse(
  response: LoginApiSuccess,
): response is Required<Pick<LoginApiSuccess, "token" | "refreshToken" | "user">> &
  LoginApiSuccess {
  return Boolean(response.token && response.refreshToken && response.user);
}

function getFormString(formData: FormData, key: string): string | null {
  const value = formData.get(key);
  return typeof value === "string" ? value : null;
}

export async function loader({ request }: LoaderFunctionArgs) {
  const url = new URL(request.url);
  const token = url.searchParams.get("token");
  if (!token) {
    return redirect("/auth/login");
  }
  return { token };
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const passwordChangeToken = getFormString(formData, "token") ?? "";
  const newPassword = getFormString(formData, "newPassword") ?? "";
  const confirmPassword = getFormString(formData, "confirmPassword") ?? "";

  if (!passwordChangeToken || !newPassword || !confirmPassword) {
    return { error: "All fields are required" };
  }
  if (newPassword.length < 6) {
    return { error: "Password must be at least 6 characters" };
  }
  if (newPassword !== confirmPassword) {
    return { error: "Passwords do not match" };
  }

  try {
    const response = await publicApi<LoginApiSuccess>("/auth/password/change-required", {
      method: "POST",
      headers: {
        "Password-Change-Token": passwordChangeToken,
      },
      body: {
        newPassword,
      },
    });

    if (!isLoginSuccessResponse(response)) {
      return { error: "Password change failed" };
    }

    const session = await getSession(request.headers.get("Cookie"));
    session.set("accessToken", response.token);
    session.set("refreshToken", response.refreshToken);
    session.set("user", response.user);

    return redirect("/", {
      headers: {
        "Set-Cookie": await commitSession(session),
      },
    });
  } catch (error: any) {
    return {
      error:
        error?.details?.message ??
        error?.details?.error ??
        error?.message ??
        "Failed to change password",
    };
  }
}

function PasswordChangeForm({ className, ...props }: ComponentProps<"div">) {
  const { token } = useLoaderData() as { token: string };
  const actionData = useActionData() as { error?: string } | undefined;
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";
  const statusId = "password-change-status-message";

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <AuthShell
        eyebrow="Security Update"
        title="No One"
        description="Your administrator issued a temporary password. Set a new password before entering the system."
      >
        <Form method="post" className="w-full">
          <input type="hidden" name="token" value={token} />

          <div className="grid gap-6">
            <AuthStatusMessage
              id={statusId}
              message={actionData?.error ? { tone: "error", text: actionData.error } : null}
              centered
            />

            <div className="grid gap-4">
              <div className="grid gap-2.5">
                <Label htmlFor="newPassword" className={authLabelClassName}>
                  New Password
                </Label>
                <Input
                  id="newPassword"
                  name="newPassword"
                  type="password"
                  required
                  disabled={isSubmitting}
                  aria-invalid={actionData?.error ? true : undefined}
                  aria-describedby={actionData?.error ? statusId : undefined}
                  className={authInputClassName}
                />
              </div>

              <div className="grid gap-2.5">
                <Label htmlFor="confirmPassword" className={authLabelClassName}>
                  Confirm Password
                </Label>
                <Input
                  id="confirmPassword"
                  name="confirmPassword"
                  type="password"
                  required
                  disabled={isSubmitting}
                  aria-invalid={actionData?.error ? true : undefined}
                  aria-describedby={actionData?.error ? statusId : undefined}
                  className={authInputClassName}
                />
              </div>

              <Button
                type="submit"
                className={cn(authPrimaryButtonClassName, "mt-1")}
                disabled={isSubmitting}
              >
                {isSubmitting ? "Updating..." : "Update Password"}
              </Button>
            </div>
          </div>
        </Form>
      </AuthShell>
    </div>
  );
}

export default function PasswordChangePage() {
  return (
    <AuthPage>
      <PasswordChangeForm />
    </AuthPage>
  );
}
