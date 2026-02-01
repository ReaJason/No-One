import { DownloadIcon, PlusIcon } from "lucide-react";
import { memo, useCallback, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";

import { QuickUsage } from "@/components/memshell/quick-usage";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { downloadBytes } from "@/lib/utils";
import type { MemShellResult } from "@/types/memshell";
import CodeViewer from "./code-viewer";
import { BasicInfo } from "./results/basic-info";
import { ResultComponent } from "./results/result-component";

interface ShellResultProps {
  packResult: string | undefined;
  allPackResults: Map<string, string> | undefined;
  packMethod: string;
  generateResult?: MemShellResult;
}

// Optimize: Memoize component (rerender-memo)
const ShellResult = memo(function ShellResult({
  packResult,
  allPackResults,
  packMethod,
  generateResult,
}: Readonly<ShellResultProps>) {
  const navigate = useNavigate();
  const [targetUrl, setTargetUrl] = useState("");
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [urlError, setUrlError] = useState("");
  const profileId = generateResult?.shellToolConfig.profile?.id;

  // Optimize: Stable callback (rerender-functional-setstate)
  const handleUrlChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setTargetUrl(e.target.value);
      if (urlError) setUrlError("");
    },
    [urlError],
  );

  const handleAddShell = useCallback(() => {
    // Validate URL
    if (!targetUrl.trim()) {
      setUrlError("URL is required");
      return;
    }

    try {
      new URL(targetUrl);
    } catch {
      setUrlError("Please enter a valid URL");
      return;
    }

    if (!profileId) {
      toast.error("No profile selected");
      return;
    }

    // Redirect to create shell page with URL and profile as query params
    const params = new URLSearchParams({
      shellUrl: targetUrl,
      profileId: profileId,
    });
    navigate(`/shells/create?${params.toString()}`);
  }, [targetUrl, profileId, navigate]);

  const handleDialogOpenChange = useCallback((open: boolean) => {
    setIsDialogOpen(open);
    if (!open) {
      // Reset state when dialog closes
      setTargetUrl("");
      setUrlError("");
    }
  }, []);

  if (!generateResult) {
    return <QuickUsage />;
  }

  const height = 553;

  return (
    <Tabs defaultValue="packResult">
      <TabsList className="grid w-full grid-cols-3">
        <TabsTrigger value="packResult">Generate Result</TabsTrigger>
        <TabsTrigger value="shell">ShellClass</TabsTrigger>
        <TabsTrigger value="injector">InjectorClass</TabsTrigger>
      </TabsList>
      <TabsContent value="packResult" className="space-y-2">
        <BasicInfo generateResult={generateResult} />
        <ResultComponent
          packResult={packResult}
          allPackResults={allPackResults}
          packMethod={packMethod}
          generateResult={generateResult}
        />
        {/* Add Shell Button */}
        <Dialog open={isDialogOpen} onOpenChange={handleDialogOpenChange}>
          <DialogTrigger
            render={
              <Button className="w-full" variant="outline">
                <PlusIcon className="h-4 w-4" />
                Add Shell
              </Button>
            }
          ></DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Add Shell Connection</DialogTitle>
            </DialogHeader>
            <Field data-invalid={!!urlError}>
              <FieldLabel htmlFor="target-url">Shell URL *</FieldLabel>
              <Input
                id="target-url"
                type="url"
                placeholder="http://example.com/shell.jsp"
                value={targetUrl}
                onChange={handleUrlChange}
                aria-invalid={!!urlError}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    handleAddShell();
                  }
                }}
              />
              {urlError && <FieldError>{urlError}</FieldError>}
            </Field>
            <DialogFooter>
              <DialogClose
                render={<Button variant="outline">Cancel</Button>}
              ></DialogClose>
              <Button onClick={handleAddShell}>
                <PlusIcon className="h-4 w-4" />
                Continue
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </TabsContent>
      <TabsContent value="shell" className="mt-4">
        <CodeViewer
          showLineNumbers={false}
          header={
            <div className="text-xs truncate">
              {generateResult?.shellClassName}
            </div>
          }
          button={
            <Button
              variant="ghost"
              size="icon"
              type="button"
              className="h-7 w-7 [&_svg]:h-4 [&_svg]:w-4"
              onClick={() => {
                if (!generateResult?.shellBytesBase64Str) {
                  toast.warning(
                    "Shell bytes is empty, please generate shell first",
                  );
                  return;
                }
                downloadBytes(
                  generateResult?.shellBytesBase64Str,
                  generateResult?.shellClassName,
                );
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
          header={
            <div className="text-xs">{generateResult?.injectorClassName}</div>
          }
          button={
            <Button
              variant="ghost"
              size="icon"
              type="button"
              className="h-7 w-7 [&_svg]:h-4 [&_svg]:w-4"
              onClick={() => {
                if (!generateResult?.injectorBytesBase64Str) {
                  toast.warning(
                    "Shell bytes is empty, please generate shell first",
                  );
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
    </Tabs>
  );
});

export default ShellResult;
