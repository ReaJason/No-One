import type { CodeViewerProps } from "@/components/memshell/code-viewer";

import { lazy, Suspense } from "react";

const CodeViewer = lazy(() => import("@/components/memshell/code-viewer"));

export default function LazyCodeViewer(props: Readonly<CodeViewerProps>) {
  return (
    <Suspense
      fallback={
        <div
          role="status"
          className="rounded-lg border p-4 text-sm text-muted-foreground"
          style={{ height: props.height }}
        >
          Loading code viewer...
        </div>
      }
    >
      <CodeViewer {...props} />
    </Suspense>
  );
}
