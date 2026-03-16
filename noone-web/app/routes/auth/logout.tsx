import { redirect } from "react-router";

import { destroySession, getSession } from "@/api/sessions.server";

async function performLogout(request: Request) {
  const session = await getSession(request.headers.get("Cookie"));
  return redirect("/auth/login", {
    headers: {
      "Set-Cookie": await destroySession(session),
    },
  });
}

export async function loader({ request }: { request: Request }) {
  return performLogout(request);
}

export async function action({ request }: { request: Request }) {
  return performLogout(request);
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
