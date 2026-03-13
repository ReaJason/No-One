import type { ActionFunctionArgs } from "react-router";

import { Download, FileCode2, LoaderCircle, PlusIcon, WandSparkles } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Form, useActionData, useNavigate, useNavigation } from "react-router";
import { toast } from "sonner";

import { createAuthFetch } from "@/api.server";
import {
  generateWebShell,
  type WebShellFormat,
  type WebShellGenerateRequest,
  type WebShellGenerateResponse,
  type WebShellLanguage,
} from "@/api/webshell-api";
import CodeViewer from "@/components/memshell/code-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { downloadContent } from "@/lib/utils";

import { useGeneratorContext } from "./generator-context";

const JAVA_WEBSHELL_OPTIONS: Array<{ value: WebShellFormat; label: string; desc: string }> = [
  { value: "JSP", label: "JSP", desc: "Java Server Pages" },
  { value: "JSPX", label: "JSPX", desc: "XML-based JSP" },
];

const DOTNET_WEBSHELL_OPTIONS: Array<{ value: WebShellFormat; label: string; desc: string }> = [
  { value: "ASPX", label: "ASPX", desc: "ASP.NET Web Forms" },
  { value: "ASHX", label: "ASHX", desc: "ASP.NET HTTP Handler" },
  { value: "ASMX", label: "ASMX", desc: "ASP.NET XML Web Service" },
  { value: "SOAP", label: "SOAP", desc: "SOAP-triggered Web Service variant" },
];

const NODEJS_WEBSHELL_OPTIONS: Array<{ value: WebShellFormat; label: string; desc: string }> = [
  { value: "MJS", label: "MJS", desc: "ES Module script" },
];

const DOTNET_WEBSHELL_FORMATS = new Set<WebShellFormat>(
  DOTNET_WEBSHELL_OPTIONS.map((option) => option.value),
);

const NODEJS_WEBSHELL_FORMATS = new Set<WebShellFormat>(
  NODEJS_WEBSHELL_OPTIONS.map((option) => option.value),
);

const WEBSHELL_LANGUAGE_OPTIONS: Array<{ value: WebShellLanguage; label: string; desc: string }> = [
  { value: "java", label: "Java", desc: "Generate JSP or JSPX scripts" },
  { value: "dotnet", label: ".NET", desc: "Generate ASPX, ASHX, ASMX, or SOAP scripts" },
  { value: "nodejs", label: "Node.js", desc: "Generate MJS scripts" },
];

function getDefaultWebShellFormat(language: WebShellLanguage): WebShellFormat {
  switch (language) {
    case "dotnet":
      return "ASPX";
    case "nodejs":
      return "MJS";
    default:
      return "JSP";
  }
}

function getFormatsByLanguage(language: WebShellLanguage) {
  switch (language) {
    case "dotnet":
      return DOTNET_WEBSHELL_OPTIONS;
    case "nodejs":
      return NODEJS_WEBSHELL_OPTIONS;
    default:
      return JAVA_WEBSHELL_OPTIONS;
  }
}

function getWebShellLanguageByFormat(format: string): WebShellLanguage {
  const upper = format.toUpperCase() as WebShellFormat;
  if (DOTNET_WEBSHELL_FORMATS.has(upper)) return "dotnet";
  if (NODEJS_WEBSHELL_FORMATS.has(upper)) return "nodejs";
  return "java";
}

function getCodeViewerLanguage(format: string) {
  const lang = getWebShellLanguageByFormat(format);
  if (lang === "dotnet") return "csharp";
  if (lang === "nodejs") return "javascript";
  return "java";
}

export async function action({ request, context }: ActionFunctionArgs) {
  const formData = await request.formData();

  const body: WebShellGenerateRequest = {
    profileId: formData.get("profileId") as string,
    language: formData.get("language") as WebShellLanguage,
    format: formData.get("format") as WebShellFormat,
  };
  const servletModule = formData.get("servletModule") as string | null;
  if (servletModule) {
    body.servletModule = servletModule as "JAVAX" | "JAKARTA";
  }
  try {
    const authFetch = createAuthFetch(request, context);
    const result = await generateWebShell(body, authFetch);
    return { success: true, result };
  } catch (error) {
    return { success: false, error: (error as Error).message };
  }
}

