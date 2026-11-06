import { redirect } from "react-router";

import { destroySession, getSession } from "@/api/session";

async function performLogout(request: Request) {
  const session = await getSession(request.headers.get("Cookie"));
  const url = new URL(request.url);
  destroySession(session);
  return redirect("/auth/login?" + url.searchParams);
}

export async function clientLoader({ request }: { request: Request }) {
  return performLogout(request);
}

export async function clientAction({ request }: { request: Request }) {
  return performLogout(request);
}

export default function LogoutPage() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center">
        <h1 className="text-2xl font-bold">LogOut...</h1>
      </div>
    </div>
  );
}
