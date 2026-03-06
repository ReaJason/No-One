import { Download, FileCode2, LoaderCircle, WandSparklesIcon } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ActionFunctionArgs } from "react-router";
import { Form, useActionData, useLoaderData, useNavigation } from "react-router";
import { toast } from "sonner";
import { generate, getMainConfig, getPackers, getServers } from "@/api/memshell-api";
import { getAllProfiles } from "@/api/profile-api";
import { generateWebShell, type WebShellGenerateResponse } from "@/api/webshell-api";
import CodeViewer from "@/components/memshell/code-viewer";
import MainConfigCard from "@/components/memshell/main-config-card";
import PackageConfigCard from "@/components/memshell/package-config-card";
import ShellResult from "@/components/memshell/shell-result";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Field, FieldContent, FieldLabel } from "@/components/ui/field";
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
  const [selectedProfileId, setSelectedProfileId] = useState<string>(profiles[0]?.id ?? "");
  const [format, setFormat] = useState<"JSP" | "JSPX">("JSP");
  const [result, setResult] = useState<WebShellGenerateResponse | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);

  const profileOptions = useMemo(
    () => profiles.map((p) => ({ label: p.name, value: p.id })),
    [profiles],
  );

  const handleGenerate = useCallback(async () => {
    if (!selectedProfileId) {
      toast.error("Please select a profile");
      return;
    }
    setIsGenerating(true);
    try {
      const response = await generateWebShell({ profileId: selectedProfileId, format });
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
  }, [selectedProfileId, format]);

  const handleDownload = useCallback(() => {
    if (!result) return;
    const blob = new Blob([result.content], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = result.fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, [result]);

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardContent className="flex flex-wrap items-end gap-4 pt-4">
          <Field className="min-w-48 flex-1">
            <FieldContent>
              <FieldLabel htmlFor="ws-profile">Profile</FieldLabel>
              <Select
                value={selectedProfileId}
                onValueChange={(v) => v && setSelectedProfileId(v)}
                items={profileOptions}
              >
                <SelectTrigger className="w-full" id="ws-profile">
                  <SelectValue placeholder="Select profile" />
                </SelectTrigger>
                <SelectContent>
                  {profileOptions.map((p) => (
                    <SelectItem key={p.value} value={p.value}>
                      {p.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FieldContent>
          </Field>
          <Field className="w-auto">
            <FieldContent>
              <FieldLabel>Format</FieldLabel>
              <RadioGroup
                value={format}
                onValueChange={(v) => setFormat(v as "JSP" | "JSPX")}
                className="flex h-9 flex-row items-center gap-4"
              >
                <div className="flex items-center gap-2">
                  <RadioGroupItem value="JSP" id="fmt-jsp" />
                  <Label htmlFor="fmt-jsp">JSP</Label>
                </div>
                <div className="flex items-center gap-2">
                  <RadioGroupItem value="JSPX" id="fmt-jspx" />
                  <Label htmlFor="fmt-jspx">JSPX</Label>
                </div>
              </RadioGroup>
            </FieldContent>
          </Field>
          <Button onClick={handleGenerate} disabled={isGenerating} className="shrink-0">
            {isGenerating ? <LoaderCircle className="animate-spin" /> : <WandSparklesIcon />}
            Generate
          </Button>
        </CardContent>
      </Card>
      {result ? (
        <CodeViewer
          code={result.content}
          language="java"
          wrapLongLines={false}
          header={
            <span className="px-2 text-sm text-muted-foreground">{result.fileName}</span>
          }
          button={
            <Button variant="ghost" size="icon" className="h-7 w-7" onClick={handleDownload}>
              <Download className="size-4" />
            </Button>
          }
          height="70vh"
        />
      ) : (
        <Card>
          <CardContent className="py-12 text-center">
            <FileCode2 className="mx-auto mb-4 size-12 text-muted-foreground/40" />
            <p className="text-sm text-muted-foreground">
              Select a profile and format, then click Generate to create a WebShell.
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
