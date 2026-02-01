import type { Profile } from "@/types/profile";

export interface ShellConfig {
  server: string;
  serverVersion: string;
  shellTool: string;
  shellType: string;
  targetJreVersion?: string;
  debug?: boolean;
  byPassJavaModule?: boolean;
  obfuscate?: boolean;
  shrink?: boolean;
  probe?: boolean;
  lambdaSuffix?: boolean;
}

export interface ShellToolConfig {
  shellClassName?: string;
  godzillaPass?: string;
  godzillaKey?: string;
  headerName?: string;
  headerValue?: string;
  profileId?: string;
}

export interface GodzillaShellToolConfig {
  shellClassName?: string;
  pass?: string;
  key?: string;
  headerName?: string;
  headerValue?: string;
}

export interface NoOneShellToolConfig {
  profile?: Profile;
}

export interface InjectorConfig {
  injectorClassName?: string;
  classInheritance?: string;
  urlPattern?: string;
  staticInitialize?: boolean;
}

export interface ServerConfig {
  [serverName: string]: Array<string>;
}

export interface MainConfig {
  [serverName: string]: {
    [toolName: string]: string[];
  };
}

export type PackerConfig = Array<string>;

export interface MemShellGenerateResponse {
  memShellResult: MemShellResult;
  packResult?: string;
  allPackResults?: Map<string, string>;
}

export interface MemShellGenerateRequest {
  shellConfig: ShellConfig;
  shellToolConfig: ShellToolConfig;
  injectorConfig: InjectorConfig;
  packer: string;
}

export interface MemShellResult {
  shellClassName: string;
  shellSize: number;
  shellBytesBase64Str: string;
  injectorClassName: string;
  injectorSize: number;
  injectorBytesBase64Str: string;
  shellConfig: ShellConfig;
  shellToolConfig: NoOneShellToolConfig;
  injectorConfig: InjectorConfig;
}

export enum ShellToolType {
  NoOne = "NoOne",
}
