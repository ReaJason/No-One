import type { MemShellResult } from "@/types/memshell";

import { DownloadIcon } from "lucide-react";
import { memo } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn, downloadBytes } from "@/lib/utils";

import CodeViewer from "./code-viewer";
import { BasicInfo } from "./results/basic-info";
import { ResultComponent } from "./results/result-component";

interface ShellResultProps {
  packResult: string | undefined;
  packMethod: string;
  generateResult?: MemShellResult;
}

// Optimize: Memoize component (rerender-memo)
const ShellResult = memo(function ShellResult({
  packResult,
  packMethod,
  generateResult,
}: Readonly<ShellResultProps>) {
  if (!generateResult) {
    return <></>;
  }
  const hasHelper =
    generateResult?.injectorHelperBytesBase64Str &&
    generateResult?.injectorHelperBytesBase64Str !== "";

  const height = 553;

  return (
    <Tabs defaultValue="packResult">
      <TabsList className={cn("grid w-full", hasHelper ? "grid-cols-4" : "grid-cols-3")}>
        <TabsTrigger value="packResult">Generate Result</TabsTrigger>
        <TabsTrigger value="shell">ShellClass</TabsTrigger>
        <TabsTrigger value="injector">InjectorClass</TabsTrigger>
        {hasHelper && <TabsTrigger value="injectorHelper">InjectorHelperClass</TabsTrigger>}
      </TabsList>
      <TabsContent value="packResult" className="space-y-2">
        <BasicInfo generateResult={generateResult} />
        <ResultComponent
          packResult={packResult}
          packMethod={packMethod}
          generateResult={generateResult}
        />
      </TabsContent>
      <TabsContent value="shell" className="mt-4">
        <CodeViewer
          showLineNumbers={false}
          header={<div className="truncate text-xs">{generateResult?.shellClassName}</div>}
          button={
            <Button
              variant="ghost"
              size="icon"
              type="button"
              className="h-7 w-7 [&_svg]:h-4 [&_svg]:w-4"
              onClick={() => {
                if (!generateResult?.shellBytesBase64Str) {
                  toast.warning("Shell bytes is empty, please generate shell first");
                  return;
                }
                downloadBytes(generateResult?.shellBytesBase64Str, generateResult?.shellClassName);
              }}
            >
              <DownloadIcon className="h-4 w-4" />
            </Button>
          }
          wrapLongLines={true}
          height={height}
          code={generateResult?.shellBytesBase64Str ?? ""}
          language="text"
        />
      </TabsContent>
      <TabsContent value="injector" className="mt-4">
        <CodeViewer
          showLineNumbers={false}
          wrapLongLines={true}
          header={<div className="text-xs">{generateResult?.injectorClassName}</div>}
          button={
            <Button
              variant="ghost"
              size="icon"
              type="button"
              className="h-7 w-7 [&_svg]:h-4 [&_svg]:w-4"
              onClick={() => {
                if (!generateResult?.injectorBytesBase64Str) {
                  toast.warning("Shell bytes is empty, please generate shell first");
                  return;
                }
                downloadBytes(
                  generateResult?.injectorBytesBase64Str,
                  generateResult?.injectorClassName,
                );
              }}
            >
              <DownloadIcon className="h-4 w-4" />
            </Button>
          }
          height={height}
          code={generateResult?.injectorBytesBase64Str ?? ""}
          language="text"
        />
      </TabsContent>
      {hasHelper && (
        <TabsContent value="injectorHelper" className="mt-4">
          <CodeViewer
            showLineNumbers={false}
            wrapLongLines={true}
            header={
              <div className="text-xs">
                {generateResult?.injectorConfig.injectorHelperClassName}
              </div>
            }
            button={
              <Button
                variant="ghost"
                size="icon"
                type="button"
                className="h-7 w-7 [&_svg]:h-4 [&_svg]:w-4"
                onClick={() => {
                  if (!generateResult?.injectorHelperBytesBase64Str) {
                    toast.warning("Shell bytes is empty, please generate shell first");
                    return;
                  }
                  downloadBytes(
                    generateResult?.injectorHelperBytesBase64Str,
                    generateResult?.injectorConfig.injectorHelperClassName,
                  );
                }}
              >
                <DownloadIcon className="h-4 w-4" />
              </Button>
            }
            height={height}
            code={generateResult?.injectorHelperBytesBase64Str ?? ""}
            language="text"
          />
        </TabsContent>
      )}
    </Tabs>
  );
});

export default ShellResult;
