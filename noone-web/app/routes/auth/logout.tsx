import {authUtils} from "@/lib/auth";

export async function action({ request }: { request: Request }) {
  try {
    await authUtils.logout();
  } catch (error) {
    console.error("Logout error:", error);
  }
  const logoutResponse = await authUtils.createLogoutResponse(request);
  return new Response(null, {
    status: 302,
    headers: {
      ...Object.fromEntries(logoutResponse.headers.entries()),
      Location: "/auth/login",
    },
  });
}
export default function LogoutPage() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center">
        <h1 className="text-2xl font-bold">正在登出...</h1>
        <p className="text-muted-foreground">请稍候，正在为您登出系统。</p>
      </div>
    </div>
  );
}
