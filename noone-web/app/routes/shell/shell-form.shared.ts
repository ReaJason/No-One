import type {
  CreateShellConnectionRequest,
  TestShellConfigRequest,
  UpdateShellConnectionRequest,
} from "@/api/shell-connection-api";
import type { ShellConnection, ShellLanguage } from "@/types/shell-connection";

import { z } from "zod";

export const LANGUAGE_ITEMS = [
  { label: "Java", value: "java" },
  { label: "NodeJs", value: "nodejs" },
  { label: "DotNet", value: "dotnet" },
] as const;

export const COMMON_SHELL_TYPES = [
  "Servlet",
  "JakartaServlet",
  "Filter",
  "JakartaFilter",
  "Listener",
  "JakartaListener",
  "NettyHandler",
  "Valve",
  "JakartaValve",
  "Action",
  "SpringWebFluxWebFilter",
  "SpringWebMvcInterceptor",
  "SpringWebMvcJakartaInterceptor",
];

// --- zod schema ---

const jsonRecordString = z.string().refine(
  (val) => {
    if (!val.trim()) return true;
    try {
      JSON.parse(val);
      return true;
    } catch {
      return false;
    }
  },
  { message: "Invalid JSON format" },
);

export const shellFormSchema = z.object({
  name: z.string().trim().min(1, "Name is required"),
  url: z.string().trim().min(1, "URL is required"),
  language: z.enum(["java", "nodejs", "dotnet"]),
  profileId: z.string().min(1, "Profile is required"),
  shellType: z.string(),
  interfaceName: z.string(),
  projectId: z.string(),
  staging: z.boolean(),
  loaderProfileId: z.string(),
  proxyUrl: z.string(),
  customHeaders: jsonRecordString,
  connectTimeoutMs: z.string(),
  readTimeoutMs: z.string(),
  maxRetries: z.string(),
  retryDelayMs: z.string(),
  skipSslVerify: z.boolean(),
});

export type ShellFormValues = z.infer<typeof shellFormSchema>;

export function createShellFormSchema(_mode: "create" | "edit") {
  return shellFormSchema.refine((data) => !data.staging || data.loaderProfileId.trim().length > 0, {
    message: "Loader Profile is required when staging is enabled",
    path: ["loaderProfileId"],
  });
}

// --- types ---

export type ShellActionData = {
  errors?: Record<string, string>;
  success?: boolean;
};

export type ShellFormPrefill = {
  url?: string;
  profileId?: string;
  loaderProfileId?: string;
  shellType?: string;
  staging?: boolean;
  language?: ShellLanguage;
  projectId?: number;
  interfaceName?: string;
  firstProfileId?: string;
  firstProjectId?: string;
};

// --- default values ---

export function getDefaultValues(
  shell?: ShellConnection,
  prefill?: ShellFormPrefill,
): ShellFormValues {
  if (shell) {
    return {
      name: shell.name ?? "",
      url: shell.url ?? "",
      shellType: shell.shellType ?? "",
      interfaceName: shell.interfaceName ?? "",
      staging: shell.staging ?? false,
      projectId: shell.projectId == null ? "" : String(shell.projectId),
      profileId: String(shell.profileId),
      loaderProfileId: shell.loaderProfileId == null ? "" : String(shell.loaderProfileId),
      language: shell.language,
      proxyUrl: shell.proxyUrl ?? "",
      customHeaders: shell.customHeaders ? JSON.stringify(shell.customHeaders) : "",
      connectTimeoutMs: shell.connectTimeoutMs == null ? "" : String(shell.connectTimeoutMs),
      readTimeoutMs: shell.readTimeoutMs == null ? "" : String(shell.readTimeoutMs),
      skipSslVerify: shell.skipSslVerify ?? false,
      maxRetries: shell.maxRetries == null ? "" : String(shell.maxRetries),
      retryDelayMs: shell.retryDelayMs == null ? "" : String(shell.retryDelayMs),
    };
  }

  const firstProfileId = prefill?.firstProfileId;
  const firstProjectId = prefill?.firstProjectId;
  const profileId = prefill?.profileId || (firstProfileId != null ? String(firstProfileId) : "");

  return {
    name: "",
    url: prefill?.url ?? "",
    shellType: prefill?.shellType ?? "",
    interfaceName: prefill?.interfaceName ?? "",
    staging: prefill?.staging ?? false,
    projectId:
      prefill?.projectId != null
        ? String(prefill.projectId)
        : firstProjectId != null
          ? String(firstProjectId)
          : "",
    profileId,
    loaderProfileId: prefill?.loaderProfileId || profileId,
    language: prefill?.language ?? "java",
    proxyUrl: "",
    customHeaders: "",
    connectTimeoutMs: "",
    readTimeoutMs: "",
    skipSslVerify: false,
    maxRetries: "",
    retryDelayMs: "",
  };
}

// --- submit data helper ---

export function toSubmitData(values: ShellFormValues): Record<string, string> {
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, String(value)]));
}

// --- payload builders ---

export function buildCreatePayload(values: ShellFormValues): CreateShellConnectionRequest {
  return {
    name: values.name,
    url: values.url,
    staging: values.staging || undefined,
    shellType: values.shellType.trim() || undefined,
    interfaceName: values.interfaceName.trim() || undefined,
    language: values.language,
    projectId: parseFiniteNumber(values.projectId),
    profileId: parseFiniteNumber(values.profileId)!,
    loaderProfileId: values.staging ? parseFiniteNumber(values.loaderProfileId) : undefined,
    proxyUrl: values.proxyUrl.trim() || undefined,
    customHeaders: parseJsonOrUndefined(values.customHeaders),
    connectTimeoutMs: parseFiniteNumber(values.connectTimeoutMs),
    readTimeoutMs: parseFiniteNumber(values.readTimeoutMs),
    skipSslVerify: values.skipSslVerify || undefined,
    maxRetries: parseFiniteNumber(values.maxRetries),
    retryDelayMs: parseFiniteNumber(values.retryDelayMs),
  };
}

