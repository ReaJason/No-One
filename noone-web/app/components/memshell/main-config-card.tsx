import { InfoIcon, ServerIcon } from "lucide-react";
import { memo, useCallback, useEffect, useMemo, useState } from "react";
import { NoOneTabContent } from "@/components/memshell/tabs/noone-tab";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldContent, FieldLabel } from "@/components/ui/field";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { Tabs } from "@/components/ui/tabs";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  type MainConfig,
  type ServerConfig,
  ShellToolType,
} from "@/types/memshell";
import type { Profile } from "@/types/profile";
import { JREVersionFormField } from "./jreversion-field";
import { ServerVersionFormField } from "./serverversion-field";

interface MainConfigCardProps {
  mainConfig: MainConfig | undefined;
  servers?: ServerConfig;
  errors?: Record<string, string>;
  profiles: Profile[];
  onServerChange?: (server: string) => void;
  onShellTypeChange?: (shellType: string) => void;
}

// Optimize: Extract static JSX outside component (rendering-hoist-jsx)
const LoadingSpinner = () => (
  <div className="flex items-center justify-center p-4 gap-4 h-100">
    <Spinner />
    <span className="text-sm text-muted-foreground">Loading...</span>
  </div>
);

// Optimize: Memoize component to prevent unnecessary re-renders (rerender-memo)
const MainConfigCard = memo(function MainConfigCard({
  mainConfig,
  servers,
  errors,
  profiles,
  onServerChange,
  onShellTypeChange,
}: Readonly<MainConfigCardProps>) {
  const [server, setServer] = useState<string | null>("Tomcat");
  const [shellTool, setShellTool] = useState<string>(ShellToolType.NoOne);
  const [debug, setDebug] = useState(false);
  const [probe, setProbe] = useState(false);
  const [byPassJavaModule, setByPassJavaModule] = useState(false);
  const [lambdaSuffix, setLambdaSuffix] = useState(false);

  // Optimize: Use primitive dependencies (rerender-dependencies)
  const serverToolMap = useMemo(() => {
    if (!mainConfig || !server) {
      return undefined;
    }
    return mainConfig[server];
  }, [mainConfig, server]);

  // Optimize: Memoize computed values with minimal dependencies
  const serverOptions = useMemo(() => Object.keys(servers ?? {}), [servers]);

  const shellTools = useMemo(() => {
    if (!serverToolMap) {
      return [];
    }
    const tools = Object.keys(serverToolMap).map(
      (tool) => tool as ShellToolType,
    );
    return Array.from(new Set([...tools]));
  }, [serverToolMap]);

  const shellTypes = useMemo(() => {
    if (!serverToolMap || !server) {
      return [];
    }
    return serverToolMap[shellTool] ?? [];
  }, [server, serverToolMap, shellTool]);

  useEffect(() => {
    if (!mainConfig || !server) {
      return;
    }
    const toolMap = mainConfig[server];
    if (!toolMap) {
      return;
    }
    const toolKeys = Object.keys(toolMap);
    if (toolKeys.length === 0) {
      return;
    }

    const currentShellTool = shellTool as ShellToolType;
    const nextShellTool = toolMap[currentShellTool]
      ? currentShellTool
      : (toolKeys[0] as ShellToolType);

    if (nextShellTool !== currentShellTool) {
      setShellTool(nextShellTool);
    }
  }, [mainConfig, server, shellTool]);

  // Optimize: Stable callback with useCallback (rerender-functional-setstate)
  const handleServerChange = useCallback(
    (value: string | null) => {
      setServer(value);
      if (value) {
        onServerChange?.(value);
      }
    },
    [onServerChange],
  );

  const handleShellToolChange = useCallback((v: string | null) => {
    setShellTool(v as string);
  }, []);

  const handleByPassJavaModuleChange = useCallback((value: boolean) => {
    setByPassJavaModule(value);
  }, []);

  return (
    <>
      <Card>
        <CardHeader className="pb-1">
          <CardTitle className="text-md flex items-center gap-2">
            <ServerIcon className="h-5" />
            <span>Main Config</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {!mainConfig ? (
            <LoadingSpinner />
          ) : (
            <div className="flex flex-col gap-2">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                <Field>
                  <FieldContent>
                    <FieldLabel htmlFor="server">Server</FieldLabel>
                    <input
                      type="hidden"
                      name="server"
                      value={server as string}
                    />
                    <Select onValueChange={handleServerChange} value={server}>
                      <SelectTrigger className="w-full" id="server">
                        <SelectValue placeholder="Select server" />
                      </SelectTrigger>
                      <SelectContent>
                        {serverOptions.map((serverOption) => (
                          <SelectItem key={serverOption} value={serverOption}>
                            {serverOption}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FieldContent>
                </Field>
                <ServerVersionFormField
                  server={server as string}
                  error={errors?.serverVersion}
                />
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                <Field>
                  <FieldContent>
                    <FieldLabel htmlFor="shellTool">Shell Tool</FieldLabel>
                    <input type="hidden" name="shellTool" value={shellTool} />
                    <Select
                      value={shellTool}
                      onValueChange={handleShellToolChange}
                    >
                      <SelectTrigger className="w-full" id="shellTool">
                        <SelectValue placeholder="Select shell tool" />
                      </SelectTrigger>
                      <SelectContent>
                        {shellTools.map((tool) => (
                          <SelectItem key={tool} value={tool}>
                            {tool}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FieldContent>
                </Field>
                <JREVersionFormField
                  error={errors?.targetJdkVersion}
                  onByPassJavaModuleChange={handleByPassJavaModuleChange}
                  server={server as string}
                />
              </div>
              <div className="flex gap-4 mt-4 flex-col lg:grid lg:grid-cols-2 2xl:grid">
                <input type="hidden" name="debug" value={debug.toString()} />
                <div className="flex items-center gap-2">
                  <Switch
                    id="debug"
                    checked={debug}
                    onCheckedChange={setDebug}
                  />
                  <Label htmlFor="debug">Debug</Label>
                  <Tooltip>
                    <TooltipTrigger>
                      <InfoIcon className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>Enable debug mode</p>
                    </TooltipContent>
                  </Tooltip>
                </div>
                <input type="hidden" name="probe" value={probe.toString()} />
                <div className="flex items-center gap-2">
                  <Switch
                    id="probe"
                    checked={probe}
                    onCheckedChange={setProbe}
                  />
                  <Label htmlFor="probe">Probe</Label>
                  <Tooltip>
                    <TooltipTrigger>
                      <InfoIcon className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>Enable probe mode</p>
                    </TooltipContent>
                  </Tooltip>
                </div>
                <input
                  type="hidden"
                  name="byPassJavaModule"
                  value={byPassJavaModule.toString()}
                />
                <div className="flex items-center gap-2">
                  <Switch
                    id="bypass"
                    checked={byPassJavaModule}
                    onCheckedChange={setByPassJavaModule}
                  />
                  <Label htmlFor="bypass">Bypass Java Module</Label>
                  <Tooltip>
                    <TooltipTrigger>
                      <InfoIcon className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>Bypass Java module system</p>
                    </TooltipContent>
                  </Tooltip>
                </div>
                <input
                  type="hidden"
                  name="lambdaSuffix"
                  value={lambdaSuffix.toString()}
                />
                <div className="flex items-center gap-2">
                  <Switch
                    id="lambdaSuffix"
                    checked={lambdaSuffix}
                    onCheckedChange={setLambdaSuffix}
                  />
                  <Label htmlFor="lambdaSuffix">Lambda Suffix</Label>
                  <Tooltip>
                    <TooltipTrigger>
                      <InfoIcon className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>Add lambda suffix</p>
                    </TooltipContent>
                  </Tooltip>
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
      {mainConfig && (
        <Tabs value={shellTool} className="w-full">
          <NoOneTabContent
            shellTypes={shellTypes}
            profiles={profiles}
            errors={errors}
            onShellTypeChange={onShellTypeChange}
          />
        </Tabs>
      )}
    </>
  );
});

export default MainConfigCard;
