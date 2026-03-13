import type { User } from "@/types/admin";

import { GalleryVerticalEnd } from "lucide-react";
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

import { publicApi } from "@/api.server";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { commitSession, getSession } from "@/sessions.server";

const REQUIRE_2FA_CODE = "REQUIRE_2FA";
const INVALID_2FA_CODE = "INVALID_2FA_CODE";

type LoginApiSuccess = {
  token?: string;
  refreshToken?: string;
  user?: User;
  mfaRequired?: boolean;
  nextAction?: string;
  actionToken?: string | null;
};

type LoginApiChallenge = {
  code?: string;
  message?: string;
  status?: string;
  mfaRequired?: boolean;
  setupToken?: string | null;
  actionToken?: string | null;
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

function getLoginMessage(
  code?: string,
  message?: string,
): { tone: "error" | "info"; text: string } | null {
  if (!message) {
    return null;
  }

  if (code === REQUIRE_2FA_CODE) {
    return {
      tone: "info",
      text: message,
    };
  }

  return {
    tone: "error",
    text: message,
  };
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
  const twoFactorCode = getFormString(formData, "twoFactorCode");
  const returnTo = normalizeReturnTo(getFormString(formData, "returnTo"));

  if (!username || !password) {
    return {
      error: "Username and password are required",
      success: false,
    };
  }
  try {
    const response = await publicApi<LoginApiSuccess | LoginApiChallenge>("/auth/login", {
      method: "POST",
      body: {
        username,
        password,
        ...(twoFactorCode ? { twoFactorCode } : {}),
      },
    });

    if ("code" in response && response.code === REQUIRE_2FA_CODE) {
      return {
        error: response.message ?? "Enter your authenticator code to continue",
        code: response.code,
        mfaRequired: response.mfaRequired === true,
        success: false,
        formData: {
          username,
          password,
        },
      };
    }

    if (!isLoginApiSuccess(response)) {
      return {
        error: "Login failed. Please try again.",
        success: false,
      };
    }
    const session = await getSession(request.headers.get("Cookie"));
    session.set("accessToken", response.token);
    session.set("refreshToken", response.refreshToken);
    session.set("user", response.user);
    return redirect(returnTo, {
      headers: {
        "Set-Cookie": await commitSession(session),
      },
    });
  } catch (error: any) {
    const detailMessage =
      error?.details?.message ?? error?.details?.error ?? error?.message ?? "Login failed";
    const detailCode = error?.details?.code;
    const mfaRequired = error?.details?.mfaRequired === true;
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
      code: detailCode,
      mfaRequired,
      success: false,
      formData: {
        username,
        password,
      },
    };
  }
}

function LoginForm({ className, ...props }: React.ComponentProps<"div">) {
  const { returnTo } = useLoaderData<typeof loader>();
  const actionData = useActionData() as
    | {
        error?: string;
        code?: string;
        mfaRequired?: boolean;
        success?: boolean;
        formData?: { username: string; password: string };
      }
    | undefined;
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";

  const require2FA =
    actionData?.code === REQUIRE_2FA_CODE ||
    actionData?.code === INVALID_2FA_CODE ||
    actionData?.mfaRequired === true;
  const loginMessage = getLoginMessage(actionData?.code, actionData?.error);

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader className="text-center">
          <CardTitle className="text-xl">Welcome back</CardTitle>
          <CardDescription>Login with your account</CardDescription>
        </CardHeader>
        <CardContent>
          <Form method="post">
            <input type="hidden" name="returnTo" value={returnTo} />
            <div className="grid gap-6">
              {loginMessage ? (
                <div
                  className={cn(
                    "text-center text-sm font-medium",
                    loginMessage.tone === "error" ? "text-destructive" : "text-muted-foreground",
                  )}
                >
                  {loginMessage.text}
                </div>
              ) : null}
              <div className="grid gap-6">
                {!require2FA ? (
                  <>
                    <div className="grid gap-3">
                      <Label htmlFor="username">Username</Label>
                      <Input
                        id="username"
                        name="username"
                        type="text"
                        required
                        disabled={isSubmitting}
                        defaultValue={actionData?.formData?.username}
                      />
                    </div>
                    <div className="grid gap-3">
                      <div className="flex items-center">
                        <Label htmlFor="password">Password</Label>
                      </div>
                      <Input
                        id="password"
                        name="password"
                        type="password"
                        required
                        disabled={isSubmitting}
                        defaultValue={actionData?.formData?.password}
                      />
                    </div>
                  </>
                ) : (
                  <>
                    <input type="hidden" name="username" value={actionData?.formData?.username} />
                    <input type="hidden" name="password" value={actionData?.formData?.password} />
                    <div className="grid gap-3">
                      <Label htmlFor="twoFactorCode">Authenticator Code</Label>
                      <Input
                        id="twoFactorCode"
                        name="twoFactorCode"
                        type="text"
                        required
                        disabled={isSubmitting}
                        autoFocus
                        placeholder="123456"
                        maxLength={6}
                      />
                    </div>
                  </>
                )}

                <Button type="submit" className="w-full" disabled={isSubmitting}>
                  {isSubmitting ? "Logging in..." : "Login"}
                </Button>
              </div>
            </div>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function LoginPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-muted p-6 md:p-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <a href="/" className="flex items-center gap-2 self-center font-medium">
          <div className="flex size-6 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <GalleryVerticalEnd className="size-4" />
          </div>
          No One
        </a>
        <LoginForm />
      </div>
    </div>
  );
}
