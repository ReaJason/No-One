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

// Default Templates
export const DEFAULT_REQUEST_TEMPLATES: Record<HttpRequestBodyType, string> = {
  FORM_URLENCODED: "username=admin&action=login&q={{payload}}&token=123456",
  TEXT: "hello{{payload}}world",
  MULTIPART_FORM_DATA: `--{{boundary}}
Content-Disposition: form-data; name="username"

admin
--{{boundary}}
Content-Disposition: form-data; name="file"; filename="test.png"
Content-Type: image/png

<hex>89504E470D0A1A0A0000000D4948445200000001000000010802000000907753DE0000000C49444154789C63F8CFC000000301010018DD8D000000000049454E44AE426082</hex>{{payload}}
--{{boundary}}--`,
  JSON: `{"hello": "{{payload}}"}`,
  XML: "<hello>{{payload}}</hello>",
  BINARY: "<base64>aGVsbG8=</base64>{{payload}}",
};

export const DEFAULT_RESPONSE_TEMPLATES: Record<HttpResponseBodyType, string> =
  {
    FORM_URLENCODED: "username=admin&action=login&q={{payload}}&token=123456",
    TEXT: "hello{{payload}}world",
    MULTIPART_FORM_DATA: `--{{boundary}}
Content-Disposition: form-data; name="username"

admin
--{{boundary}}
Content-Disposition: form-data; name="file"; filename="test.png"
Content-Type: image/png

<hex>89504E470D0A1A0A0000000D4948445200000001000000010802000000907753DE0000000C49444154789C63F8CFC000000301010018DD8D000000000049454E44AE426082</hex>{{payload}}
--{{boundary}}--`,
    JSON: `{"hello": "{{payload}}"}`,
    XML: "<hello>{{payload}}</hello>",
    BINARY: "<base64>aGVsbG8=</base64>{{payload}}",
  };

// Select Options
export const ENCRYPTION_OPTIONS = [
  { label: "None", value: "None" },
  { label: "XOR", value: "XOR" },
  { label: "AES", value: "AES" },
  { label: "TripleDES", value: "TripleDES" },
];

export const COMPRESSION_OPTIONS = [
  { label: "None", value: "None" },
  { label: "Gzip", value: "Gzip" },
  { label: "Deflate", value: "Deflate" },
  { label: "LZ4", value: "LZ4" },
];

export const ENCODING_OPTIONS = [
  { label: "None", value: "None" },
  { label: "Base64", value: "Base64" },
  { label: "Hex", value: "Hex" },
  { label: "BigInteger", value: "BigInteger" },
];

export const IDENTIFIER_OPERATOR_OPTIONS = [
  { label: "Equals", value: "EQUALS" },
  { label: "Contains", value: "CONTAINS" },
  { label: "Starts With", value: "STARTS_WITH" },
  { label: "Ends With", value: "ENDS_WITH" },
  { label: "Regex", value: "REGEX" },
];

export const HTTP_IDENTIFIER_LOCATION_OPTIONS = [
  { label: "Header", value: "HEADER" },
  { label: "Cookie", value: "COOKIE" },
  { label: "Query Parameter", value: "QUERY_PARAM" },
];

export const WEBSOCKET_IDENTIFIER_LOCATION_OPTIONS = [
  { label: "Handshake Header", value: "HANDSHAKE_HEADER" },
  { label: "Message Frame", value: "MESSAGE_FRAME" },
];

export const REQUEST_BODY_TYPE_OPTIONS = [
  { value: "JSON", label: "JSON" },
  { value: "XML", label: "XML" },
  { value: "FORM_URLENCODED", label: "Form URL Encoded" },
  { value: "MULTIPART_FORM_DATA", label: "Multipart Form Data" },
  { value: "BINARY", label: "Binary" },
  { value: "TEXT", label: "Text" },
];

export const RESPONSE_BODY_TYPE_OPTIONS = [
  { value: "JSON", label: "JSON" },
  { value: "XML", label: "XML" },
  { value: "BINARY", label: "Binary" },
  { value: "TEXT", label: "Text" },
];

export const REQUEST_METHOD_OPTIONS = [
  { value: "POST", label: "POST" },
  { value: "PUT", label: "PUT" },
  { value: "DELETE", label: "DELETE" },
  { value: "PATCH", label: "PATCH" },
];
