import { Skeleton } from "@/components/ui/skeleton";

export type ShellSectionSkeletonVariant =
  | "dashboard"
  | "file-manager"
  | "command"
  | "extensions"
  | "list";

function SkeletonBlock({ className }: { className: string }) {
  return <Skeleton className={className} />;
}

function ShellSectionBodySkeleton({ variant }: { variant: ShellSectionSkeletonVariant }) {
  switch (variant) {
    case "file-manager":
      return (
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            <SkeletonBlock className="h-9 w-44" />
            <SkeletonBlock className="h-9 w-28" />
            <SkeletonBlock className="h-9 w-28" />
          </div>
          <div className="rounded-xl border p-4">
            <SkeletonBlock className="mb-4 h-5 w-64" />
            <div className="space-y-3">
              {Array.from({ length: 8 }).map((_, index) => (
                <SkeletonBlock key={index} className="h-10 w-full" />
              ))}
            </div>
          </div>
        </div>
      );
    case "command":
      return (
        <div className="space-y-3">
          <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
            <div className="space-y-3 rounded-xl border p-4">
              <SkeletonBlock className="h-5 w-32" />
              <SkeletonBlock className="h-10 w-full" />
              <SkeletonBlock className="h-24 w-full" />
              <SkeletonBlock className="h-10 w-28" />
            </div>
            <div className="rounded-xl border bg-muted/20 p-4">
              <SkeletonBlock className="mb-3 h-5 w-40" />
              <SkeletonBlock className="h-80 w-full" />
            </div>
          </div>
        </div>
      );
    case "extensions":
      return (
        <div className="space-y-4">
          <div className="flex gap-2 overflow-hidden">
            <SkeletonBlock className="h-9 w-28" />
            <SkeletonBlock className="h-9 w-36" />
            <SkeletonBlock className="h-9 w-32" />
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className="rounded-xl border p-4">
                <SkeletonBlock className="mb-3 h-5 w-40" />
                <SkeletonBlock className="mb-2 h-4 w-full" />
                <SkeletonBlock className="mb-4 h-4 w-4/5" />
                <SkeletonBlock className="h-9 w-24" />
              </div>
            ))}
          </div>
        </div>
      );
    case "list":
      return (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <SkeletonBlock className="h-9 w-48" />
            <SkeletonBlock className="h-4 w-24" />
          </div>
          <div className="space-y-2">
            {Array.from({ length: 7 }).map((_, index) => (
              <div key={index} className="rounded-xl border p-4">
                <div className="mb-2 flex items-center gap-2">
                  <SkeletonBlock className="h-4 w-4 rounded-full" />
                  <SkeletonBlock className="h-5 w-24" />
                  <SkeletonBlock className="h-4 w-32" />
                </div>
                <SkeletonBlock className="mb-2 h-4 w-2/3" />
                <SkeletonBlock className="h-4 w-32" />
              </div>
            ))}
          </div>
        </div>
      );
    case "dashboard":
    default:
      return (
        <div className="space-y-4">
          <div className="grid gap-4 xl:grid-cols-2">
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className="rounded-xl border p-4">
                <SkeletonBlock className="mb-3 h-5 w-36" />
                <div className="grid gap-3 md:grid-cols-2">
                  <SkeletonBlock className="h-16 w-full" />
                  <SkeletonBlock className="h-16 w-full" />
                  <SkeletonBlock className="h-16 w-full" />
                  <SkeletonBlock className="h-16 w-full" />
                </div>
              </div>
            ))}
          </div>
        </div>
      );
  }
}

export function ShellManagerSkeleton() {
  return (
    <div
      role="status"
      aria-label="Loading shell manager"
      className="flex min-h-svh w-full bg-background"
    >
      <div className="hidden w-72 shrink-0 border-r bg-sidebar/40 p-4 md:flex md:flex-col">
        <SkeletonBlock className="mb-6 h-12 w-full" />
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, index) => (
            <SkeletonBlock key={index} className="h-10 w-full" />
          ))}
        </div>
      </div>
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex h-16 items-center justify-between border-b px-4">
          <div className="flex items-center gap-3">
            <SkeletonBlock className="h-8 w-8" />
            <SkeletonBlock className="h-6 w-72 max-w-[50vw]" />
          </div>
          <div className="flex items-center gap-2">
            <SkeletonBlock className="h-8 w-8" />
            <SkeletonBlock className="h-8 w-8" />
          </div>
        </div>
        <div className="flex-1 p-4">
          <ShellSectionBodySkeleton variant="dashboard" />
        </div>
      </div>
    </div>
  );
}

export function ShellSectionSkeleton({
  label = "Loading shell section",
  variant = "dashboard",
  showStatusCard = true,
}: {
  label?: string;
  variant?: ShellSectionSkeletonVariant;
  showStatusCard?: boolean;
}) {
  return (
    <div role="status" aria-label={label} className="flex h-full min-h-0 flex-col gap-4 p-4">
      {showStatusCard ? (
        <div className="rounded-xl border p-4">
          <div className="flex items-center justify-between gap-4">
            <div className="space-y-2">
              <SkeletonBlock className="h-5 w-40" />
              <SkeletonBlock className="h-4 w-56" />
            </div>
            <SkeletonBlock className="h-9 w-24" />
          </div>
        </div>
      ) : null}
      <ShellSectionBodySkeleton variant={variant} />
    </div>
  );
}
