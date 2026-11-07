import type { Route } from "./+types/root";

import { ThemeProvider } from "next-themes";
import { lazy, Suspense } from "react";
import "@fontsource-variable/inter/wght.css";

import "./app.css";
import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from "react-router";

import { TailwindIndicator } from "@/components/tailwind-indicator";
import { isAuthRedirectError } from "@/lib/auth-redirect-error";

const NotFoundErrorBoundary = lazy(async () => {
  const { NotFoundErrorBoundary } = await import("@/components/not-found-error-boundary");
  return { default: NotFoundErrorBoundary };
});
const ServerErrorBoundary = lazy(async () => {
  const { ServerErrorBoundary } = await import("@/components/server-error-boundary");
  return { default: ServerErrorBoundary };
});
const UnAuthorizedErrorBoundary = lazy(async () => {
  const { UnAuthorizedErrorBoundary } = await import("@/components/unauthorized-error-boundary");
  return { default: UnAuthorizedErrorBoundary };
});

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body className="min-h-screen">
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          {children}
          {import.meta.env.MODE !== "production" && <TailwindIndicator />}
        </ThemeProvider>
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function App() {
  return <Outlet />;
}

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  let boundary: React.ReactNode;

  if (isAuthRedirectError(error)) {
    boundary = <UnAuthorizedErrorBoundary description={error.message} />;
  } else if (isRouteErrorResponse(error)) {
    boundary =
      error.status === 404 ? (
        <NotFoundErrorBoundary />
      ) : (
        <ServerErrorBoundary
          description={error.statusText || "An unexpected error occurred."}
          devMode={import.meta.env.DEV}
        />
      );
  } else {
    boundary = (
      <ServerErrorBoundary
        description={
          error instanceof Error
            ? error.message
            : "Sorry, something went wrong on our end. Please try again later."
        }
        error={error instanceof Error ? error : undefined}
        devMode={import.meta.env.DEV}
      />
    );
  }

  return <Suspense fallback={null}>{boundary}</Suspense>;
}
