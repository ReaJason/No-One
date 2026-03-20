import type { CreateProfileRequest, Profile } from "@/types/profile";

import { z } from "zod";

import {
  DEFAULT_REQUEST_TEMPLATES,
  DEFAULT_RESPONSE_TEMPLATES,
  type HttpProtocolConfig,
  type IdentifierConfig,
  type IdentifierLocation,
  type IdentifierOperator,
  type WebSocketProtocolConfig,
} from "@/types/profile";

// --- zod schema ---

const BODY_TYPES = [
  "TEXT",
  "FORM_URLENCODED",
  "MULTIPART_FORM_DATA",
  "JSON",
  "XML",
  "BINARY",
] as const;
const bodyTypeSchema = z.enum(BODY_TYPES);
const messageFormatSchema = z.enum(["TEXT", "BINARY"]);

const TRIMMED_FIELDS = [
  "name",
  "identifierLocation",
  "identifierOperator",
  "identifierName",
  "identifierValue",
  "requestMethod",
  "responseStatusCode",
  "subprotocol",
] as const;

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
  { message: "Must be a valid JSON object" },
);

export const profileFormSchema = z.object({
  name: z.string().trim().min(1, "Profile name is required"),
  password: z.string(),
  protocolType: z.enum(["HTTP", "WEBSOCKET"]),
  identifierLocation: z.string().optional(),
  identifierOperator: z.string().optional(),
  identifierName: z.string().optional(),
  identifierValue: z.string().optional(),
  // HTTP-specific fields (unregistered when protocolType is WEBSOCKET)
  requestMethod: z.string().optional(),
  requestTemplate: z.string().optional(),
  responseTemplate: z.string().optional(),
  requestBodyType: bodyTypeSchema.optional(),
  responseBodyType: bodyTypeSchema.optional(),
  responseStatusCode: z
    .string()
    .trim()
    .refine(
      (val) => {
        if (!val.trim()) return true;
        const num = Number(val);
        return Number.isInteger(num) && num >= 0;
      },
      { message: "Response status code must be a non-negative integer" },
    )
    .optional(),
  requestHeaders: jsonRecordString.optional(),
  responseHeaders: jsonRecordString.optional(),
  // WebSocket-specific fields (unregistered when protocolType is HTTP)
  messageTemplate: z.string().optional(),
  wsResponseTemplate: z.string().optional(),
  subprotocol: z.string().optional(),
  messageFormat: messageFormatSchema.optional(),
  handshakeHeaders: jsonRecordString.optional(),
  requestCompression: z.string(),
  requestEncryption: z.string(),
  requestEncoding: z.string(),
  responseCompression: z.string(),
  responseEncryption: z.string(),
  responseEncoding: z.string(),
});

export type ProfileFormValues = z.infer<typeof profileFormSchema>;

export function createProfileFormSchema(mode: "create" | "edit") {
  return profileFormSchema.refine((data) => mode !== "create" || data.password.length > 0, {
    message: "Password is required",
    path: ["password"],
  });
}

// --- types ---

export type ProfileActionData = {
  errors?: Record<string, string>;
  success?: boolean;
  values?: ProfileFormValues;
};

// --- default values ---

export function getDefaultValues(profile?: Profile): ProfileFormValues {
  const httpConfig =
    profile?.protocolConfig?.type === "HTTP"
      ? (profile.protocolConfig as HttpProtocolConfig)
      : undefined;
  const wsConfig =
    profile?.protocolConfig?.type === "WEBSOCKET"
      ? (profile.protocolConfig as WebSocketProtocolConfig)
      : undefined;

  const requestBodyType = httpConfig?.requestBodyType ?? "JSON";
  const responseBodyType = httpConfig?.responseBodyType ?? "JSON";

  return {
    name: profile?.name ?? "",
    password: "",
    protocolType: profile?.protocolType ?? "HTTP",
    identifierLocation: profile?.identifier?.location ?? "",
    identifierOperator: profile?.identifier?.operator ?? "",
    identifierName: profile?.identifier?.name ?? "",
    identifierValue: profile?.identifier?.value ?? "",
    requestMethod: httpConfig?.requestMethod ?? "POST",
    requestTemplate: httpConfig?.requestTemplate ?? DEFAULT_REQUEST_TEMPLATES[requestBodyType],
    responseTemplate: httpConfig?.responseTemplate ?? DEFAULT_RESPONSE_TEMPLATES[responseBodyType],
    requestBodyType,
    responseBodyType,
    responseStatusCode:
      httpConfig?.responseStatusCode === undefined || httpConfig?.responseStatusCode === 0
        ? ""
        : String(httpConfig.responseStatusCode),
    requestHeaders: httpConfig?.requestHeaders
      ? JSON.stringify(httpConfig.requestHeaders, null, 2)
      : "",
    responseHeaders: httpConfig?.responseHeaders
      ? JSON.stringify(httpConfig.responseHeaders, null, 2)
      : "",
    messageTemplate: wsConfig?.messageTemplate ?? "",
    wsResponseTemplate: wsConfig?.responseTemplate ?? "",
    subprotocol: wsConfig?.subprotocol ?? "",
    messageFormat: wsConfig?.messageFormat ?? "TEXT",
    handshakeHeaders: wsConfig?.handshakeHeaders
      ? JSON.stringify(wsConfig.handshakeHeaders, null, 2)
      : "",
    requestCompression: profile?.requestTransformations[0] ?? "None",
    requestEncryption: profile?.requestTransformations[1] ?? "None",
    requestEncoding: profile?.requestTransformations[2] ?? "None",
    responseCompression: profile?.responseTransformations[0] ?? "None",
    responseEncryption: profile?.responseTransformations[1] ?? "None",
    responseEncoding: profile?.responseTransformations[2] ?? "None",
  };
}

