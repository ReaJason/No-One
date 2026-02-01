import type {
  InjectorConfig,
  ShellConfig,
  ShellToolConfig,
} from "@/types/memshell";
import type { MemShellFormSchema } from "@/types/schema";

export function transformToPostData(formValue: MemShellFormSchema) {
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
    profileId: formValue.profileId,
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
    packer: formValue.packingMethod,
  };
}
