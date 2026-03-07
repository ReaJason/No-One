import {Download, FileCode2, LoaderCircle, PlusIcon, WandSparkles, WandSparklesIcon} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ActionFunctionArgs } from "react-router";
import { Form, useActionData, useLoaderData, useNavigate, useNavigation } from "react-router";
import { toast } from "sonner";
import { generate, getMainConfig, getPackers, getServers } from "@/api/memshell-api";
import { getAllProfiles } from "@/api/profile-api";
import {
  generateWebShell,
  type WebShellFormat,
  type WebShellGenerateResponse,
  type WebShellLanguage,
} from "@/api/webshell-api";
import CodeViewer from "@/components/memshell/code-viewer";
import MainConfigCard from "@/components/memshell/main-config-card";
import PackageConfigCard from "@/components/memshell/package-config-card";
import ShellResult from "@/components/memshell/shell-result";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent, CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
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
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import { transformToPostData } from "@/lib/transformer";
import { type MemShellResult } from "@/types/memshell";
import type { Profile } from "@/types/profile";
import {Separator} from "@/components/ui/separator";
import {Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle} from "@/components/ui/empty";
import {downloadContent} from "@/lib/utils";

export const handle = createBreadcrumb(() => ({
  id: "generator",
  label: "Generator",
  to: "/generator",
}));

// Validation helper functions
const urlPatternIsNeeded = (shellType: string) => {
  if (shellType.startsWith("Agent")) {
    return false;
  }
  return (
    shellType.endsWith("Servlet") ||
    shellType.endsWith("ControllerHandler") ||
    shellType === "HandlerMethod" ||
    shellType === "HandlerFunction" ||
    shellType.endsWith("WebSocket")
  );
};

const isInvalidUrl = (urlPattern: string | undefined) =>
  urlPattern === "/" || urlPattern === "/*" || !urlPattern?.startsWith("/") || !urlPattern;

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

const DOTNET_WEBSHELL_FORMATS = new Set<WebShellFormat>(
  DOTNET_WEBSHELL_OPTIONS.map((option) => option.value),
);

const WEBSHELL_LANGUAGE_OPTIONS: Array<{ value: WebShellLanguage; label: string; desc: string }> = [
  { value: "java", label: "Java", desc: "Generate JSP or JSPX scripts" },
  { value: "dotnet", label: ".NET", desc: "Generate ASPX, ASHX, ASMX, or SOAP scripts" },
];

function getDefaultWebShellFormat(language: WebShellLanguage): WebShellFormat {
  return language === "dotnet" ? "ASPX" : "JSP";
}

function getFormatsByLanguage(language: WebShellLanguage) {
  return language === "dotnet" ? DOTNET_WEBSHELL_OPTIONS : JAVA_WEBSHELL_OPTIONS;
}

function getWebShellLanguageByFormat(format: string): WebShellLanguage {
  return DOTNET_WEBSHELL_FORMATS.has(format.toUpperCase() as WebShellFormat) ? "dotnet" : "java";
}

function getCodeViewerLanguage(format: string) {
  return getWebShellLanguageByFormat(format) === "dotnet" ? "csharp" : "java";
}

export async function loader() {
  try {
    const [serverConfig, mainConfig, packerConfig, profiles] = await Promise.all([
      getServers(),
      getMainConfig(),
      getPackers(),
      getAllProfiles(),
    ]);
    return {
      serverConfig,
      mainConfig,
      packerConfig,
      profiles: profiles,
    };
  } catch (error) {
    console.error("Failed to load config:", error);
    throw new Response("Failed to load configuration", { status: 500 });
  }
}

