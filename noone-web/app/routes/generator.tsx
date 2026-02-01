import { LoaderCircle, WandSparklesIcon } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import type { ActionFunctionArgs } from "react-router";
import {
  Form,
  useActionData,
  useLoaderData,
  useNavigation,
} from "react-router";
import { toast } from "sonner";
import {
  generate,
  getMainConfig,
  getPackers,
  getServers,
} from "@/api/memshell-api";
import { getAllProfiles } from "@/api/profile-api";
import MainConfigCard from "@/components/memshell/main-config-card";
import PackageConfigCard from "@/components/memshell/package-config-card";
import ShellResult from "@/components/memshell/shell-result";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import { transformToPostData } from "@/lib/transformer";
import { type MemShellResult } from "@/types/memshell";

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
  urlPattern === "/" ||
  urlPattern === "/*" ||
  !urlPattern?.startsWith("/") ||
  !urlPattern;

export async function loader() {
  try {
    const [serverConfig, mainConfig, packerConfig, profiles] =
      await Promise.all([
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
    errors.urlPattern =
      "Servlet type requires a specific URL Pattern, e.g., /hello_servlet";
  }

  // Server version validation
  if (
    data.server === "TongWeb" &&
    data.shellType === "Valve" &&
    data.serverVersion === "Unknown"
  ) {
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
  const { serverConfig, mainConfig, packerConfig, profiles } =
    useLoaderData<typeof loader>();
  const actionData = useActionData<typeof action>();
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";

  const [selectedServer, setSelectedServer] = useState<string>("Tomcat");
  const [selectedShellType, setSelectedShellType] = useState<
    string | undefined
  >(undefined);

  const [generateResult, setGenerateResult] = useState<MemShellResult>();
  const [packResult, setPackResult] = useState<string | undefined>();
  const [allPackResults, setAllPackResults] = useState<
    Map<string, string> | undefined
  >();
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
        {isSubmitting ? (
          <LoaderCircle className="animate-spin" />
        ) : (
          <WandSparklesIcon />
        )}
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
          <Form method="post" className="flex flex-col xl:flex-row gap-6">
            <div className="w-full xl:w-1/2 flex flex-col gap-2">
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
            <div className="w-full xl:w-1/2 flex flex-col gap-2">
              <ShellResult
                packMethod={packMethod}
                generateResult={generateResult}
                packResult={packResult}
                allPackResults={allPackResults}
              />
            </div>
          </Form>
        </TabsContent>
        <TabsContent
          value="webshell"
          className="flex flex-1 items-center justify-center"
        >
          <Card className="w-full max-w-xl">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <WandSparklesIcon className="size-4 text-muted-foreground" />
                WebShell
              </CardTitle>
              <CardDescription>
                WebShell generator is under active development.
              </CardDescription>
              <CardAction>
                <Badge variant="secondary">Coming soon</Badge>
              </CardAction>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="text-muted-foreground">Planned capabilities:</div>
              <ul className="text-muted-foreground list-disc pl-5 space-y-1">
                <li>Quick create & connect</li>
                <li>Traffic profile & obfuscation options</li>
              </ul>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
