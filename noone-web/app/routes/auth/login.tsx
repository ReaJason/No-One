import type { User } from "@/types/admin";
import type { ComponentProps } from "react";

import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";
import {
  type ActionFunctionArgs,
  data,
  Form,
  type LoaderFunctionArgs,
  redirect,
  useActionData,
  useLoaderData,
  useNavigation,
} from "react-router";

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

const REQUIRE_2FA_CODE = "REQUIRE_2FA";

type LoginApiSuccess = {
  token?: string;
  refreshToken?: string;
  user?: User;
};

type LoginApiChallenge = {
  code?: string;
  message?: string;
  mfaRequired?: boolean;
  setupToken?: string | null;
  actionToken?: string | null;
};

type LoginActionData = {
  error?: string;
  formData?: { username: string };
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

export async function loader({ request }: LoaderFunctionArgs) {
  const url = new URL(request.url);
  const returnTo = normalizeReturnTo(url.searchParams.get("returnTo"));
  const session = await getSession(request.headers.get("Cookie"));

  if (session.get("accessToken")) {
    throw redirect(returnTo);
  }

  const error = session.get("error") ?? null;

  return data({ error, returnTo });
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const username = (getFormString(formData, "username") ?? "").trim();
  const password = getFormString(formData, "password") ?? "";
  const returnTo = normalizeReturnTo(getFormString(formData, "returnTo"));

  if (!username || !password) {
    return {
      error: "Username and password are required",
    };
  }

  try {
    const response = await createPublicApi(request)<LoginApiSuccess | LoginApiChallenge>(
      "/auth/login",
      {
        method: "POST",
        body: {
          username,
          password,
        },
      },
    );

    if ("code" in response && response.code === REQUIRE_2FA_CODE) {
      if (!response.actionToken) {
        return {
          error: response.message ?? "Two-factor verification is required",
          formData: { username },
        };
      }

      const session = await getSession(request.headers.get("Cookie"));
      session.unset("accessToken");
      session.unset("refreshToken");
      session.unset("user");
      session.set("pending2faActionToken", response.actionToken);
      session.set("pending2faReturnTo", returnTo);
      return redirect("/auth/verify-2fa", {
        headers: {
          "Set-Cookie": await commitSession(session),
        },
      });
    }

    if (!isLoginApiSuccess(response)) {
      return {
        error: "Login failed. Please try again.",
        formData: { username },
      };
    }

    const session = await getSession(request.headers.get("Cookie"));
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
    const detailMessage =
      error?.details?.message ?? error?.details?.error ?? error?.message ?? "Login failed";
    const detailCode = error?.details?.code;

    if (detailCode === "REQUIRE_SETUP") {
      const setupToken = error?.details?.setupToken || "";
      let setupUrl = "/auth/setup";
      if (setupToken) {
        setupUrl += `?token=${encodeURIComponent(setupToken)}`;
      }

      return redirect(setupUrl);
    }

    if (detailCode === "REQUIRE_PASSWORD_CHANGE") {
      const actionToken = error?.details?.actionToken || "";
      let changePasswordUrl = "/auth/password-change";
      if (actionToken) {
        changePasswordUrl += `?token=${encodeURIComponent(actionToken)}`;
      }
      return redirect(changePasswordUrl);
    }

    return {
      error: detailMessage,
      formData: {
        username,
      },
    };
  }
}

function LoginForm({ className, ...props }: ComponentProps<"div">) {
  const { returnTo } = useLoaderData<typeof loader>();
  const actionData = useActionData() as LoginActionData | undefined;
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";
  const [showPassword, setShowPassword] = useState(false);

  const loginMessage = actionData?.error
    ? { tone: "error" as const, text: actionData.error }
    : null;
  const loginMessageId = "login-status-message";

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <AuthShell eyebrow="Professional Edition" title="No One">
        <Form method="post" className="w-full">
          <input type="hidden" name="returnTo" value={returnTo} />

          <div className="grid gap-6">
            <AuthStatusMessage id={loginMessageId} message={loginMessage} centered />

            <div className="grid gap-5">
              <div className="grid gap-2.5">
                <Label htmlFor="username" className={authLabelClassName}>
                  Username or email
                </Label>
                <Input
                  id="username"
                  name="username"
                  type="text"
                  autoFocus
                  required
                  disabled={isSubmitting}
                  aria-invalid={loginMessage?.tone === "error" || undefined}
                  aria-describedby={loginMessage ? loginMessageId : undefined}
                  autoComplete="username"
                  defaultValue={actionData?.formData?.username}
                  className={authInputClassName}
                />
              </div>

              <div className="grid gap-2.5">
                <Label htmlFor="password" className={authLabelClassName}>
                  Password
                </Label>
                <div className="relative">
                  <Input
                    id="password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    required
                    disabled={isSubmitting}
                    aria-invalid={loginMessage?.tone === "error" || undefined}
                    aria-describedby={loginMessage ? loginMessageId : undefined}
                    autoComplete="current-password"
                    className={cn(authInputClassName, "pr-12")}
                  />
                  <button
                    type="button"
                    className="absolute inset-y-0 right-0 flex w-12 items-center justify-center rounded-r-xl text-muted-foreground transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
                    onClick={() => setShowPassword((value) => !value)}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    aria-pressed={showPassword}
                  >
                    {showPassword ? (
                      <EyeOff className="size-[18px]" />
                    ) : (
                      <Eye className="size-[18px]" />
                    )}
                  </button>
                </div>
                <p className="text-right text-sm text-muted-foreground">
                  Need a password reset? Contact your administrator.
                </p>
              </div>

              <Button
                type="submit"
                size="lg"
                className={authPrimaryButtonClassName}
                disabled={isSubmitting}
              >
                {isSubmitting ? "Signing in..." : "Sign in"}
              </Button>
            </div>
          </div>
        </Form>
      </AuthShell>
    </div>
  );
}

export default function LoginPage() {
  return (
    <AuthPage>
      <LoginForm />
    </AuthPage>
  );
}