// Action function to handle form submission with validation
export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();

  // Extract form data
  const data = {
    server: formData.get("server") as string,
    serverVersion: formData.get("serverVersion") as string,
    targetJdkVersion: formData.get("targetJdkVersion") as string,
    debug: formData.get("debug") === "true",
    byPassJavaModule: formData.get("byPassJavaModule") === "true",
    shellClassName: formData.get("shellClassName") as string,
    shellTool: formData.get("shellTool") as string,
    shellType: formData.get("shellType") as string,
    urlPattern: formData.get("urlPattern") as string,
    godzillaPass: formData.get("godzillaPass") as string,
    godzillaKey: formData.get("godzillaKey") as string,
    headerName: formData.get("headerName") as string,
    headerValue: formData.get("headerValue") as string,
    injectorClassName: formData.get("injectorClassName") as string,
    packingMethod: formData.get("packingMethod") as string,
    lambdaSuffix: formData.get("lambdaSuffix") === "true",
    probe: formData.get("probe") === "true",
    profileId: formData.get("profileId") as string,
  };

  // Validation
  const errors: Record<string, string> = {};

  if (!data.server || data.server.length < 1) {
    errors.server = "Server is required";
  }
  if (!data.serverVersion || data.serverVersion.length < 1) {
    errors.serverVersion = "Server version is required";
  }
  if (!data.shellTool || data.shellTool.length < 1) {
    errors.shellTool = "Shell tool is required";
  }
  if (!data.shellType || data.shellType.length < 1) {
    errors.shellType = "Shell type is required";
  }
  if (!data.packingMethod || data.packingMethod.length < 1) {
    errors.packingMethod = "Packing method is required";
  }

  // URL pattern validation
  if (urlPatternIsNeeded(data.shellType) && isInvalidUrl(data.urlPattern)) {
    errors.urlPattern = "Servlet type requires a specific URL Pattern, e.g., /hello_servlet";
  }

  // Server version validation
  if (data.server === "TongWeb" && data.shellType === "Valve" && data.serverVersion === "Unknown") {
    errors.serverVersion = "serverVersion is required";
  }

  if (
    data.server === "Jetty" &&
    (data.shellType === "Handler" || data.shellType === "JakartaHandler") &&
    data.serverVersion === "Unknown"
  ) {
    errors.serverVersion = "serverVersion is required";
  }

  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  // Submit to API
  try {
    const apiResponse = await generate(transformToPostData(data));

    if (!apiResponse.success) {
      return { error: apiResponse.message, success: false };
    }

    const result = apiResponse.data;
    return {
      success: true,
      result: result.memShellResult,
      packResult: result.packResult,
      allPackResults: result.allPackResults,
      packMethod: data.packingMethod,
    };
  } catch (error) {
    return {
      error: (error as Error).message,
      success: false,
    };
  }
}

