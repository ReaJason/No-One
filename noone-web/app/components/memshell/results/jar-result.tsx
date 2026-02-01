import { ScrollTextIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { downloadBytes, formatBytes } from "@/lib/utils";
import type { MemShellResult } from "@/types/memshell";

export function JarResult({
  packMethod,
  packResult,
  generateResult,
}: Readonly<{
  packMethod: string;
  packResult: string;
  generateResult?: MemShellResult;
}>) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-md flex items-center gap-2">
          <ScrollTextIcon className="h-5" />
          <span>Usage</span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ol className="list-decimal list-inside space-y-4 text-sm">
          <li className="flex items-center justify-between">
            <span>
              Download {packMethod}Shell.jar (
              {formatBytes(atob(packResult).length)})
            </span>
            <Button
              size="sm"
              variant="outline"
              className="w-28"
              type="button"
              onClick={() =>
                downloadBytes(
                  packResult,
                  undefined,
                  `${generateResult?.shellConfig.server}${generateResult?.shellConfig.shellTool}MemShell`,
                )
              }
            >
              Download
            </Button>
          </li>
          <Separator />
          <li>
            Download the jar file and upload it to the public network server, so
            that it can be accessed through the http link to download
          </li>
          <li>Trigger the injector class loading with RCE vulnerability</li>
        </ol>
      </CardContent>
    </Card>
  );
}
