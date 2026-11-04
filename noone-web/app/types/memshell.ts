import type { Profile } from "@/types/profile";

export type ObfuscationScope = "ALL" | "MEMSHELL_INJECTOR" | "INJECTOR" | "MEMSHELL";
export type ProbeMethod = "ResponseBody" | "ResponseHeader";

export interface ObfuscationConfig {
  enabled: boolean;
  options?: Record<string, unknown>;
}

export interface ProbeConfig {
  probeMethod: ProbeMethod;
  respHeaderName?: string;
}

export interface ObfuscationInfo {
  enabled: boolean;
  originalShellSize?: number;
  obfuscatedShellSize?: number;
  originalInjectorSize?: number;
  obfuscatedInjectorSize?: number;
}

export interface ShellConfig {
  server: string;
  serverVersion: string;
  shellTool: string;
  shellType: string;
  targetJreVersion?: string;
  debug?: boolean;
  byPassJavaModule?: boolean;
  obfuscation?: ObfuscationConfig;
  obfuscationScope?: ObfuscationScope;
  shrink?: boolean;
  probe?: ProbeConfig;
  lambdaSuffix?: boolean;
}

export interface ShellToolConfig {
  shellClassName?: string;
  coreProfileId?: string;
  loaderProfileId?: string;
  staging?: boolean;
}

export interface GodzillaShellToolConfig {
  shellClassName?: string;
  pass?: string;
  key?: string;
  headerName?: string;
  headerValue?: string;
}

export interface NoOneShellToolConfig {
  coreProfile?: Profile;
  loaderProfile?: Profile;
  staging?: boolean;
}

export interface InjectorConfig {
  injectorClassName?: string;
  injectorHelperClassName?: string;
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

export interface MemShellGenerateResponse {
  memShellResult: MemShellResult;
  packResult?: string;
}

export interface MemShellGenerateRequest {
  shellConfig: ShellConfig;
  shellToolConfig: ShellToolConfig;
  injectorConfig: InjectorConfig;
  packer?: string;
}

export interface MemShellResult {
  shellClassName: string;
  shellSize: number;
  shellBytesBase64Str: string;
  helperClassName?: string;
  helperSize?: number;
  helperBytesBase64Str?: string;
  injectorClassName: string;
  injectorSize: number;
  injectorBytesBase64Str: string;
  shellConfig: ShellConfig;
  shellToolConfig: NoOneShellToolConfig;
  injectorConfig: InjectorConfig;
  obfuscationInfo?: ObfuscationInfo;
}

export enum ShellToolType {
  NoOne = "NoOne",
}

export interface LegacyPackerGroup {
  group: string;
  options: string[];
}

export interface PackerSchemaFieldOption {
  value: string;
  label: string;
}

export interface PackerSchemaField {
  key: string;
  type: string;
  required: boolean;
  defaultValue?: unknown;
  description?: string;
  descriptionI18nKey?: string;
  options?: PackerSchemaFieldOption[];
}

export interface PackerSchema {
  fields?: PackerSchemaField[];
  defaultConfig?: Record<string, unknown>;
}

export interface PackerEntry {
  name: string;
  outputKind?: string;
  categoryAnchor?: boolean;
  schema?: PackerSchema;
}

export interface PackerCategory {
  name: string;
  children: string[];
}

export interface LegacyPackerCategory {
  name: string;
  packers: PackerEntry[];
}

export type PackerConfig = Array<
  LegacyPackerGroup | LegacyPackerCategory | PackerCategory | string
>;
