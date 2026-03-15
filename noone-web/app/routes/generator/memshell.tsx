import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { LoaderCircle, WandSparklesIcon } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Form, useActionData, useLoaderData, useNavigation } from "react-router";
import { toast } from "sonner";

import { createAuthFetch } from "@/api.server";
import { generate, getMainConfig, getPackers, getServers } from "@/api/memshell-api";
import MainConfigCard from "@/components/memshell/main-config-card";
import PackageConfigCard from "@/components/memshell/package-config-card";
import ShellResult from "@/components/memshell/shell-result";
import AddShellButton from "@/components/shell/add-shell-button";
import { Button } from "@/components/ui/button";
import { transformToPostData } from "@/lib/transformer";
import { type MemShellResult } from "@/types/memshell";
import { type MemShellFormSchema } from "@/types/schema";

import { useGeneratorContext } from "./generator-context";

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

export async function loader({ request, context }: LoaderFunctionArgs) {
  try {
    const authFetch = createAuthFetch(request, context);
    const [serverConfig, mainConfig, packerConfig] = await Promise.all([
      getServers(authFetch),
      getMainConfig(authFetch),
      getPackers(authFetch),
    ]);
    return { serverConfig, mainConfig, packerConfig };
  } catch (error) {
    console.error("Failed to load config:", error);
    throw new Response("Failed to load configuration", { status: 500 });
  }
}

export async function action({ request, context }: ActionFunctionArgs) {
  const formData = await request.formData();

  // Extract custom packer config dynamically
  const packerCustomConfig: Record<string, any> = {};
  for (const [key, value] of formData.entries()) {
    if (key.startsWith("packerCustomConfig.")) {
      const configKey = key.replace("packerCustomConfig.", "");
      if (typeof value === "string") {
        if (value === "true") {
          packerCustomConfig[configKey] = true;
        } else if (value === "false") {
          packerCustomConfig[configKey] = false;
        } else {
          const num = Number(value);
          if (!Number.isNaN(num) && value !== "") {
            packerCustomConfig[configKey] = num;
          } else {
            packerCustomConfig[configKey] = value;
          }
        }
      }
    }
  }

  // Extract form data
  const data: MemShellFormSchema = {
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
    packerCustomConfig: Object.keys(packerCustomConfig).length > 0 ? packerCustomConfig : undefined,
    lambdaSuffix: formData.get("lambdaSuffix") === "true",
    probe: formData.get("probe") === "true",
    coreProfileId: (formData.get("coreProfileId") as string | null) ?? undefined,
    loaderProfileId: (formData.get("loaderProfileId") as string | null) ?? undefined,
    staging: formData.get("staging") === "true",
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
    const authFetch = createAuthFetch(request, context);
    const result = await generate(transformToPostData(data), authFetch);
    return {
      success: true,
      result: result.memShellResult,
      packResult: result.packResult,
      packMethod: data.packingMethod,
    };
  } catch (error) {
    return {
      error: (error as Error).message,
      success: false,
    };
  }
}

export default function MemShell() {
  const { serverConfig, mainConfig, packerConfig } = useLoaderData<typeof loader>();
  const { profiles } = useGeneratorContext();
  const actionData = useActionData<typeof action>();
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";

  const [selectedServer, setSelectedServer] = useState<string>("Tomcat");
  const [selectedShellType, setSelectedShellType] = useState<string | undefined>(undefined);

  const [generateResult, setGenerateResult] = useState<MemShellResult>();
  const [packResult, setPackResult] = useState<string | undefined>();
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

  const addShellParams = useMemo(() => {
    if (!generateResult) return null;
    const loaderProfileId = generateResult.shellToolConfig.loaderProfile?.id;
    const isStaging = Boolean(loaderProfileId);
    return {
      profileId: isStaging
        ? undefined
        : (generateResult.shellToolConfig.coreProfile?.id ?? undefined),
      loaderProfileId: isStaging ? (loaderProfileId ?? undefined) : undefined,
      shellType: generateResult.shellConfig.shellType ?? undefined,
      staging: isStaging || undefined,
    };
  }, [generateResult]);

  return (
    <Form method="post" className="flex flex-col gap-6">
      <div className="flex w-full flex-col gap-2">
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
        <div className="flex gap-2">
          <Button className="flex-1" type="submit" disabled={isSubmitting}>
            {isSubmitting ? <LoaderCircle className="animate-spin" /> : <WandSparklesIcon />}
            Generate
          </Button>
          {addShellParams && <AddShellButton params={addShellParams} />}
        </div>
      </div>
      <div className="flex w-full flex-col gap-2">
        <ShellResult
          packMethod={packMethod}
          generateResult={generateResult}
          packResult={packResult}
        />
      </div>
    </Form>
  );
}