export function buildUpdatePayload(values: ShellFormValues): UpdateShellConnectionRequest {
  return {
    name: values.name,
    url: values.url,
    staging: values.staging,
    shellType: values.shellType.trim() || null,
    interfaceName: values.interfaceName.trim() || null,
    language: values.language,
    projectId: values.projectId.trim() ? parseFiniteNumber(values.projectId) : null,
    profileId: parseFiniteNumber(values.profileId)!,
    loaderProfileId: values.staging ? (parseFiniteNumber(values.loaderProfileId) ?? null) : null,
    proxyUrl: values.proxyUrl.trim() || undefined,
    customHeaders: parseJsonOrUndefined(values.customHeaders),
    connectTimeoutMs: parseFiniteNumber(values.connectTimeoutMs),
    readTimeoutMs: parseFiniteNumber(values.readTimeoutMs),
    skipSslVerify: values.skipSslVerify || undefined,
    maxRetries: parseFiniteNumber(values.maxRetries),
    retryDelayMs: parseFiniteNumber(values.retryDelayMs),
  };
}

export function buildTestConfigPayload(values: ShellFormValues): TestShellConfigRequest {
  return {
    url: values.url,
    staging: values.staging || undefined,
    shellType: values.shellType.trim() || undefined,
    interfaceName: values.interfaceName.trim() || undefined,
    language: values.language,
    profileId: parseFiniteNumber(values.profileId)!,
    loaderProfileId: values.staging ? parseFiniteNumber(values.loaderProfileId) : undefined,
    proxyUrl: values.proxyUrl.trim() || undefined,
    customHeaders: parseJsonOrUndefined(values.customHeaders),
    connectTimeoutMs: parseFiniteNumber(values.connectTimeoutMs),
    readTimeoutMs: parseFiniteNumber(values.readTimeoutMs),
    skipSslVerify: values.skipSslVerify || undefined,
    maxRetries: parseFiniteNumber(values.maxRetries),
    retryDelayMs: parseFiniteNumber(values.retryDelayMs),
  };
}

// --- form data parsers (used in route action) ---

export function parseShellFormData(
  formData: FormData,
  options: { mode: "create" | "edit" },
):
  | { errors: Record<string, string>; values: ShellFormValues }
  | {
      payload: CreateShellConnectionRequest | UpdateShellConnectionRequest;
      values: ShellFormValues;
    } {
  const values = normalizeFormData(formData);

  const parsed = createShellFormSchema(options.mode).safeParse(values);
  if (!parsed.success) {
    return { errors: toFieldErrors(parsed.error), values };
  }

  const payload =
    options.mode === "edit" ? buildUpdatePayload(parsed.data) : buildCreatePayload(parsed.data);

  return { payload, values: parsed.data };
}

export function parseTestConfigFormData(
  formData: FormData,
): { errors: Record<string, string> } | { payload: TestShellConfigRequest } {
  const values = normalizeFormData(formData);

  if (!values.url.trim() || !values.profileId.trim()) {
    return { errors: { general: "URL and Profile are required to test connection" } };
  }

  if (values.staging && !values.loaderProfileId.trim()) {
    return { errors: { general: "Loader Profile is required when staging is enabled" } };
  }

  if (values.customHeaders.trim()) {
    try {
      JSON.parse(values.customHeaders);
    } catch {
      return { errors: { general: "Invalid JSON format for custom headers" } };
    }
  }

  return { payload: buildTestConfigPayload(values) };
}

// --- private helpers ---

function normalizeFormData(formData: FormData): ShellFormValues {
  const get = (name: string) => {
    const val = formData.get(name);
    return typeof val === "string" ? val : "";
  };

  return {
    name: get("name"),
    url: get("url"),
    language: normalizeLanguage(get("language")),
    profileId: get("profileId"),
    shellType: get("shellType"),
    interfaceName: get("interfaceName"),
    projectId: get("projectId"),
    staging: get("staging") === "true",
    loaderProfileId: get("loaderProfileId"),
    proxyUrl: get("proxyUrl"),
    customHeaders: get("customHeaders"),
    connectTimeoutMs: get("connectTimeoutMs"),
    readTimeoutMs: get("readTimeoutMs"),
    maxRetries: get("maxRetries"),
    retryDelayMs: get("retryDelayMs"),
    skipSslVerify: get("skipSslVerify") === "true",
  };
}

function normalizeLanguage(value: string): ShellLanguage {
  if (value === "nodejs" || value === "dotnet") return value;
  return "java";
}

function toFieldErrors(error: z.ZodError<ShellFormValues>) {
  const fieldErrors = error.flatten().fieldErrors;
  return Object.fromEntries(
    Object.entries(fieldErrors).flatMap(([key, messages]) =>
      messages?.[0] ? [[key, messages[0]]] : [],
    ),
  );
}

function parseFiniteNumber(raw: string | undefined): number | undefined {
  if (!raw?.trim()) return undefined;
  const value = Number(raw);
  return Number.isFinite(value) ? value : undefined;
}

function parseJsonOrUndefined(raw: string | undefined): Record<string, string> | undefined {
  if (!raw?.trim()) return undefined;
  try {
    return JSON.parse(raw);
  } catch {
    return undefined;
  }
}