// --- payload builder ---

export function buildPayload(
  values: ProfileFormValues,
): Omit<CreateProfileRequest, "password"> & { password?: string } {
  const identifier = buildIdentifier(values);

  return {
    name: values.name.trim(),
    password: values.password || undefined,
    protocolType: values.protocolType,
    identifier,
    protocolConfig:
      values.protocolType === "HTTP"
        ? buildHttpProtocolConfig(values)
        : buildWebSocketProtocolConfig(values),
    requestTransformations: [
      values.requestCompression,
      values.requestEncryption,
      values.requestEncoding,
    ],
    responseTransformations: [
      values.responseCompression,
      values.responseEncryption,
      values.responseEncoding,
    ],
  };
}

// --- private helpers ---

function buildIdentifier(values: ProfileFormValues): IdentifierConfig | null {
  return values.identifierLocation ||
    values.identifierOperator ||
    values.identifierName ||
    values.identifierValue
    ? {
        location: values.identifierLocation as IdentifierLocation,
        operator: values.identifierOperator as IdentifierOperator,
        name: values.identifierName || undefined,
        value: values.identifierValue || undefined,
      }
    : null;
}

export function parseProfileFormData(
  formData: FormData,
  options: { mode: "create" | "edit" },
):
  | {
      errors: Record<string, string>;
      values: ProfileFormValues;
    }
  | {
      payload: Omit<CreateProfileRequest, "password"> & { password?: string };
      values: ProfileFormValues;
    } {
  const values = normalizeProfileFormValues(formData);

  const parsed = createProfileFormSchema(options.mode).safeParse(values);
  if (!parsed.success) {
    return {
      errors: toFieldErrors(parsed.error),
      values,
    };
  }

  return {
    payload: buildPayload(parsed.data),
    values: parsed.data,
  };
}

function toFieldErrors(error: z.ZodError<ProfileFormValues>) {
  const fieldErrors = error.flatten().fieldErrors;
  return Object.fromEntries(
    Object.entries(fieldErrors).flatMap(([key, messages]) =>
      messages?.[0] ? [[key, messages[0]]] : [],
    ),
  );
}

function normalizeProfileFormValues(formData: FormData): ProfileFormValues {
  const defaults = getDefaultValues();
  const values = {
    ...defaults,
    ...Object.fromEntries(
      Array.from(formData.entries(), ([key, value]) => [
        key,
        typeof value === "string" ? value : "",
      ]),
    ),
  } as ProfileFormValues;

  for (const field of TRIMMED_FIELDS) {
    trimField(values, field);
  }

  values.protocolType = normalizeProtocolType(values.protocolType);
  values.requestBodyType = normalizeBodyType(values.requestBodyType);
  values.responseBodyType = normalizeBodyType(values.responseBodyType);
  values.messageFormat = normalizeMessageFormat(values.messageFormat);
  return values;
}

function buildHttpProtocolConfig(values: ProfileFormValues): HttpProtocolConfig {
  return {
    type: "HTTP",
    requestMethod: values.requestMethod
      ? (values.requestMethod as HttpProtocolConfig["requestMethod"])
      : undefined,
    requestHeaders: parseJsonOrUndefined(values.requestHeaders),
    requestTemplate: values.requestTemplate || undefined,
    requestBodyType: values.requestBodyType,
    responseStatusCode: values.responseStatusCode ? Number(values.responseStatusCode) : undefined,
    responseHeaders: parseJsonOrUndefined(values.responseHeaders),
    responseBodyType: values.responseBodyType,
    responseTemplate: values.responseTemplate || undefined,
  };
}

function buildWebSocketProtocolConfig(values: ProfileFormValues): WebSocketProtocolConfig {
  return {
    type: "WEBSOCKET",
    handshakeHeaders: parseJsonOrUndefined(values.handshakeHeaders),
    subprotocol: values.subprotocol || undefined,
    messageTemplate: values.messageTemplate || undefined,
    responseTemplate: values.wsResponseTemplate || undefined,
    messageFormat: values.messageFormat,
  };
}

function normalizeProtocolType(value: string): ProfileFormValues["protocolType"] {
  return value === "WEBSOCKET" ? "WEBSOCKET" : "HTTP";
}

function normalizeBodyType(
  value: ProfileFormValues["requestBodyType"],
): NonNullable<ProfileFormValues["requestBodyType"]> {
  return BODY_TYPES.includes(value ?? "JSON") ? (value ?? "JSON") : "JSON";
}

function normalizeMessageFormat(
  value: ProfileFormValues["messageFormat"],
): NonNullable<ProfileFormValues["messageFormat"]> {
  return value === "BINARY" ? "BINARY" : "TEXT";
}

function trimField(values: ProfileFormValues, field: (typeof TRIMMED_FIELDS)[number]) {
  const value = values[field];
  if (typeof value === "string") {
    (values as Record<string, unknown>)[field] = value.trim();
  }
}

function parseJsonOrUndefined(raw: string | undefined): Record<string, string> | undefined {
  if (!raw?.trim()) return undefined;
  try {
    return JSON.parse(raw);
  } catch {
    return undefined;
  }
}