export default function Generator() {
  const { serverConfig, mainConfig, packerConfig, profiles } = useLoaderData<typeof loader>();
  const actionData = useActionData<typeof action>();
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";

  const [selectedServer, setSelectedServer] = useState<string>("Tomcat");
  const [selectedShellType, setSelectedShellType] = useState<string | undefined>(undefined);

  const [generateResult, setGenerateResult] = useState<MemShellResult>();
  const [packResult, setPackResult] = useState<string | undefined>();
  const [allPackResults, setAllPackResults] = useState<Map<string, string> | undefined>();
  const [packMethod, setPackMethod] = useState<string>("");

  // Use ref to track if we've already shown toast for this actionData
  const lastActionDataRef = useRef(actionData);

  // Optimize: Fix useEffect to prevent duplicate toasts and only fire on actual changes
  useEffect(() => {
    // Only process if actionData has actually changed
    if (actionData === lastActionDataRef.current) {
      return;
    }
    lastActionDataRef.current = actionData;

    if (actionData?.success) {
      setGenerateResult(actionData.result);
      setPackResult(actionData.packResult);
      setAllPackResults(actionData.allPackResults);
      setPackMethod(actionData.packMethod as string);
      toast.success("Generated successfully");
    } else if (actionData?.error) {
      toast.error(`Generation error: ${actionData.error}`);
    }
  }, [actionData]);

  // Optimize: Memoize callbacks to prevent unnecessary re-renders (rerender-functional-setstate)
  const handleServerChange = useCallback((server: string) => {
    setSelectedServer(server);
  }, []);

  const handleShellTypeChange = useCallback((shellType: string) => {
    setSelectedShellType(shellType);
  }, []);

  // Optimize: Memoize static JSX (rendering-hoist-jsx)
  const submitButton = useMemo(
    () => (
      <Button className="w-full" type="submit" disabled={isSubmitting}>
        {isSubmitting ? <LoaderCircle className="animate-spin" /> : <WandSparklesIcon />}
        Generate
      </Button>
    ),
    [isSubmitting],
  );

  return (
    <div className="@container/page flex flex-1 flex-col gap-8 p-6">
      <Tabs defaultValue="memshell" className="gap-6">
        <TabsList>
          <TabsTrigger value="memshell">MemShell</TabsTrigger>
          <TabsTrigger value="webshell">WebShell</TabsTrigger>
        </TabsList>
        <TabsContent value="memshell">
          <Form method="post" className="flex flex-col gap-6 xl:flex-row">
            <div className="flex w-full flex-col gap-2 xl:w-1/2">
              <MainConfigCard
                servers={serverConfig}
                mainConfig={mainConfig}
                errors={actionData?.errors}
                profiles={profiles}
                onServerChange={handleServerChange}
                onShellTypeChange={handleShellTypeChange}
              />
              <PackageConfigCard
                packerConfig={packerConfig}
                errors={actionData?.errors}
                server={selectedServer}
                shellType={selectedShellType}
              />
              {submitButton}
            </div>
            <div className="flex w-full flex-col gap-2 xl:w-1/2">
              <ShellResult
                packMethod={packMethod}
                generateResult={generateResult}
                packResult={packResult}
                allPackResults={allPackResults}
              />
            </div>
          </Form>
        </TabsContent>
        <TabsContent value="webshell">
          <WebShellPanel profiles={profiles} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function WebShellPanel({ profiles }: { profiles: Profile[] }) {
  const navigate = useNavigate();
  const [selectedProfileId, setSelectedProfileId] = useState<string>(profiles[0]?.id ?? "");
  const [language, setLanguage] = useState<WebShellLanguage>("java");
  const [format, setFormat] = useState<WebShellFormat>(getDefaultWebShellFormat("java"));
  const [servletModule, setServletModule] = useState<"JAVAX" | "JAKARTA">("JAVAX");
  const [result, setResult] = useState<WebShellGenerateResponse | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
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

  const handleGenerate = useCallback(async () => {
    if (!selectedProfileId) {
      toast.error("Please select a profile");
      return;
    }
    setIsGenerating(true);
    try {
      const response = await generateWebShell({
        profileId: selectedProfileId,
        language,
        format,
        ...(isJavaLanguage ? { servletModule } : {}),
      });
      if (response.success) {
        setResult(response.data);
        toast.success("WebShell generated successfully");
      } else {
        toast.error(response.message ?? "Generation failed");
      }
    } catch (error) {
      toast.error(`Generation error: ${(error as Error).message}`);
    } finally {
      setIsGenerating(false);
    }
  }, [selectedProfileId, language, format, servletModule, isJavaLanguage]);

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
              <RadioGroup value={language} onValueChange={handleLanguageChange} className="flex flex-col gap-2">
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
              <RadioGroup value={format} onValueChange={handleFormatChange} className="flex flex-col gap-2">
                {formatOptions.map((option) => (
                  <Label
                    key={option.value}
                    htmlFor={`fmt-${option.value.toLowerCase()}`}
                    className="flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors hover:bg-muted/50 has-[[data-state=checked]]:border-primary has-[[data-state=checked]]:bg-primary/5"
                  >
                    <RadioGroupItem value={option.value} id={`fmt-${option.value.toLowerCase()}`} />
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
                      <RadioGroupItem value={option.value} id={`sm-${option.value.toLowerCase()}`} />
                      <div className="flex flex-col gap-0.5">
                        <span className="font-medium font-mono">{option.label}</span>
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
            onClick={handleGenerate}
            disabled={isGenerating || !selectedProfileId}
            className="w-full sm:w-auto"
            size="lg"
          >
            {isGenerating ? <LoaderCircle className="size-4 animate-spin" /> : <WandSparkles className="size-4" />}
            {isGenerating ? "Generating..." : "Generate WebShell"}
          </Button>
        </CardContent>
      </Card>

      {result ? (
        <CodeViewer
          code={result.content}
          language={getCodeViewerLanguage(result.format)}
          wrapLongLines={false}
          header={<span className="text-sm font-medium text-muted-foreground">{result.fileName}</span>}
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
                  <span className="font-medium text-foreground">Generate</span> to create your WebShell file.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
