import { ArrowLeft, Edit } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import {
  Form,
  redirect,
  useActionData,
  useLoaderData,
  useNavigate,
} from "react-router";
import { toast } from "sonner";
import { getProfileById, updateProfile } from "@/api/profile-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  COMPRESSION_OPTIONS,
  DEFAULT_REQUEST_TEMPLATES,
  DEFAULT_RESPONSE_TEMPLATES,
  ENCODING_OPTIONS,
  ENCRYPTION_OPTIONS,
  HTTP_IDENTIFIER_LOCATION_OPTIONS,
  type HttpProtocolConfig,
  type HttpRequestBodyType,
  type HttpResponseBodyType,
  IDENTIFIER_OPERATOR_OPTIONS,
  type IdentifierConfig,
  type IdentifierLocation,
  type IdentifierOperator,
  type Profile,
  type ProtocolType,
  REQUEST_BODY_TYPE_OPTIONS,
  REQUEST_METHOD_OPTIONS,
  RESPONSE_BODY_TYPE_OPTIONS,
  WEBSOCKET_IDENTIFIER_LOCATION_OPTIONS,
  type WebSocketProtocolConfig,
} from "@/types/profile";

export async function loader({ params }: LoaderFunctionArgs) {
  const profileId = params.profileId as string | undefined;
  if (!profileId) throw new Response("Invalid profile ID", { status: 400 });
  const profile = await getProfileById(profileId);
  if (!profile) throw new Response("Profile not found", { status: 404 });
  return { profile };
}

