"use client";

import { ArrowLeft } from "lucide-react";
import { Link } from "react-router";

import { cn } from "@/lib/utils";

interface NotFoundErrorBoundaryProps {
  resourceType?: string;
  resourceId?: string;
  title?: string;
  description?: string;
  backHref?: string;
  backLabel?: string;
}

export function NotFoundErrorBoundary({
  resourceType = "page",
  resourceId,
  title = "Page not found",
  description,
  backHref = "/",
  backLabel = "Back to home",
}: NotFoundErrorBoundaryProps) {
  const defaultDescription = resourceId
    ? `Sorry, we couldn't find the ${resourceType.toLowerCase()} with ID "${resourceId}".`
    : `Sorry, we couldn't find the ${resourceType.toLowerCase()} you're looking for.`;

  return (
    <main
      className={cn(
        "flex flex-col justify-center px-6 py-24 lg:px-8",
        backHref === "/" ? "min-h-screen" : "",
      )}
    >
      <div className="text-left">
        <p className="text-base font-semibold text-primary">404</p>
        <h1 className="mt-4 text-5xl font-semibold tracking-tight text-foreground">{title}</h1>
        <p className="mt-6 text-lg text-muted-foreground">{description || defaultDescription}</p>
        <div className="mt-10">
          <Link
            to={backHref}
            className="inline-flex items-center gap-2 text-sm font-semibold text-primary hover:text-primary/80"
          >
            <ArrowLeft className="size-4" />
            {backLabel}
          </Link>
        </div>
      </div>
    </main>
  );
}
