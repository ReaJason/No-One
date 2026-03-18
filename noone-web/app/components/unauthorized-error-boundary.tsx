import { useLocation } from "react-router";

import { Button } from "@/components/ui/button";

interface UnAuthorizedErrorBoundaryProps {
  title?: string;
  description?: string;
}

export function UnAuthorizedErrorBoundary({
  title = "UnAuthorized",
  description = "Your current authentication is no longer valid. ",
}: UnAuthorizedErrorBoundaryProps) {
  let location = useLocation();
  return (
    <main className="min-h-screen px-6 py-24 lg:px-8">
      <div className="mx-auto grid w-full max-w-7xl gap-10 lg:min-h-[calc(100vh-12rem)] lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:items-center">
        <section className="flex flex-col justify-center text-left">
          <p className="text-base font-semibold text-destructive">401</p>
          <h1 className="mt-4 text-5xl font-semibold tracking-tight text-foreground">{title}</h1>
          <p className="mt-6 max-w-2xl text-lg text-muted-foreground">{description}</p>
          <div className="mt-10">
            <form method="post" action={"/auth/logout" + location.search} className="mt-6">
              <Button type="submit" className="w-full">
                Log in again
              </Button>
            </form>
          </div>
        </section>
      </div>
    </main>
  );
}