export async function action({ request, params }: ActionFunctionArgs) {
  const profileId = params.profileId as string | undefined;
  if (!profileId) throw new Response("Invalid profile ID", { status: 400 });

  const formData = await request.formData();
  const name = (formData.get("name") as string)?.trim();
  const password = (formData.get("password") as string) || undefined;
  const protocolType = (formData.get("protocolType") as ProtocolType) || "HTTP";

  // Identifier
  const identifierLocation =
    (formData.get("identifierLocation") as IdentifierLocation) || undefined;
  const identifierOperator =
    (formData.get("identifierOperator") as IdentifierOperator) || undefined;
  const identifierName =
    (formData.get("identifierName") as string) || undefined;
  const identifierValue =
    (formData.get("identifierValue") as string) || undefined;

  const identifier: IdentifierConfig | null =
    identifierLocation ||
    identifierOperator ||
    identifierName ||
    identifierValue
      ? {
          location: identifierLocation,
          operator: identifierOperator,
          name: identifierName,
          value: identifierValue,
        }
      : null;

  // Build transformations from 3 dropdowns each
  const reqEncryption = (formData.get("requestEncryption") as string) || "none";
  const reqCompression =
    (formData.get("requestCompression") as string) || "none";
  const reqEncoding = (formData.get("requestEncoding") as string) || "none";
  const resEncryption =
    (formData.get("responseEncryption") as string) || "none";
  const resCompression =
    (formData.get("responseCompression") as string) || "none";
  const resEncoding = (formData.get("responseEncoding") as string) || "none";

  const requestTransformations = [
    reqCompression,
    reqEncryption,
    reqEncoding,
  ].filter((v) => v && v !== "none");
  const responseTransformations = [
    resCompression,
    resEncryption,
    resEncoding,
  ].filter((v) => v && v !== "none");

  const errors: Record<string, string> = {};
  if (!name) errors.name = "Profile name is required";

  // Build protocol config based on type
  let protocolConfig: HttpProtocolConfig | WebSocketProtocolConfig;

  if (protocolType === "HTTP") {
    const requestMethod =
      (formData.get("requestMethod") as string) || undefined;
    const requestTemplate =
      (formData.get("requestTemplate") as string) || undefined;
    const responseTemplate =
      (formData.get("responseTemplate") as string) || undefined;
    const requestBodyType =
      (formData.get("requestBodyType") as HttpRequestBodyType) ||
      "FORM_URLENCODED";
    const responseBodyType =
      (formData.get("responseBodyType") as HttpResponseBodyType) || "TEXT";
    const responseStatusCodeRaw =
      (formData.get("responseStatusCode") as string) || "";
    const responseStatusCode = responseStatusCodeRaw
      ? Number(responseStatusCodeRaw)
      : undefined;

    let requestHeaders: Record<string, string> | undefined;
    let responseHeaders: Record<string, string> | undefined;

    const requestHeadersJson = (formData.get("requestHeaders") as string) || "";
    const responseHeadersJson =
      (formData.get("responseHeaders") as string) || "";

    if (requestHeadersJson) {
      try {
        requestHeaders = JSON.parse(requestHeadersJson);
      } catch {
        errors.requestHeaders = "Request headers must be valid JSON object";
      }
    }
    if (responseHeadersJson) {
      try {
        responseHeaders = JSON.parse(responseHeadersJson);
      } catch {
        errors.responseHeaders = "Response headers must be valid JSON object";
      }
    }
    if (
      responseStatusCode !== undefined &&
      (!Number.isInteger(responseStatusCode) || responseStatusCode < 0)
    ) {
      errors.responseStatusCode =
        "Response status code must be a non-negative integer";
    }

    protocolConfig = {
      type: "HTTP",
      requestMethod: requestMethod as HttpProtocolConfig["requestMethod"],
      requestHeaders,
      requestTemplate,
      requestBodyType,
      responseStatusCode,
      responseHeaders,
      responseBodyType,
      responseTemplate,
    };
  } else {
    // WebSocket
    const messageTemplate =
      (formData.get("messageTemplate") as string) || undefined;
    const responseTemplate =
      (formData.get("wsResponseTemplate") as string) || undefined;
    const subprotocol = (formData.get("subprotocol") as string) || undefined;
    const messageFormat =
      (formData.get("messageFormat") as "TEXT" | "BINARY") || "TEXT";

    let handshakeHeaders: Record<string, string> | undefined;
    const handshakeHeadersJson =
      (formData.get("handshakeHeaders") as string) || "";

    if (handshakeHeadersJson) {
      try {
        handshakeHeaders = JSON.parse(handshakeHeadersJson);
      } catch {
        errors.handshakeHeaders = "Handshake headers must be valid JSON object";
      }
    }

    protocolConfig = {
      type: "WEBSOCKET",
      handshakeHeaders,
      subprotocol,
      messageTemplate,
      responseTemplate,
      messageFormat,
    };
  }

  if (Object.keys(errors).length > 0) return { errors, success: false };

  try {
    await updateProfile(profileId, {
      name,
      password,
      protocolType,
      identifier,
      protocolConfig,
      requestTransformations: requestTransformations.length
        ? requestTransformations
        : null,
      responseTransformations: responseTransformations.length
        ? responseTransformations
        : null,
    });
    toast.success("Profile updated successfully");
    return redirect("/profiles");
  } catch (error: any) {
    toast.error(error?.message || "Failed to update profile");
    return {
      errors: { general: error?.message || "Failed to update profile" },
      success: false,
    };
  }
}

export const handle = createBreadcrumb((match) => ({
  id: "profiles-edit",
  label: "Edit Profile",
  to: `/profiles/edit/${match.params.profileId}`,
}));

