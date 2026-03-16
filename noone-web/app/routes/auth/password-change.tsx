import type { User } from "@/types/admin";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { GalleryVerticalEnd, KeyRound } from "lucide-react";
import React from "react";
import { Form, redirect, useActionData, useLoaderData, useNavigation } from "react-router";

import { publicApi } from "@/api/api.server";
import { commitSession, getSession } from "@/api/sessions.server";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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

function PasswordChangeForm({ className, ...props }: React.ComponentProps<"div">) {
  const { token } = useLoaderData() as { token: string };
  const actionData = useActionData() as { error?: string } | undefined;
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader className="text-center">
          <CardTitle className="text-xl">Update Temporary Password</CardTitle>
          <CardDescription>
            Your administrator issued a temporary password. Set a new password before entering the
            system.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form method="post">
            <input type="hidden" name="token" value={token} />
            <div className="grid gap-6">
              {actionData?.error ? (
                <div className="text-center text-sm font-medium text-destructive">
                  {actionData.error}
                </div>
              ) : null}
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
                <Button type="submit" className="mt-2 w-full" disabled={isSubmitting}>
                  <KeyRound className="mr-2 h-4 w-4" />
                  {isSubmitting ? "Updating..." : "Update Password"}
                </Button>
              </div>
            </div>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function PasswordChangePage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-muted p-6 md:p-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-center gap-2 self-center font-medium">
          <div className="flex size-6 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <GalleryVerticalEnd className="size-4" />
          </div>
          No One Security
        </div>
        <PasswordChangeForm />
      </div>
    </div>
  );
}
