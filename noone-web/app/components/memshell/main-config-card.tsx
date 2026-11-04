import type { Profile } from "@/types/profile";

import { ServerIcon } from "lucide-react";
import { memo, useCallback, useMemo, useState } from "react";

import { NoOneTabContent } from "@/components/memshell/tabs/noone-tab";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldContent, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
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
  type MainConfig,
  type ObfuscationScope,
  type ProbeMethod,
  type ServerConfig,
  ShellToolType,
} from "@/types/memshell";

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
  <div className="flex h-100 items-center justify-center gap-4 p-4">
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
  const shellTool = ShellToolType.NoOne;
  const [debug, setDebug] = useState(false);
  const [probe, setProbe] = useState(false);
  const [probeMethod, setProbeMethod] = useState<ProbeMethod>("ResponseBody");
  const [respHeaderName, setRespHeaderName] = useState("");
  const [byPassJavaModule, setByPassJavaModule] = useState(false);
  const [lambdaSuffix, setLambdaSuffix] = useState(false);
  const [obfuscationEnabled, setObfuscationEnabled] = useState(false);
  const [obfuscationScope, setObfuscationScope] = useState<ObfuscationScope>("ALL");

  // Optimize: Use primitive dependencies (rerender-dependencies)
  const serverToolMap = useMemo(() => {
    if (!mainConfig || !server) {
      return undefined;
    }
    return mainConfig[server];
  }, [mainConfig, server]);

  // Optimize: Memoize computed values with minimal dependencies
  const serverOptions = useMemo(() => Object.keys(servers ?? {}), [servers]);

  const shellTypes = useMemo(() => {
    if (!serverToolMap || !server) {
      return [];
    }
    return serverToolMap[shellTool] ?? [];
  }, [server, serverToolMap]);

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

  const handleByPassJavaModuleChange = useCallback((value: boolean) => {
    setByPassJavaModule(value);
  }, []);

  return (
    <>
      <Card>
        <CardHeader className="pb-1">
          <CardTitle className="text-md flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <ServerIcon className="h-5" />
              <span>Main Config</span>
            </div>
            <Badge
              variant={"outline"}
              className="bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300"
            >
              MemShellParty v3.1.0-SNAPSHOT
            </Badge>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {!mainConfig ? (
            <LoadingSpinner />
          ) : (
            <div className="flex flex-col gap-4">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <Field>
                  <FieldContent>
                    <FieldLabel htmlFor="server">Server</FieldLabel>
                    <input type="hidden" name="server" value={server as string} />
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
                <ServerVersionFormField server={server as string} error={errors?.serverVersion} />
                <JREVersionFormField
                  error={errors?.targetJdkVersion}
                  onByPassJavaModuleChange={handleByPassJavaModuleChange}
                  server={server as string}
                />
              </div>

              <input type="hidden" name="shellTool" value={shellTool} />

              {/* Options - Responsive grid: 1 col mobile, 2 cols tablet, 4 cols desktop */}
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <input type="hidden" name="debug" value={debug.toString()} />
                <div className="flex items-center gap-2">
                  <Switch id="debug" checked={debug} onCheckedChange={setDebug} />
                  <Label htmlFor="debug" className="cursor-pointer">
                    DebugMode
                  </Label>
                </div>

                <input type="hidden" name="probe" value={probe.toString()} />
                <div className="flex items-center gap-2">
                  <Switch id="probe" checked={probe} onCheckedChange={setProbe} />
                  <Label htmlFor="probe" className="cursor-pointer">
                    ProbeMode
                  </Label>
                </div>

                <input type="hidden" name="byPassJavaModule" value={byPassJavaModule.toString()} />
                <div className="flex items-center gap-2">
                  <Switch
                    id="bypass"
                    checked={byPassJavaModule}
                    onCheckedChange={setByPassJavaModule}
                  />
                  <Label htmlFor="bypass" className="cursor-pointer">
                    BypassModule
                  </Label>
                </div>

                <input type="hidden" name="lambdaSuffix" value={lambdaSuffix.toString()} />
                <div className="flex items-center gap-2">
                  <Switch
                    id="lambdaSuffix"
                    checked={lambdaSuffix}
                    onCheckedChange={setLambdaSuffix}
                  />
                  <Label htmlFor="lambdaSuffix" className="cursor-pointer">
                    LambdaSuffix
                  </Label>
                </div>

                <input
                  type="hidden"
                  name="obfuscationEnabled"
                  value={obfuscationEnabled.toString()}
                />
                <div className="flex items-center gap-2">
                  <Switch
                    id="obfuscationEnabled"
                    checked={obfuscationEnabled}
                    onCheckedChange={setObfuscationEnabled}
                  />
                  <Label htmlFor="obfuscationEnabled" className="cursor-pointer">
                    Obfuscation
                  </Label>
                </div>
              </div>

              {probe && (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <Field>
                    <FieldContent>
                      <FieldLabel htmlFor="probeMethod">Probe Method</FieldLabel>
                      <Select
                        name="probeMethod"
                        value={probeMethod}
                        onValueChange={(value) => setProbeMethod(value as ProbeMethod)}
                      >
                        <SelectTrigger className="w-full" id="probeMethod">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ResponseBody">ResponseBody</SelectItem>
                          <SelectItem value="ResponseHeader">ResponseHeader</SelectItem>
                        </SelectContent>
                      </Select>
                    </FieldContent>
                  </Field>
                  {probeMethod === "ResponseHeader" && (
                    <Field>
                      <FieldContent>
                        <FieldLabel htmlFor="respHeaderName">Response Header Name</FieldLabel>
                        <Input
                          id="respHeaderName"
                          name="respHeaderName"
                          value={respHeaderName}
                          onChange={(event) => setRespHeaderName(event.target.value)}
                          placeholder="Random X-* header when empty"
                        />
                      </FieldContent>
                    </Field>
                  )}
                </div>
              )}

              {obfuscationEnabled && (
                <Field>
                  <FieldContent>
                    <FieldLabel htmlFor="obfuscationScope">Obfuscation Scope</FieldLabel>
                    <Select
                      name="obfuscationScope"
                      value={obfuscationScope}
                      onValueChange={(value) => setObfuscationScope(value as ObfuscationScope)}
                    >
                      <SelectTrigger className="w-full" id="obfuscationScope">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="ALL">All generated classes</SelectItem>
                        <SelectItem value="MEMSHELL_INJECTOR">MemShell + Injector</SelectItem>
                        <SelectItem value="INJECTOR">Injector only</SelectItem>
                        <SelectItem value="MEMSHELL">MemShell only</SelectItem>
                      </SelectContent>
                    </Select>
                  </FieldContent>
                </Field>
              )}
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
