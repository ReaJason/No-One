import type { User } from "@/types/admin";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { GalleryVerticalEnd, KeyRound } from "lucide-react";
import React from "react";
import { Form, redirect, useActionData, useLoaderData, useNavigation } from "react-router";

import { publicApi } from "@/api.server";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { commitSession, getSession } from "@/sessions.server";

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

function SetupForm({ className, ...props }: React.ComponentProps<"div">) {
  const { token, qrUri } = useLoaderData() as { token: string; qrUri: string };
  const actionData = useActionData() as { error?: string } | undefined;
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader className="text-center">
          <CardTitle className="text-xl">Account Activation</CardTitle>
          <CardDescription>
            Secure your account by changing your password and setting up two-factor authentication.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form method="post">
            <input type="hidden" name="token" value={token} />
            <div className="grid gap-6">
              {actionData?.error && (
                <div className="text-center text-sm font-medium text-destructive">
                  {actionData.error}
                </div>
              )}

              <div className="flex flex-col items-center gap-4 rounded-lg border bg-muted/50 p-4">
                <div className="flex items-center gap-2 text-sm font-medium">
                  <KeyRound className="size-4" />
                  <span>Scan this QR code with your Authenticator App</span>
                </div>
                {qrUri ? (
                  <img
                    src={qrUri}
                    alt="2FA QR Code"
                    className="size-48 rounded-md bg-white p-2 shadow-sm"
                  />
                ) : (
                  <div className="flex size-48 items-center justify-center rounded-md bg-background text-muted-foreground shadow-sm">
                    Unable to load QR
                  </div>
                )}
              </div>

              <div className="grid gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="newPassword">New Password</Label>
                  <Input
                    id="newPassword"
                    name="newPassword"
                    type="password"
                    required
                    disabled={isSubmitting}
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="confirmPassword">Confirm Password</Label>
                  <Input
                    id="confirmPassword"
                    name="confirmPassword"
                    type="password"
                    required
                    disabled={isSubmitting}
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="twoFactorCode">6-digit Authenticator Code</Label>
                  <Input
                    id="twoFactorCode"
                    name="twoFactorCode"
                    type="text"
                    required
                    maxLength={6}
                    placeholder="123456"
                    disabled={isSubmitting}
                    className="text-center text-lg tracking-widest"
                  />
                </div>
                <Button type="submit" className="mt-2 w-full" disabled={isSubmitting}>
                  {isSubmitting ? "Activating..." : "Complete Setup"}
                </Button>
              </div>
            </div>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function SetupPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-muted p-6 md:p-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-center gap-2 self-center font-medium">
          <div className="flex size-6 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <GalleryVerticalEnd className="size-4" />
          </div>
          No One Setup
        </div>
        <SetupForm />
      </div>
    </div>
  );
}
