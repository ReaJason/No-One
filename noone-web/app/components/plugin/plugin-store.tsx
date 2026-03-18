import type { CatalogEntry } from "@/types/plugin";

import { Check, Download, Loader, RefreshCw } from "lucide-react";
import { useFetcher } from "react-router";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

function PluginStoreCard({ entry }: { entry: CatalogEntry }) {
  const fetcher = useFetcher();
  const isSubmitting = fetcher.state !== "idle";

  return (
    <Card className="flex flex-col justify-between">
      <CardHeader>
        <CardTitle>{entry.name}</CardTitle>
        <CardDescription>{entry.id}</CardDescription>
        <div className="flex gap-1.5 pt-1">
          <Badge variant="secondary">{entry.language}</Badge>
          <Badge variant="outline">{entry.version}</Badge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col justify-between gap-4">
        <div className="space-y-1">
          {entry.description && (
            <p className="line-clamp-2 text-sm text-muted-foreground">{entry.description}</p>
          )}
          {entry.author && <p className="text-xs text-muted-foreground">by {entry.author}</p>}
        </div>
        <fetcher.Form method="post" action="/plugins">
          <input type="hidden" name="intent" value="install" />
          <input type="hidden" name="pluginId" value={entry.id} />
          <input type="hidden" name="language" value={entry.language} />
          {entry.installed && !entry.updateAvailable ? (
            <Button variant="secondary" className="w-full" disabled>
              <Check className="size-4" />
              Installed
            </Button>
          ) : entry.updateAvailable ? (
            <Button variant="default" className="w-full" type="submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <Loader className="size-4 animate-spin" />
              ) : (
                <RefreshCw className="size-4" />
              )}
              Update to {entry.version}
            </Button>
          ) : (
            <Button variant="default" className="w-full" type="submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <Loader className="size-4 animate-spin" />
              ) : (
                <Download className="size-4" />
              )}
              Install
            </Button>
          )}
        </fetcher.Form>
      </CardContent>
    </Card>
  );
}

type PluginStoreProps = {
  catalog: CatalogEntry[];
  error?: string;
  enabled: boolean;
};

export function PluginStore({ catalog, error, enabled }: PluginStoreProps) {
  if (!enabled) {
    return (
      <div className="flex items-center justify-center rounded-lg border-2 border-dashed p-8">
        <p className="text-center text-muted-foreground">
          Plugin registry is not enabled. Configure it in <code>application.yaml</code> to browse
          available plugins.
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center rounded-lg border-2 border-destructive p-8">
        <p className="text-center text-destructive">{error}</p>
      </div>
    );
  }

  if (catalog.length === 0) {
    return (
      <div className="flex items-center justify-center rounded-lg border-2 border-dashed p-8">
        <p className="text-center text-muted-foreground">No plugins available in the registry.</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
      {catalog.map((entry) => (
        <PluginStoreCard key={`${entry.id}-${entry.language}`} entry={entry} />
      ))}
    </div>
  );
}
