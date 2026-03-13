import type { Plugin } from "@/types/plugin";

import { ExternalLink, Puzzle } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";

interface PluginCatalogProps {
  extensionPlugins: Plugin[];
  openPluginIds: string[];
  onOpenPlugin: (plugin: Plugin) => void;
}

export default function PluginCatalog({
  extensionPlugins,
  openPluginIds,
  onOpenPlugin,
}: PluginCatalogProps) {
  if (extensionPlugins.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center text-muted-foreground">
        No extension plugins available
      </div>
    );
  }

  return (
    <ScrollArea className="flex-1">
      <div className="grid grid-cols-1 gap-4 p-1 md:grid-cols-2">
        {extensionPlugins.map((plugin) => {
          const actions = plugin.actions ?? {};
          const actionKeys = Object.keys(actions);
          const firstAction = actionKeys.length > 0 ? actions[actionKeys[0]] : undefined;
          const description = firstAction?.description || "No description available";
          const isOpen = openPluginIds.includes(plugin.id);

          return (
            <Card key={plugin.id} size="sm">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Puzzle className="h-4 w-4 shrink-0" />
                  <span className="truncate">{plugin.name}</span>
                  <Badge variant="outline" className="shrink-0">
                    {plugin.version}
                  </Badge>
                </CardTitle>
                <CardDescription className="line-clamp-2">{description}</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="flex items-center gap-2">
                  <Badge variant="secondary">{plugin.language}</Badge>
                  {actionKeys.length > 0 && (
                    <Badge variant="outline">
                      {actionKeys.length} {actionKeys.length === 1 ? "action" : "actions"}
                    </Badge>
                  )}
                </div>
              </CardContent>
              <CardFooter>
                {isOpen ? (
                  <Button variant="outline" size="sm" disabled className="ml-auto">
                    Opened
                  </Button>
                ) : (
                  <Button
                    variant="default"
                    size="sm"
                    className="ml-auto"
                    onClick={() => onOpenPlugin(plugin)}
                  >
                    <ExternalLink className="mr-1.5 h-3.5 w-3.5" />
                    Open
                  </Button>
                )}
              </CardFooter>
            </Card>
          );
        })}
      </div>
    </ScrollArea>
  );
}
