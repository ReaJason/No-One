// Protocol Types
export type ProtocolType = "HTTP" | "WEBSOCKET";

export type HttpMethod =
  | "POST"
  | "PUT"
  | "DELETE"
  | "PATCH"
  | "HEAD"
  | "OPTIONS";

export type HttpRequestBodyType =
  | "TEXT"
  | "FORM_URLENCODED"
  | "MULTIPART_FORM_DATA"
  | "JSON"
  | "XML"
  | "BINARY";

export type HttpResponseBodyType =
  | "TEXT"
  | "FORM_URLENCODED"
  | "MULTIPART_FORM_DATA"
  | "JSON"
  | "XML"
  | "BINARY";

export type IdentifierLocation =
  | "HEADER"
  | "COOKIE"
  | "QUERY_PARAM"
  | "BODY_FIELD"
  | "HANDSHAKE_HEADER"
  | "MESSAGE_FRAME"
  | "METADATA";

export type IdentifierOperator =
  | "EQUALS"
  | "NOT_EQUALS"
  | "CONTAINS"
  | "STARTS_WITH"
  | "ENDS_WITH"
  | "REGEX";

export type MessageFormat = "TEXT" | "BINARY";

// Identifier Config
export interface IdentifierConfig {
  location?: IdentifierLocation;
  operator?: IdentifierOperator;
  name?: string;
  value?: string;
}

// Protocol Config - Discriminated Union
export interface HttpProtocolConfig {
  type: "HTTP";
  requestMethod?: HttpMethod;
  requestHeaders?: Record<string, string>;
  requestTemplate?: string;
  requestBodyType?: HttpRequestBodyType;
  responseStatusCode?: number;
  responseHeaders?: Record<string, string>;
  responseBodyType?: HttpResponseBodyType;
  responseTemplate?: string;
}

export interface WebSocketProtocolConfig {
  type: "WEBSOCKET";
  handshakeHeaders?: Record<string, string>;
  subprotocol?: string;
  messageTemplate?: string;
  responseTemplate?: string;
  messageFormat?: MessageFormat;
}

export type ProtocolConfig = HttpProtocolConfig | WebSocketProtocolConfig;

// Profile
export interface Profile {
  id: string;
  name: string;
  protocolType: ProtocolType;
  identifier?: IdentifierConfig;
  protocolConfig: ProtocolConfig;
  requestTransformations: string[];
  responseTransformations: string[];
  createdAt: string;
  updatedAt: string;
}

// Create/Update Requests
export interface CreateProfileRequest {
  name: string;
  password: string;
  protocolType: ProtocolType;
  identifier?: IdentifierConfig | null;
  protocolConfig: ProtocolConfig;
  requestTransformations?: string[] | null;
  responseTransformations?: string[] | null;
}

export interface UpdateProfileRequest {
  name?: string;
  password?: string;
  protocolType?: ProtocolType;
  identifier?: IdentifierConfig | null;
  protocolConfig?: ProtocolConfig;
  requestTransformations?: string[] | null;
  responseTransformations?: string[] | null;
}
