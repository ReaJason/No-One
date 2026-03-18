import { RotateCcw } from "lucide-react";

interface ServerErrorBoundaryProps {
  title?: string;
  description?: string;
  showRetry?: boolean;
  onRetry?: () => void;
  /** Pass the error object to show stack trace in dev mode */
  error?: Error | null;
  /** Override dev mode detection (defaults to process.env.NODE_ENV === "development") */
  devMode?: boolean;
}

export function ServerErrorBoundary({
  title = "Server error",
  description = "Sorry, something went wrong on our end. Please try again later.",
  showRetry = true,
  onRetry,
  error,
  devMode = process.env.NODE_ENV === "development",
}: ServerErrorBoundaryProps) {
  const devError = devMode ? (error ?? undefined) : undefined;
  const stackTrace = devError?.stack;
  const errorSummary = devError ? `${devError.name}: ${devError.message}` : "";
  const showStackPanel = Boolean(stackTrace);

  const handleRetry = () => {
    if (onRetry) {
      onRetry();
      return;
    }

    window.location.reload();
  };
  return (
    <main className="min-h-screen px-6 py-24 lg:px-8">
      <div className="mx-auto grid w-full max-w-7xl gap-10 lg:min-h-[calc(100vh-12rem)] lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:items-center">
        <section className="flex flex-col justify-center text-left">
          <p className="text-base font-semibold text-destructive">500</p>
          <h1 className="mt-4 text-5xl font-semibold tracking-tight text-foreground">{title}</h1>
          <p className="mt-6 max-w-2xl text-lg text-muted-foreground">{description}</p>

          <div className="mt-10 flex flex-wrap items-center gap-6">
            {showRetry ? (
              <button
                onClick={handleRetry}
                className="inline-flex items-center gap-2 text-sm font-semibold text-muted-foreground hover:text-foreground"
              >
                <RotateCcw className="size-4" />
                Try again
              </button>
            ) : null}
          </div>
        </section>

        {showStackPanel ? (
          <aside className="overflow-hidden rounded-2xl border border-destructive/20 bg-muted/50 shadow-sm">
            <div className="flex items-start justify-between gap-4 border-b border-destructive/15 bg-destructive/5 px-4 py-4">
              <div className="min-w-0">
                <div className="mb-2 inline-flex rounded bg-destructive/10 px-2 py-0.5 font-mono text-xs font-semibold text-destructive">
                  DEV
                </div>
                <p className="truncate text-sm font-semibold text-foreground">{errorSummary}</p>
              </div>
            </div>
            <pre className="max-h-[60vh] overflow-auto p-4 text-xs leading-relaxed text-muted-foreground lg:max-h-[70vh]">
              {stackTrace}
            </pre>
          </aside>
        ) : null}
      </div>
    </main>
  );
}
