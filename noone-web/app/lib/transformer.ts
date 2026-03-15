import type { InjectorConfig, ShellConfig, ShellToolConfig } from "@/types/memshell";
import type { MemShellFormSchema } from "@/types/schema";

const toOptionalValue = (value?: string) => {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
};

export function transformToPostData(formValue: MemShellFormSchema) {
  const isStagingMode = formValue.staging === true;
  const shellConfig: ShellConfig = {
    server: formValue.server,
    serverVersion: formValue.serverVersion,
    shellTool: formValue.shellTool,
    shellType: formValue.shellType,
    debug: formValue.debug,
    targetJreVersion: formValue.targetJdkVersion,
    byPassJavaModule: formValue.byPassJavaModule,
    shrink: formValue.shrink,
    lambdaSuffix: formValue.lambdaSuffix,
    probe: formValue.probe,
  };
  const shellToolConfig: ShellToolConfig = {
    shellClassName: formValue.shellClassName,
    godzillaPass: formValue.godzillaPass,
    godzillaKey: formValue.godzillaKey,
    headerName: formValue.headerName,
    headerValue: formValue.headerValue,
    coreProfileId: toOptionalValue(formValue.coreProfileId),
    staging: isStagingMode,
    loaderProfileId: isStagingMode ? toOptionalValue(formValue.loaderProfileId) : undefined,
  };

  const injectorConfig: InjectorConfig = {
    urlPattern: formValue.urlPattern,
    injectorClassName: formValue.injectorClassName,
    staticInitialize: formValue.staticInitialize,
  };
  return {
    shellConfig,
    shellToolConfig,
    injectorConfig,
    packerSpec: {
      name: formValue.packingMethod,
      config: formValue.packerCustomConfig ?? {},
    },
  };
}
