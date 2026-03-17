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

type SetupQrResponse = {
  qrCodeUri?: string;
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

  try {
    const response = await publicApi<SetupQrResponse>("/setup/2fa/qr", {
      method: "GET",
      headers: {
        "Setup-Token": token,
      },
    });
    const qrUri = response.qrCodeUri;
    if (!qrUri) {
      return redirect("/auth/login");
    }
    return { token, qrUri };
  } catch (error: any) {
    console.error("Failed to fetch setup QR code:", error);
    return redirect("/auth/login");
  }
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const setupToken = getFormString(formData, "token") ?? "";
  const newPassword = getFormString(formData, "newPassword") ?? "";
  const confirmPassword = getFormString(formData, "confirmPassword") ?? "";
  const twoFactorCode = getFormString(formData, "twoFactorCode") ?? "";

  if (!setupToken || !newPassword || !confirmPassword || !twoFactorCode) {
    return { error: "All fields are required" };
  }

  if (newPassword !== confirmPassword) {
    return { error: "Passwords do not match" };
  }

  try {
    const response = await publicApi<LoginApiSuccess>("/setup/activate", {
      method: "POST",
      headers: {
        "Setup-Token": setupToken,
      },
      body: {
        newPassword,
        twoFactorCode,
      },
    });

    if (!isLoginSuccessResponse(response)) {
      return { error: "Activation failed" };
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
    console.error("Setup activation error:", error);
    return {
      error:
        error?.details?.message ??
        error?.details?.error ??
        error?.message ??
        "Failed to activate account. Please verify your 2FA code.",
    };
  }
}

function SetupForm({ className, ...props }: ComponentProps<"div">) {
  const { token, qrUri } = useLoaderData() as { token: string; qrUri: string };
  const actionData = useActionData() as { error?: string } | undefined;
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";
  const statusId = "setup-status-message";

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <AuthShell
        eyebrow="Account Activation"
        title="No One"
        description="Secure your account by changing your password and setting up two-factor authentication."
      >
        <Form method="post" className="w-full">
          <input type="hidden" name="token" value={token} />

          <div className="grid gap-6">
            <AuthStatusMessage
              id={statusId}
              message={actionData?.error ? { tone: "error", text: actionData.error } : null}
              centered
            />

            <div className="flex flex-col items-center gap-4 rounded-xl border border-border bg-muted/40 p-4">
              <div className="text-center text-sm font-medium text-foreground">
                Scan this QR code with your authenticator app
              </div>
              {qrUri ? (
                <img
                  src={qrUri}
                  alt="2FA QR code"
                  className="size-40 rounded-xl border border-border bg-background p-2 shadow-sm"
                />
              ) : (
                <div className="flex size-40 items-center justify-center rounded-xl border border-border bg-background text-sm text-muted-foreground shadow-sm">
                  Unable to load QR
                </div>
              )}
            </div>

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

              <div className="grid gap-2.5">
                <Label htmlFor="twoFactorCode" className={authLabelClassName}>
                  6-digit Authenticator Code
                </Label>
                <Input
                  id="twoFactorCode"
                  name="twoFactorCode"
                  type="text"
                  required
                  maxLength={6}
                  placeholder="123456"
                  disabled={isSubmitting}
                  autoComplete="one-time-code"
                  aria-invalid={actionData?.error ? true : undefined}
                  aria-describedby={actionData?.error ? statusId : undefined}
                  className={cn(authInputClassName, "text-center tracking-[0.16em]")}
                />
              </div>

              <Button
                type="submit"
                className={cn(authPrimaryButtonClassName, "mt-1")}
                disabled={isSubmitting}
              >
                {isSubmitting ? "Activating..." : "Complete Setup"}
              </Button>
            </div>
          </div>
        </Form>
      </AuthShell>
    </div>
  );
}

export default function SetupPage() {
  return (
    <AuthPage>
      <SetupForm />
    </AuthPage>
  );
}
