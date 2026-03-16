import type { Route } from "./+types/root";

import { ThemeProvider } from "next-themes";

import "./app.css";
import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from "react-router";

import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { ServerErrorBoundary } from "@/components/server-error-boundary";
import { TailwindIndicator } from "@/components/tailwind-indicator";

export const links: Route.LinksFunction = () => [
  { rel: "preconnect", href: "https://fonts.googleapis.com" },
  {
    rel: "preconnect",
    href: "https://fonts.gstatic.com",
    crossOrigin: "anonymous",
  },
  {
    rel: "stylesheet",
    href: "https://fonts.googleapis.com/css2?family=Inter:ital,opsz,wght@0,14..32,100..900;1,14..32,100..900&display=swap",
  },
];

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
  if (isRouteErrorResponse(error)) {
    if (error.status === 404) {
      return <NotFoundErrorBoundary />;
    }

    return (
      <ServerErrorBoundary
        description={error.statusText || "An unexpected error occurred."}
        devMode={import.meta.env.DEV}
      />
    );
  }

  return (
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
