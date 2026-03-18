import type { User } from "@/types/admin";
import type { ComponentProps } from "react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { Form, redirect, useActionData, useNavigation } from "react-router";

import { createPublicApi } from "@/api/api.server";
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

type LoginApiChallenge = {
  code?: string;
  message?: string;
  actionToken?: string | null;
};

type VerifyActionData = {
  error?: string;
};

function isLoginApiSuccess(
  response: LoginApiSuccess | LoginApiChallenge,
): response is LoginApiSuccess &
  Required<Pick<LoginApiSuccess, "token" | "refreshToken" | "user">> {
  return Boolean(
    "token" in response &&
    "refreshToken" in response &&
    "user" in response &&
    response.token &&
    response.refreshToken &&
    response.user,
  );
}

function normalizeReturnTo(returnTo: string | null): string {
  if (!returnTo || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
    return "/";
  }
  return returnTo;
}

function getFormString(formData: FormData, key: string): string | null {
  const value = formData.get(key);
  return typeof value === "string" ? value : null;
}

async function redirectToLoginAndClearPendingSession(request: Request) {
  const session = await getSession(request.headers.get("Cookie"));
  session.unset("pending2faActionToken");
  session.unset("pending2faReturnTo");
  return redirect("/auth/login", {
    headers: {
      "Set-Cookie": await commitSession(session),
    },
  });
}

export async function loader({ request }: LoaderFunctionArgs) {
  const session = await getSession(request.headers.get("Cookie"));

  if (session.get("accessToken")) {
    const returnTo = normalizeReturnTo(session.get("pending2faReturnTo") ?? "/");
    throw redirect(returnTo);
  }

  if (!session.get("pending2faActionToken")) {
    throw await redirectToLoginAndClearPendingSession(request);
  }

  return null;
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const twoFactorCode = (getFormString(formData, "twoFactorCode") ?? "").trim();
  const session = await getSession(request.headers.get("Cookie"));
  const actionToken = session.get("pending2faActionToken") ?? "";
  const returnTo = normalizeReturnTo(session.get("pending2faReturnTo") ?? "/");

  if (!actionToken) {
    return redirectToLoginAndClearPendingSession(request);
  }

  if (!twoFactorCode) {
    return {
      error: "Verification code is required",
    };
  }

  try {
    const response = await createPublicApi(request)<LoginApiSuccess | LoginApiChallenge>(
      "/auth/verify-2fa",
      {
        method: "POST",
        body: {
          actionToken,
          twoFactorCode,
        },
      },
    );

    if (!isLoginApiSuccess(response)) {
      return {
        error: "Verification failed. Please try again.",
      };
    }

    session.set("accessToken", response.token);
    session.set("refreshToken", response.refreshToken);
    session.set("user", response.user);
    session.unset("pending2faActionToken");
    session.unset("pending2faReturnTo");

    return redirect(returnTo, {
      headers: {
        "Set-Cookie": await commitSession(session),
      },
    });
  } catch (error: any) {
    const detailCode = error?.details?.code;
    const detailMessage =
      error?.details?.message ??
      error?.details?.error ??
      error?.message ??
      "Failed to verify two-factor code";

    if (detailCode === "REQUIRE_PASSWORD_CHANGE") {
      const passwordChangeToken = error?.details?.actionToken || "";
      session.unset("pending2faActionToken");
      session.unset("pending2faReturnTo");
      let changePasswordUrl = "/auth/password-change";
      if (passwordChangeToken) {
        changePasswordUrl += `?token=${encodeURIComponent(passwordChangeToken)}`;
      }
      return redirect(changePasswordUrl, {
        headers: {
          "Set-Cookie": await commitSession(session),
        },
      });
    }

    if (detailCode === "INVALID_2FA_CHALLENGE") {
      return redirectToLoginAndClearPendingSession(request);
    }

    return {
      error: detailMessage,
    };
  }
}

function VerifyTwoFactorForm({ className, ...props }: ComponentProps<"div">) {
  const actionData = useActionData() as VerifyActionData | undefined;
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";
  const statusId = "verify-2fa-status-message";

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <AuthShell
        eyebrow="Two-Factor Verification"
        title="No One"
        description="Enter the verification code from your authenticator app to finish signing in."
      >
        <Form method="post" className="w-full">
          <div className="grid gap-6">
            <AuthStatusMessage
              id={statusId}
              message={actionData?.error ? { tone: "error", text: actionData.error } : null}
              centered
            />

            <div className="grid gap-4">
              <div className="grid gap-2.5">
                <Label htmlFor="twoFactorCode" className={authLabelClassName}>
                  Verification Code
                </Label>
                <Input
                  id="twoFactorCode"
                  name="twoFactorCode"
                  type="text"
                  required
                  disabled={isSubmitting}
                  autoFocus
                  autoComplete="one-time-code"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  aria-invalid={actionData?.error ? true : undefined}
                  aria-describedby={actionData?.error ? statusId : undefined}
                  className={cn(authInputClassName, "tracking-[0.16em]")}
                />
              </div>

              <Button
                type="submit"
                className={cn(authPrimaryButtonClassName, "mt-1")}
                disabled={isSubmitting}
              >
                {isSubmitting ? "Verifying..." : "Verify code"}
              </Button>
            </div>
          </div>
        </Form>
      </AuthShell>
    </div>
  );
}

export default function VerifyTwoFactorPage() {
  return (
    <AuthPage>
      <VerifyTwoFactorForm />
    </AuthPage>
  );
}
