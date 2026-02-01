import { ScrollTextIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { downloadBytes, formatBytes } from "@/lib/utils";
import type { MemShellResult } from "@/types/memshell";

export function AgentResult({
  packMethod,
  packResult,
  generateResult,
}: Readonly<{
  packMethod: string;
  packResult: string;
  generateResult?: MemShellResult;
}>) {
  const isPureAgent = packMethod === "AgentJar";
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
              Download MemShellAgent.jar ({formatBytes(atob(packResult).length)}
              )
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
                  `${generateResult?.shellConfig.server}${generateResult?.shellConfig.shellTool}MemShellAgent`,
                )
              }
            >
              Download
            </Button>
          </li>
          {isPureAgent && (
            <li className="flex items-center justify-between">
              <span>Download the Jattach tool</span>
              <Button
                size="sm"
                variant="outline"
                className="w-28"
                type="button"
                onClick={() =>
                  window.open("https://github.com/jattach/jattach/releases")
                }
              >
                Download
              </Button>
            </li>
          )}
          <Separator />
          <li>
            {isPureAgent
              ? "Move MemShellAgent.jar and jattach to target host"
              : "Move MemShellAgent.jar to target host"}
          </li>
          <li>Get the process pid of the target jvm (use jps or ps)</li>
          <li>
            {isPureAgent
              ? "Execute the command to inject: /path/to/jattach pid load instrument false /path/to/agent.jar"
              : "Execute the command to inject: java -jar /path/to/agent.jar pid"}
          </li>
          <li>Try to use the memory shell</li>
        </ol>
      </CardContent>
    </Card>
  );
}
