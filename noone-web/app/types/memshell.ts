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

export interface MemShellGenerateResponse {
  memShellResult: MemShellResult;
  packResult?: string;
}

export interface MemShellGenerateRequest {
  shellConfig: ShellConfig;
  shellToolConfig: ShellToolConfig;
  injectorConfig: InjectorConfig;
  packer?: string;
  packerSpec?: {
    name: string;
    config: Record<string, unknown>;
  };
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
  packers: PackerEntry[];
}

export type PackerConfig = Array<LegacyPackerGroup | PackerCategory | string>;
