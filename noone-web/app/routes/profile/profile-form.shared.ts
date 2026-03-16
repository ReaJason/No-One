import type { CreateProfileRequest, Profile, ProtocolType } from "@/types/profile";

import {
  DEFAULT_REQUEST_TEMPLATES,
  DEFAULT_RESPONSE_TEMPLATES,
  type HttpProtocolConfig,
  type HttpRequestBodyType,
  type HttpResponseBodyType,
  type IdentifierConfig,
  type IdentifierLocation,
  type IdentifierOperator,
  type WebSocketProtocolConfig,
} from "@/types/profile";

export type ProfileActionData = {
  errors?: Record<string, string>;
  success?: boolean;
  values?: ProfileFormSeed;
};

export type ProfileFormSeed = {
  name: string;
  password: string;
  protocolType: ProtocolType;
  identifierLocation: string;
  identifierOperator: string;
  identifierName: string;
  identifierValue: string;
  requestMethod: string;
  requestTemplate: string;
  responseTemplate: string;
  requestBodyType: HttpRequestBodyType;
  responseBodyType: HttpResponseBodyType;
  responseStatusCode: string;
  requestHeaders: string;
  responseHeaders: string;
  messageTemplate: string;
  wsResponseTemplate: string;
  subprotocol: string;
  messageFormat: "TEXT" | "BINARY";
  handshakeHeaders: string;
  requestCompression: string;
  requestEncryption: string;
  requestEncoding: string;
  responseCompression: string;
  responseEncryption: string;
  responseEncoding: string;
};

export function getProfileFormSeed(profile?: Profile): ProfileFormSeed {
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
      httpConfig?.responseStatusCode === undefined ? "" : String(httpConfig.responseStatusCode),
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
    requestCompression: toTransformerSlot(profile?.requestTransformations, 0),
    requestEncryption: toTransformerSlot(profile?.requestTransformations, 1),
    requestEncoding: toTransformerSlot(profile?.requestTransformations, 2),
    responseCompression: toTransformerSlot(profile?.responseTransformations, 0),
    responseEncryption: toTransformerSlot(profile?.responseTransformations, 1),
    responseEncoding: toTransformerSlot(profile?.responseTransformations, 2),
  };
}