export default function EditProfile() {
  const { profile } = useLoaderData() as { profile: Profile };
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();

  const httpConfig =
    profile.protocolConfig?.type === "HTTP"
      ? (profile.protocolConfig as HttpProtocolConfig)
      : null;
  const wsConfig =
    profile.protocolConfig?.type === "WEBSOCKET"
      ? (profile.protocolConfig as WebSocketProtocolConfig)
      : null;

  const initialRequestBodyType: HttpRequestBodyType =
    httpConfig?.requestBodyType || "FORM_URLENCODED";
  const initialResponseBodyType: HttpResponseBodyType =
    httpConfig?.responseBodyType || "TEXT";

  const [protocolType, setProtocolType] = useState<ProtocolType>(
    profile.protocolType,
  );
  const [method, setMethod] = useState<string | undefined>(
    httpConfig?.requestMethod,
  );
  const [requestBodyType, setRequestBodyType] = useState<HttpRequestBodyType>(
    initialRequestBodyType,
  );
  const [responseBodyType, setResponseBodyType] =
    useState<HttpResponseBodyType>(initialResponseBodyType);
  const [identifierLocation, setIdentifierLocation] = useState<string>(
    profile.identifier?.location || "",
  );
  const [identifierOperator, setIdentifierOperator] = useState<string>(
    profile.identifier?.operator || "",
  );
  const [messageFormat, setMessageFormat] = useState<"TEXT" | "BINARY">(
    wsConfig?.messageFormat || "TEXT",
  );

  const [requestTemplate, setRequestTemplate] = useState<string>(
    httpConfig?.requestTemplate ||
      DEFAULT_REQUEST_TEMPLATES[initialRequestBodyType],
  );
  const [responseTemplate, setResponseTemplate] = useState<string>(
    httpConfig?.responseTemplate ||
      DEFAULT_RESPONSE_TEMPLATES[initialResponseBodyType],
  );

  const previousRequestBodyType = useRef<HttpRequestBodyType>(requestBodyType);
  const previousResponseBodyType =
    useRef<HttpResponseBodyType>(responseBodyType);

  useEffect(() => {
    const previous = previousRequestBodyType.current;
    const previousDefault = DEFAULT_REQUEST_TEMPLATES[previous];
    if (requestTemplate.trim() === "" || requestTemplate === previousDefault) {
      setRequestTemplate(DEFAULT_REQUEST_TEMPLATES[requestBodyType]);
    }
    previousRequestBodyType.current = requestBodyType;
  }, [requestBodyType, requestTemplate]);

  useEffect(() => {
    const previous = previousResponseBodyType.current;
    const previousDefault = DEFAULT_RESPONSE_TEMPLATES[previous];
    if (
      responseTemplate.trim() === "" ||
      responseTemplate === previousDefault
    ) {
      setResponseTemplate(DEFAULT_RESPONSE_TEMPLATES[responseBodyType]);
    }
    previousResponseBodyType.current = responseBodyType;
  }, [responseBodyType, responseTemplate]);

  const [reqCompression, setReqCompression] = useState<string>(
    profile.requestTransformations[0] || "None",
  );
  const [reqEncryption, setReqEncryption] = useState<string>(
    profile.requestTransformations[1] || "None",
  );
  const [reqEncoding, setReqEncoding] = useState<string>(
    profile.requestTransformations[2] || "None",
  );
  const [resCompression, setResCompression] = useState<string>(
    profile.responseTransformations[0] || "None",
  );
  const [resEncryption, setResEncryption] = useState<string>(
    profile.responseTransformations[1] || "None",
  );
  const [resEncoding, setResEncoding] = useState<string>(
    profile.responseTransformations[2] || "None",
  );

  const identifierLocationOptions =
    protocolType === "HTTP"
      ? HTTP_IDENTIFIER_LOCATION_OPTIONS
      : WEBSOCKET_IDENTIFIER_LOCATION_OPTIONS;

  return (
    <div className="container mx-auto p-6 max-w-6xl">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={() => navigate("/profiles")}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to profile list
        </Button>

        <h1 className="text-3xl font-bold text-balance">Edit Profile</h1>
        <p className="text-muted-foreground mt-2">
          Update profile: <span className="font-semibold">{profile.name}</span>
        </p>
      </div>

      <Form method="post" className="space-y-6">
        {actionData?.errors?.general && (
          <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
            {actionData.errors.general}
          </div>
        )}

        {/* Basic Info */}
        <Card>
          <CardHeader>
            <CardTitle>Basic Info</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Field data-invalid={!!actionData?.errors?.name}>
                <FieldLabel htmlFor="name">Name</FieldLabel>
                <Input
                  id="name"
                  name="name"
                  type="text"
                  required
                  defaultValue={profile.name}
                  placeholder="Profile unique name"
                />
                <FieldError>{actionData?.errors?.name}</FieldError>
              </Field>
              <Field data-invalid={!!actionData?.errors?.password}>
                <FieldLabel htmlFor="password">Password</FieldLabel>
                <Input
                  id="password"
                  name="password"
                  type="password"
                  placeholder="Leave blank to keep unchanged"
                />
                <FieldError>{actionData?.errors?.password}</FieldError>
              </Field>
              <Field>
                <FieldLabel>Protocol Type</FieldLabel>
                <input type="hidden" name="protocolType" value={protocolType} />
                <Select
                  value={protocolType}
                  onValueChange={(v) => setProtocolType(v as ProtocolType)}
                  items={[
                    { value: "HTTP", label: "HTTP" },
                    { value: "WEBSOCKET", label: "WebSocket" },
                  ]}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select protocol" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="HTTP">HTTP</SelectItem>
                      <SelectItem value="WEBSOCKET">WebSocket</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            </div>
          </CardContent>
        </Card>

        {/* Identifier Config */}
        <Card>
          <CardHeader>
            <CardTitle>Identifier Config</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <Field>
                <FieldLabel>Location</FieldLabel>
                <input
                  type="hidden"
                  name="identifierLocation"
                  value={identifierLocation}
                />
                <Select
                  value={identifierLocation}
                  onValueChange={(v) => setIdentifierLocation(v ?? "")}
                  items={identifierLocationOptions}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select location" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {identifierLocationOptions.map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel>Operator</FieldLabel>
                <input
                  type="hidden"
                  name="identifierOperator"
                  value={identifierOperator}
                />
                <Select
                  value={identifierOperator}
                  onValueChange={(v) => setIdentifierOperator(v ?? "")}
                  items={IDENTIFIER_OPERATOR_OPTIONS}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select operator" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {IDENTIFIER_OPERATOR_OPTIONS.map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="identifierName">Name</FieldLabel>
                <Input
                  id="identifierName"
                  name="identifierName"
                  defaultValue={profile.identifier?.name || ""}
                  placeholder="e.g., X-Profile"
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="identifierValue">Value</FieldLabel>
                <Input
                  id="identifierValue"
                  name="identifierValue"
                  defaultValue={profile.identifier?.value || ""}
                  placeholder="e.g., alpha"
                />
              </Field>
            </div>
          </CardContent>
        </Card>

        {/* Protocol-specific Config */}
        {protocolType === "HTTP" ? (
          <Card>
            <CardHeader>
              <CardTitle>HTTP Config</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Request Configuration */}
                <div className="space-y-4">
                  <Field>
                    <FieldLabel>Request Method</FieldLabel>
                    <input
                      type="hidden"
                      name="requestMethod"
                      value={method || ""}
                    />
                    <Select
                      value={method}
                      onValueChange={(v) => setMethod(v ?? undefined)}
                      items={REQUEST_METHOD_OPTIONS}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Select method" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {REQUEST_METHOD_OPTIONS.map((o) => (
                            <SelectItem key={o.value} value={o.value}>
                              {o.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>
                  <Field>
                    <FieldLabel>Request Body Type</FieldLabel>
                    <input
                      type="hidden"
                      name="requestBodyType"
                      value={requestBodyType}
                    />
                    <Select
                      value={requestBodyType}
                      onValueChange={(v) =>
                        setRequestBodyType(v as HttpRequestBodyType)
                      }
                      items={REQUEST_BODY_TYPE_OPTIONS}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Select body type" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {REQUEST_BODY_TYPE_OPTIONS.map((o) => (
                            <SelectItem key={o.value} value={o.value}>
                              {o.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>
                </div>

                {/* Response Configuration */}
                <div className="space-y-4">
                  <Field
                    data-invalid={!!actionData?.errors?.responseStatusCode}
                  >
                    <FieldLabel htmlFor="responseStatusCode">
                      Response Status Code
                    </FieldLabel>
                    <Input
                      id="responseStatusCode"
                      name="responseStatusCode"
                      placeholder="e.g., 200"
                      defaultValue={
                        httpConfig?.responseStatusCode !== undefined &&
                        httpConfig?.responseStatusCode !== 0
                          ? String(httpConfig.responseStatusCode)
                          : ""
                      }
                    />
                    <FieldError>
                      {actionData?.errors?.responseStatusCode}
                    </FieldError>
                  </Field>
                  <Field>
                    <FieldLabel>Response Body Type</FieldLabel>
                    <input
                      type="hidden"
                      name="responseBodyType"
                      value={responseBodyType}
                    />
                    <Select
                      value={responseBodyType}
                      onValueChange={(v) =>
                        setResponseBodyType(v as HttpResponseBodyType)
                      }
                      items={RESPONSE_BODY_TYPE_OPTIONS}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Select body type" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {RESPONSE_BODY_TYPE_OPTIONS.map((o) => (
                            <SelectItem key={o.value} value={o.value}>
                              {o.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field data-invalid={!!actionData?.errors?.requestHeaders}>
                  <FieldLabel htmlFor="requestHeaders">
                    Request Headers (JSON)
                  </FieldLabel>
                  <Textarea
                    id="requestHeaders"
                    name="requestHeaders"
                    placeholder='{"Content-Type":"application/json"}'
                    rows={6}
                    className="font-mono text-sm"
                    defaultValue={
                      httpConfig?.requestHeaders
                        ? JSON.stringify(httpConfig.requestHeaders, null, 2)
                        : ""
                    }
                  />
                  <FieldError>{actionData?.errors?.requestHeaders}</FieldError>
                </Field>
                <Field data-invalid={!!actionData?.errors?.responseHeaders}>
                  <FieldLabel htmlFor="responseHeaders">
                    Response Headers (JSON)
                  </FieldLabel>
                  <Textarea
                    id="responseHeaders"
                    name="responseHeaders"
                    placeholder='{"Cache-Control":"no-cache"}'
                    rows={6}
                    className="font-mono text-sm"
                    defaultValue={
                      httpConfig?.responseHeaders
                        ? JSON.stringify(httpConfig.responseHeaders, null, 2)
                        : ""
                    }
                  />
                  <FieldError>{actionData?.errors?.responseHeaders}</FieldError>
                </Field>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field>
                  <FieldLabel htmlFor="requestTemplate">
                    Request Template
                  </FieldLabel>
                  <Textarea
                    id="requestTemplate"
                    name="requestTemplate"
                    placeholder="Request template with {{payload}} placeholder..."
                    rows={10}
                    className="font-mono text-sm leading-6"
                    value={requestTemplate}
                    onChange={(e) => setRequestTemplate(e.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="responseTemplate">
                    Response Template
                  </FieldLabel>
                  <Textarea
                    id="responseTemplate"
                    name="responseTemplate"
                    placeholder="Response template with {{payload}} placeholder..."
                    rows={10}
                    className="font-mono text-sm leading-6"
                    value={responseTemplate}
                    onChange={(e) => setResponseTemplate(e.target.value)}
                  />
                </Field>
              </div>
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>WebSocket Config</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field>
                  <FieldLabel htmlFor="subprotocol">Subprotocol</FieldLabel>
                  <Input
                    id="subprotocol"
                    name="subprotocol"
                    placeholder="e.g., graphql-ws"
                    defaultValue={wsConfig?.subprotocol || ""}
                  />
                </Field>
                <Field>
                  <FieldLabel>Message Format</FieldLabel>
                  <input
                    type="hidden"
                    name="messageFormat"
                    value={messageFormat}
                  />
                  <Select
                    value={messageFormat}
                    onValueChange={(v) =>
                      setMessageFormat(v as "TEXT" | "BINARY")
                    }
                    items={[
                      { value: "TEXT", label: "Text" },
                      { value: "BINARY", label: "Binary" },
                    ]}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Select format" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="TEXT">Text</SelectItem>
                        <SelectItem value="BINARY">Binary</SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              </div>

              <Field data-invalid={!!actionData?.errors?.handshakeHeaders}>
                <FieldLabel htmlFor="handshakeHeaders">
                  Handshake Headers (JSON)
                </FieldLabel>
                <Textarea
                  id="handshakeHeaders"
                  name="handshakeHeaders"
                  placeholder='{"Authorization":"Bearer token"}'
                  rows={4}
                  className="font-mono text-sm"
                  defaultValue={
                    wsConfig?.handshakeHeaders
                      ? JSON.stringify(wsConfig.handshakeHeaders, null, 2)
                      : ""
                  }
                />
                <FieldError>{actionData?.errors?.handshakeHeaders}</FieldError>
              </Field>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field>
                  <FieldLabel htmlFor="messageTemplate">
                    Message Template
                  </FieldLabel>
                  <Textarea
                    id="messageTemplate"
                    name="messageTemplate"
                    placeholder="Message template with {payload} placeholder..."
                    rows={10}
                    className="font-mono text-sm leading-6"
                    defaultValue={wsConfig?.messageTemplate || ""}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="wsResponseTemplate">
                    Response Template
                  </FieldLabel>
                  <Textarea
                    id="wsResponseTemplate"
                    name="wsResponseTemplate"
                    placeholder="Response template with {payload} placeholder..."
                    rows={10}
                    className="font-mono text-sm leading-6"
                    defaultValue={wsConfig?.responseTemplate || ""}
                  />
                </Field>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Transformers */}
        <Card>
          <CardHeader>
            <CardTitle>Transformers</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-3">
              <FieldLabel>Request Transformers</FieldLabel>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Field>
                  <FieldLabel>Compression</FieldLabel>
                  <input
                    type="hidden"
                    name="requestCompression"
                    value={reqCompression}
                  />
                  <Select
                    value={reqCompression}
                    onValueChange={(v) => setReqCompression(v ?? "none")}
                    items={COMPRESSION_OPTIONS}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select compression" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {COMPRESSION_OPTIONS.map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel>Encryption</FieldLabel>
                  <input
                    type="hidden"
                    name="requestEncryption"
                    value={reqEncryption}
                  />
                  <Select
                    value={reqEncryption}
                    onValueChange={(v) => setReqEncryption(v ?? "none")}
                    items={ENCRYPTION_OPTIONS}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select encryption" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {ENCRYPTION_OPTIONS.map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel>Encoding</FieldLabel>
                  <input
                    type="hidden"
                    name="requestEncoding"
                    value={reqEncoding}
                  />
                  <Select
                    value={reqEncoding}
                    onValueChange={(v) => setReqEncoding(v ?? "none")}
                    items={ENCODING_OPTIONS}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select encoding" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {ENCODING_OPTIONS.map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              </div>
            </div>

            <div className="space-y-3">
              <FieldLabel>Response Transformers</FieldLabel>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Field>
                  <FieldLabel>Compression</FieldLabel>
                  <input
                    type="hidden"
                    name="responseCompression"
                    value={resCompression}
                  />
                  <Select
                    value={resCompression}
                    onValueChange={(v) => setResCompression(v ?? "none")}
                    items={COMPRESSION_OPTIONS}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select compression" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {COMPRESSION_OPTIONS.map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel>Encryption</FieldLabel>
                  <input
                    type="hidden"
                    name="responseEncryption"
                    value={resEncryption}
                  />
                  <Select
                    value={resEncryption}
                    onValueChange={(v) => setResEncryption(v ?? "none")}
                    items={ENCRYPTION_OPTIONS}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select encryption" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {ENCRYPTION_OPTIONS.map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel>Encoding</FieldLabel>
                  <input
                    type="hidden"
                    name="responseEncoding"
                    value={resEncoding}
                  />
                  <Select
                    value={resEncoding}
                    onValueChange={(v) => setResEncoding(v ?? "none")}
                    items={ENCODING_OPTIONS}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select encoding" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {ENCODING_OPTIONS.map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="flex gap-4 pt-4">
          <Button type="submit" className="flex items-center gap-2">
            <Edit className="h-4 w-4" />
            Update Profile
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => navigate("/profiles")}
          >
            Cancel
          </Button>
        </div>
      </Form>
    </div>
  );
}
