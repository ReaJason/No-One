import type { ComponentProps, ReactNode } from "react";

import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type BadgeVariant = ComponentProps<typeof Badge>["variant"];

export type FormPageBadge = {
  label: ReactNode;
  variant?: BadgeVariant;
};

type FormPageShellProps = {
  backHref: string;
  backLabel: string;
  title: ReactNode;
  description: ReactNode;
  badges?: FormPageBadge[];
  children: ReactNode;
  className?: string;
  contentClassName?: string;
};

export function FormPageShell({
  backHref,
  backLabel,
  title,
  description,
  badges,
  children,
  className,
  contentClassName,
}: FormPageShellProps) {
  const navigate = useNavigate();

  return (
    <div className={cn("container mx-auto max-w-7xl p-6", className)}>
      <section className="mb-8 rounded-2xl border border-border/70 bg-gradient-to-br from-muted/40 via-background to-background p-6 shadow-sm">
        <Button
          variant="ghost"
          onClick={() => navigate(backHref)}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          {backLabel}
        </Button>

        <div className="space-y-4">
          {badges?.length ? (
            <div className="flex flex-wrap items-center gap-2">
              {badges.map((badge) => (
                <Badge key={String(badge.label)} variant={badge.variant}>
                  {badge.label}
                </Badge>
              ))}
            </div>
          ) : null}

          <div className="space-y-2">
            <h1 className="text-3xl font-bold text-balance">{title}</h1>
            <p className="max-w-3xl text-sm text-muted-foreground sm:text-base">{description}</p>
          </div>
        </div>
      </section>

      <div className={cn("mx-auto", contentClassName)}>{children}</div>
    </div>
  );
}
