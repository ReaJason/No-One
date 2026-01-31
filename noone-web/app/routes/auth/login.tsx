import {GalleryVerticalEnd} from "lucide-react";
import {useState} from "react";
import type {ActionFunctionArgs, LoaderFunctionArgs} from "react-router";
import {Form, useActionData, useNavigation, useSearchParams,} from "react-router";
import {Button} from "@/components/ui/button";
import {Card, CardContent, CardDescription, CardHeader, CardTitle,} from "@/components/ui/card";
import {Checkbox} from "@/components/ui/checkbox";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {authUtils} from "@/lib/auth";
import {cn} from "@/lib/utils";

// Loader 函数处理页面加载
export async function loader({ request }: LoaderFunctionArgs) {
  const url = new URL(request.url);
  const returnTo = url.searchParams.get("returnTo");
  return { returnTo };
}

// Action 函数处理表单提交
export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const username = formData.get("username") as string;
  const password = formData.get("password") as string;
  const agreedToTerms = formData.get("agreedToTerms") === "on";
  const returnTo = formData.get("returnTo") as string;
  if (!username || !password) {
    return {
      error: "用户名和密码不能为空",
      success: false,
    };
  }

  if (!agreedToTerms) {
    return {
      error: "请同意服务条款和隐私政策",
      success: false,
    };
  }
  try {
    const response = await authUtils.login({ username, password });
    const authResponse = await authUtils.createAuthResponse(
      response.token,
      response.user,
      response.expiresIn,
      request,
    );
    console.log(
      `[Login] User ${response.user.username} logged in successfully`,
    );
    console.log(`[Login] JWT Token set: ${response.token.substring(0, 20)}...`);
    const redirectTo = returnTo || "/";

    return new Response(null, {
      status: 302,
      headers: {
        ...Object.fromEntries(authResponse.headers.entries()),
        Location: redirectTo,
      },
    });
  } catch (error: any) {
    console.error("Login error:", error);
    return {
      error: error.message || "登录失败，请检查用户名和密码",
      success: false,
    };
  }
}

function LoginForm({ className, ...props }: React.ComponentProps<"div">) {
  const [agreedToTerms, setAgreedToTerms] = useState(false);
  const [searchParams] = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const actionData = useActionData() as
    | { error?: string; success?: boolean }
    | undefined;
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader className="text-center">
          <CardTitle className="text-xl">Welcome back</CardTitle>
          <CardDescription>Login with your account</CardDescription>
        </CardHeader>
        <CardContent>
          <Form method="post">
            {returnTo && (
              <input type="hidden" name="returnTo" value={returnTo} />
            )}
            <div className="grid gap-6">
              {actionData?.error && (
                <div className="text-red-500 text-sm text-center">
                  {actionData.error}
                </div>
              )}
              <div className="grid gap-6">
                <div className="grid gap-3">
                  <Label htmlFor="username">Username</Label>
                  <Input
                    id="username"
                    name="username"
                    type="text"
                    required
                    disabled={isSubmitting}
                  />
                </div>
                <div className="grid gap-3">
                  <div className="flex items-center">
                    <Label htmlFor="password">Password</Label>
                    <a
                      href="/auth/reset-password"
                      className="ml-auto text-sm underline-offset-4 hover:underline"
                    >
                      Forgot your password?
                    </a>
                  </div>
                  <Input
                    id="password"
                    name="password"
                    type="password"
                    required
                    disabled={isSubmitting}
                  />
                </div>
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="terms"
                    name="agreedToTerms"
                    checked={agreedToTerms}
                    onCheckedChange={(checked) =>
                      setAgreedToTerms(checked as boolean)
                    }
                    required
                    disabled={isSubmitting}
                  />
                  <div className="text-muted-foreground *:[a]:hover:text-primary text-xs text-balance *:[a]:underline *:[a]:underline-offset-4">
                    I agree to the{" "}
                    <a href="/auth/terms-of-service">Terms of Service</a> and{" "}
                    <a href="/auth/privacy-policy">Privacy Policy</a>.
                  </div>
                </div>
                <Button
                  type="submit"
                  className="w-full"
                  disabled={!agreedToTerms || isSubmitting}
                >
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
    <div className="bg-muted flex min-h-svh flex-col items-center justify-center gap-6 p-6 md:p-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <a href="/" className="flex items-center gap-2 self-center font-medium">
          <div className="bg-primary text-primary-foreground flex size-6 items-center justify-center rounded-md">
            <GalleryVerticalEnd className="size-4" />
          </div>
          No One
        </a>
        <LoginForm />
      </div>
    </div>
  );
}