export function parseProfileFormData(
  formData: FormData,
  options: { mode: "create" | "edit" },
): {
  errors?: Record<string, string>;
  payload: Partial<CreateProfileRequest>;
  values: ProfileFormSeed;
} {
  const values: ProfileFormSeed = {
    name: readTrimmedString(formData, "name"),
    password: String(formData.get("password") ?? ""),
    protocolType: readProtocolType(formData),
    identifierLocation: readTrimmedString(formData, "identifierLocation"),
    identifierOperator: readTrimmedString(formData, "identifierOperator"),
    identifierName: readTrimmedString(formData, "identifierName"),
    identifierValue: readTrimmedString(formData, "identifierValue"),
    requestMethod: readTrimmedString(formData, "requestMethod"),
    requestTemplate: readRawString(formData, "requestTemplate"),
    responseTemplate: readRawString(formData, "responseTemplate"),
    requestBodyType: readHttpRequestBodyType(formData),
    responseBodyType: readHttpResponseBodyType(formData),
    responseStatusCode: readTrimmedString(formData, "responseStatusCode"),
    requestHeaders: readRawString(formData, "requestHeaders"),
    responseHeaders: readRawString(formData, "responseHeaders"),
    messageTemplate: readRawString(formData, "messageTemplate"),
    wsResponseTemplate: readRawString(formData, "wsResponseTemplate"),
    subprotocol: readTrimmedString(formData, "subprotocol"),
    messageFormat: readMessageFormat(formData),
    handshakeHeaders: readRawString(formData, "handshakeHeaders"),
    requestCompression: readTrimmedString(formData, "requestCompression") || "None",
    requestEncryption: readTrimmedString(formData, "requestEncryption") || "None",
    requestEncoding: readTrimmedString(formData, "requestEncoding") || "None",
    responseCompression: readTrimmedString(formData, "responseCompression") || "None",
    responseEncryption: readTrimmedString(formData, "responseEncryption") || "None",
    responseEncoding: readTrimmedString(formData, "responseEncoding") || "None",
  };

  const errors: Record<string, string> = {};
  if (!values.name) {
    errors.name = "Profile name is required";
  }
  if (options.mode === "create" && !values.password) {
    errors.password = "Password is required";
  }

  const identifier = buildIdentifier(values);
  const requestTransformations = [
    values.requestCompression,
    values.requestEncryption,
    values.requestEncoding,
  ];
  const responseTransformations = [
    values.responseCompression,
    values.responseEncryption,
    values.responseEncoding,
  ];

  let protocolConfig: HttpProtocolConfig | WebSocketProtocolConfig;

  if (values.protocolType === "HTTP") {
    const requestHeaders = parseJsonRecord(values.requestHeaders);
    const responseHeaders = parseJsonRecord(values.responseHeaders);
    if (requestHeaders.error) {
      errors.requestHeaders = requestHeaders.error;
    }
    if (responseHeaders.error) {
      errors.responseHeaders = responseHeaders.error;
    }

    let responseStatusCode: number | undefined;
    if (values.responseStatusCode) {
      responseStatusCode = Number(values.responseStatusCode);
      if (!Number.isInteger(responseStatusCode) || responseStatusCode < 0) {
        errors.responseStatusCode = "Response status code must be a non-negative integer";
      }
    }

    protocolConfig = {
      type: "HTTP",
      requestMethod: values.requestMethod
        ? (values.requestMethod as HttpProtocolConfig["requestMethod"])
        : undefined,
      requestHeaders: requestHeaders.value,
      requestTemplate: values.requestTemplate || undefined,
      requestBodyType: values.requestBodyType,
      responseStatusCode,
      responseHeaders: responseHeaders.value,
      responseBodyType: values.responseBodyType,
      responseTemplate: values.responseTemplate || undefined,
    };
  } else {
    const handshakeHeaders = parseJsonRecord(values.handshakeHeaders);
    if (handshakeHeaders.error) {
      errors.handshakeHeaders = handshakeHeaders.error;
    }

    protocolConfig = {
      type: "WEBSOCKET",
      handshakeHeaders: handshakeHeaders.value,
      subprotocol: values.subprotocol || undefined,
      messageTemplate: values.messageTemplate || undefined,
      responseTemplate: values.wsResponseTemplate || undefined,
      messageFormat: values.messageFormat,
    };
  }

  return {
    errors: Object.keys(errors).length > 0 ? errors : undefined,
    payload: {
      name: values.name,
      password: values.password || undefined,
      protocolType: values.protocolType,
      identifier,
      protocolConfig,
      requestTransformations: requestTransformations.length ? requestTransformations : null,
      responseTransformations: responseTransformations.length ? responseTransformations : null,
    },
    values,
  };
}

function buildIdentifier(values: ProfileFormSeed): IdentifierConfig | null {
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

function parseJsonRecord(raw: string) {
  if (!raw.trim()) {
    return { value: undefined as Record<string, string> | undefined };
  }

  try {
    return { value: JSON.parse(raw) as Record<string, string> };
  } catch {
    return { error: "Must be a valid JSON object" };
  }
}

function readTrimmedString(formData: FormData, key: string) {
  return String(formData.get(key) ?? "").trim();
}

function readRawString(formData: FormData, key: string) {
  return String(formData.get(key) ?? "");
}

function readProtocolType(formData: FormData): ProtocolType {
  return String(formData.get("protocolType") ?? "HTTP").toUpperCase() === "WEBSOCKET"
    ? "WEBSOCKET"
    : "HTTP";
}

function readHttpRequestBodyType(formData: FormData): HttpRequestBodyType {
  const value = String(formData.get("requestBodyType") ?? "JSON").toUpperCase();
  if (
    value === "TEXT" ||
    value === "FORM_URLENCODED" ||
    value === "MULTIPART_FORM_DATA" ||
    value === "XML" ||
    value === "BINARY"
  ) {
    return value;
  }
  return "JSON";
}

function readHttpResponseBodyType(formData: FormData): HttpResponseBodyType {
  const value = String(formData.get("responseBodyType") ?? "JSON").toUpperCase();
  if (
    value === "TEXT" ||
    value === "FORM_URLENCODED" ||
    value === "MULTIPART_FORM_DATA" ||
    value === "XML" ||
    value === "BINARY"
  ) {
    return value;
  }
  return "JSON";
}

function readMessageFormat(formData: FormData): "TEXT" | "BINARY" {
  return String(formData.get("messageFormat") ?? "TEXT").toUpperCase() === "BINARY"
    ? "BINARY"
    : "TEXT";
}

function toTransformerSlot(values: string[] | undefined, index: number) {
  return values?.[index] ?? "None";
}
