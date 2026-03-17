import type { ComponentProps, ReactNode } from "react";

import { Icons } from "@/components/icons";
import { cn } from "@/lib/utils";

export const authLabelClassName = "text-sm font-medium text-foreground";
export const authInputClassName =
  "border-border bg-background px-4 text-sm text-foreground shadow-sm";
export const authPrimaryButtonClassName = "w-full text-sm font-medium shadow-sm";

export function getDescribedBy(...ids: Array<string | undefined>): string | undefined {
  const value = ids.filter(Boolean).join(" ");
  return value || undefined;
}

export function AuthPage({
  className,
  children,
  ...props
}: ComponentProps<"main"> & { children: ReactNode }) {
  return (
    <main
      className={cn(
        "flex min-h-svh flex-col items-center bg-background px-6 pt-[10vh] pb-16 md:px-8 md:pt-[15vh] md:pb-24",
        className,
      )}
      {...props}
    >
      <div className="w-full max-w-[34rem] space-y-6">
        {children}
        <p className="text-center text-xs text-muted-foreground">
          © 2026 No One. All rights reserved.
        </p>
      </div>
    </main>
  );
}

export function AuthShell({
  eyebrow,
  title,
  description,
  className,
  children,
}: {
  eyebrow: string;
  title: string;
  description?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={cn("mx-auto flex w-full flex-col gap-2", className)}>
      <div className="flex flex-col items-center text-center">
        <Icons.logo className="size-16 drop-shadow-sm" />
        <div className="space-y-2">
          <h1 className="text-[clamp(1.9rem,4vw,2.5rem)] font-semibold tracking-tight text-foreground">
            {title}
          </h1>
          <p className="text-xs font-medium tracking-[0.32em] text-muted-foreground uppercase">
            {eyebrow}
          </p>
        </div>
        {description ? (
          <p className="max-w-[32rem] text-sm leading-6 text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {children}
    </section>
  );
}

export function AuthStatusMessage({
  id,
  message,
  centered = false,
}: {
  id: string;
  message: { tone: "error" | "info"; text: string } | null;
  centered?: boolean;
}) {
  if (!message) {
    return null;
  }

  return (
    <div
      id={id}
      role={message.tone === "error" ? "alert" : "status"}
      aria-live={message.tone === "error" ? "assertive" : "polite"}
      className={cn(
        "rounded-xl border px-4 py-3 text-sm font-medium",
        centered && "text-center",
        message.tone === "error"
          ? "border-destructive/20 bg-destructive/10 text-destructive"
          : "border-border bg-muted/50 text-muted-foreground",
      )}
    >
      {message.text}
    </div>
  );
}