export default function WebShell() {
  const { profiles } = useGeneratorContext();
  const navigate = useNavigate();
  const actionData = useActionData<typeof action>();
  const navigation = useNavigation();
  const isGenerating = navigation.state === "submitting";
  const [selectedProfileId, setSelectedProfileId] = useState<string>(profiles[0]?.id ?? "");
  const [language, setLanguage] = useState<WebShellLanguage>("java");
  const [format, setFormat] = useState<WebShellFormat>(getDefaultWebShellFormat("java"));
  const [servletModule, setServletModule] = useState<"JAVAX" | "JAKARTA">("JAVAX");
  const [result, setResult] = useState<WebShellGenerateResponse | null>(null);
  const [targetUrl, setTargetUrl] = useState("");
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [urlError, setUrlError] = useState("");
  const isJavaLanguage = language === "java";
  const formatOptions = getFormatsByLanguage(language);

  const profileOptions = useMemo(
    () => profiles.map((p) => ({ label: p.name, value: p.id })),
    [profiles],
  );

  const generatedLanguage = result ? getWebShellLanguageByFormat(result.format) : language;

  const lastActionDataRef = useRef(actionData);

  useEffect(() => {
    if (actionData === lastActionDataRef.current) return;
    lastActionDataRef.current = actionData;

    if (actionData?.success) {
      setResult(actionData.result ?? null);
      toast.success("WebShell generated successfully");
    } else if (actionData && !actionData.success) {
      toast.error(`Generation error: ${actionData.error}`);
    }
  }, [actionData]);

  const handleLanguageChange = useCallback((nextLanguage: string) => {
    const normalizedLanguage = nextLanguage as WebShellLanguage;
    setLanguage(normalizedLanguage);
    setFormat(getDefaultWebShellFormat(normalizedLanguage));
    setResult(null);
  }, []);

  const handleFormatChange = useCallback((nextFormat: string) => {
    setFormat(nextFormat as WebShellFormat);
    setResult(null);
  }, []);

  const handleUrlChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setTargetUrl(e.target.value);
      if (urlError) setUrlError("");
    },
    [urlError],
  );

  const handleAddShell = useCallback(() => {
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

    if (!selectedProfileId) {
      toast.error("No profile selected");
      return;
    }

    const params = new URLSearchParams({
      shellUrl: targetUrl,
      profileId: selectedProfileId,
      language: generatedLanguage,
    });
    navigate(`/shells/create?${params.toString()}`);
  }, [targetUrl, selectedProfileId, generatedLanguage, navigate]);

  const handleDialogOpenChange = useCallback((open: boolean) => {
    setIsDialogOpen(open);
    if (!open) {
      setTargetUrl("");
      setUrlError("");
    }
  }, []);

  const handleDownload = useCallback(() => {
    if (!result) return;
    const blob = new Blob([result.content], {
      type: "text/plain;charset=utf-8",
    });
    return downloadContent(blob, result.fileName);
  }, [result]);

  return (
    <div className="flex flex-col gap-6">
      <Form method="post">
        <input type="hidden" name="profileId" value={selectedProfileId} />
        <input type="hidden" name="language" value={language} />
        <input type="hidden" name="format" value={format} />
        {isJavaLanguage && <input type="hidden" name="servletModule" value={servletModule} />}
        <Card>
          <CardHeader>
            <CardTitle>Configuration</CardTitle>
            <CardDescription>
              Select a profile, language, and output format to generate your WebShell
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-2">
              <Label htmlFor="ws-profile" className="text-sm font-medium">
                Profile
              </Label>
              <Select
                value={selectedProfileId}
                onValueChange={(v) => v && setSelectedProfileId(v)}
                items={profileOptions}
              >
                <SelectTrigger className="w-full max-w-sm" id="ws-profile">
                  <SelectValue placeholder="Select profile" />
                </SelectTrigger>
                <SelectContent>
                  {profileOptions.map((profile) => (
                    <SelectItem key={profile.value} value={profile.value}>
                      {profile.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <Separator />

            <div className="grid gap-6 lg:grid-cols-3">
              <div className="space-y-3">
                <Label className="text-sm font-medium">Language</Label>
                <RadioGroup
                  value={language}
                  onValueChange={handleLanguageChange}
                  className="flex flex-col gap-2"
                >
                  {WEBSHELL_LANGUAGE_OPTIONS.map((option) => (
                    <Label
                      key={option.value}
                      htmlFor={`lang-${option.value}`}
                      className="flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors hover:bg-muted/50 has-[[data-state=checked]]:border-primary has-[[data-state=checked]]:bg-primary/5"
                    >
                      <RadioGroupItem value={option.value} id={`lang-${option.value}`} />
                      <div className="flex flex-col gap-0.5">
                        <span className="font-medium">{option.label}</span>
                        <span className="text-xs text-muted-foreground">{option.desc}</span>
                      </div>
                    </Label>
                  ))}
                </RadioGroup>
              </div>

              <div className="space-y-3">
                <Label className="text-sm font-medium">Output Format</Label>
                <RadioGroup
                  value={format}
                  onValueChange={handleFormatChange}
                  className="flex flex-col gap-2"
                >
                  {formatOptions.map((option) => (
                    <Label
                      key={option.value}
                      htmlFor={`fmt-${option.value.toLowerCase()}`}
                      className="flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors hover:bg-muted/50 has-[[data-state=checked]]:border-primary has-[[data-state=checked]]:bg-primary/5"
                    >
                      <RadioGroupItem
                        value={option.value}
                        id={`fmt-${option.value.toLowerCase()}`}
                      />
                      <div className="flex flex-col gap-0.5">
                        <span className="font-medium">{option.label}</span>
                        <span className="text-xs text-muted-foreground">{option.desc}</span>
                      </div>
                    </Label>
                  ))}
                </RadioGroup>
              </div>

              {isJavaLanguage && (
                <div className="space-y-3">
                  <Label className="text-sm font-medium">Servlet Module</Label>
                  <RadioGroup
                    value={servletModule}
                    onValueChange={(v) => setServletModule(v as "JAVAX" | "JAKARTA")}
                    className="flex flex-col gap-2"
                  >
                    {[
                      {
                        value: "JAVAX",
                        label: "javax",
                        desc: "Legacy (Java EE 8 and earlier)",
                      },
                      {
                        value: "JAKARTA",
                        label: "jakarta",
                        desc: "Modern (Jakarta EE 9+)",
                      },
                    ].map((option) => (
                      <Label
                        key={option.value}
                        htmlFor={`sm-${option.value.toLowerCase()}`}
                        className="flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors hover:bg-muted/50 has-[[data-state=checked]]:border-primary has-[[data-state=checked]]:bg-primary/5"
                      >
                        <RadioGroupItem
                          value={option.value}
                          id={`sm-${option.value.toLowerCase()}`}
                        />
                        <div className="flex flex-col gap-0.5">
                          <span className="font-mono font-medium">{option.label}</span>
                          <span className="text-xs text-muted-foreground">{option.desc}</span>
                        </div>
                      </Label>
                    ))}
                  </RadioGroup>
                </div>
              )}
            </div>

            <Separator />

            <Button
              type="submit"
              disabled={isGenerating || !selectedProfileId}
              className="w-full sm:w-auto"
              size="lg"
            >
              {isGenerating ? (
                <LoaderCircle className="size-4 animate-spin" />
              ) : (
                <WandSparkles className="size-4" />
              )}
              {isGenerating ? "Generating..." : "Generate WebShell"}
            </Button>
          </CardContent>
        </Card>
      </Form>

      {result ? (
        <CodeViewer
          code={result.content}
          language={getCodeViewerLanguage(result.format)}
          wrapLongLines={false}
          header={
            <span className="text-sm font-medium text-muted-foreground">{result.fileName}</span>
          }
          button={
            <div className="flex items-center gap-1">
              <Dialog open={isDialogOpen} onOpenChange={handleDialogOpenChange}>
                <DialogTrigger
                  render={
                    <Button variant="ghost" size="sm" className="gap-1.5">
                      <PlusIcon className="size-4" />
                      Add Shell
                    </Button>
                  }
                ></DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Add Shell Connection</DialogTitle>
                  </DialogHeader>
                  <Field data-invalid={!!urlError}>
                    <FieldLabel htmlFor="ws-target-url">Shell URL *</FieldLabel>
                    <Input
                      id="ws-target-url"
                      type="url"
                      placeholder={`http://example.com/${result.fileName}`}
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
                    <DialogClose render={<Button variant="outline">Cancel</Button>}></DialogClose>
                    <Button onClick={handleAddShell}>
                      <PlusIcon className="h-4 w-4" />
                      Continue
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
              <Button variant="ghost" size="sm" onClick={handleDownload}>
                <Download className="size-4" />
              </Button>
            </div>
          }
          height="70vh"
        />
      ) : (
        <Card className="border-dashed">
          <CardContent className="py-0">
            <Empty className="border-0 py-16">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <FileCode2 className="size-5" />
                </EmptyMedia>
                <EmptyTitle>No WebShell Generated</EmptyTitle>
                <EmptyDescription>
                  Select a profile, language, and format above, then click{" "}
                  <span className="font-medium text-foreground">Generate</span> to create your
                  WebShell file.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
