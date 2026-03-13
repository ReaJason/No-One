import type { ProfileFormSeed } from "@/routes/profile/profile-form.shared";
import type { LucideIcon } from "lucide-react";

import { useEffect, useRef, useState } from "react";
import { Form } from "react-router";

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
import {
  COMPRESSION_OPTIONS,
  DEFAULT_REQUEST_TEMPLATES,
  DEFAULT_RESPONSE_TEMPLATES,
  ENCODING_OPTIONS,
  ENCRYPTION_OPTIONS,
  HTTP_IDENTIFIER_LOCATION_OPTIONS,
  IDENTIFIER_OPERATOR_OPTIONS,
  REQUEST_BODY_TYPE_OPTIONS,
  REQUEST_METHOD_OPTIONS,
  RESPONSE_BODY_TYPE_OPTIONS,
  WEBSOCKET_IDENTIFIER_LOCATION_OPTIONS,
  type HttpRequestBodyType,
  type HttpResponseBodyType,
  type ProtocolType,
} from "@/types/profile";

type ProfileFormProps = {
  action?: string;
  errors?: Record<string, string>;
  icon: LucideIcon;
  initialValues: ProfileFormSeed;
  mode: "create" | "edit";
  onCancel: () => void;
  submitLabel: string;
};

export function ProfileForm({
  action,
  errors,
  icon: Icon,
  initialValues,
  mode,
  onCancel,
  submitLabel,
}: ProfileFormProps) {
  const [protocolType, setProtocolType] = useState<ProtocolType>(initialValues.protocolType);
  const [method, setMethod] = useState(initialValues.requestMethod);
  const [requestBodyType, setRequestBodyType] = useState<HttpRequestBodyType>(
    initialValues.requestBodyType,
  );
  const [responseBodyType, setResponseBodyType] = useState<HttpResponseBodyType>(
    initialValues.responseBodyType,
  );
  const [identifierLocation, setIdentifierLocation] = useState(initialValues.identifierLocation);
  const [identifierOperator, setIdentifierOperator] = useState(initialValues.identifierOperator);
  const [messageFormat, setMessageFormat] = useState<"TEXT" | "BINARY">(
    initialValues.messageFormat,
  );
  const [requestTemplate, setRequestTemplate] = useState(initialValues.requestTemplate);
  const [responseTemplate, setResponseTemplate] = useState(initialValues.responseTemplate);
  const [reqCompression, setReqCompression] = useState(initialValues.requestCompression);
  const [reqEncryption, setReqEncryption] = useState(initialValues.requestEncryption);
  const [reqEncoding, setReqEncoding] = useState(initialValues.requestEncoding);
  const [resCompression, setResCompression] = useState(initialValues.responseCompression);
  const [resEncryption, setResEncryption] = useState(initialValues.responseEncryption);
  const [resEncoding, setResEncoding] = useState(initialValues.responseEncoding);

  const previousRequestBodyType = useRef<HttpRequestBodyType>(requestBodyType);
  const previousResponseBodyType = useRef<HttpResponseBodyType>(responseBodyType);

  useEffect(() => {
    setProtocolType(initialValues.protocolType);
    setMethod(initialValues.requestMethod);
    setRequestBodyType(initialValues.requestBodyType);
    setResponseBodyType(initialValues.responseBodyType);
    setIdentifierLocation(initialValues.identifierLocation);
    setIdentifierOperator(initialValues.identifierOperator);
    setMessageFormat(initialValues.messageFormat);
    setRequestTemplate(initialValues.requestTemplate);
    setResponseTemplate(initialValues.responseTemplate);
    setReqCompression(initialValues.requestCompression);
    setReqEncryption(initialValues.requestEncryption);
    setReqEncoding(initialValues.requestEncoding);
    setResCompression(initialValues.responseCompression);
    setResEncryption(initialValues.responseEncryption);
    setResEncoding(initialValues.responseEncoding);
    previousRequestBodyType.current = initialValues.requestBodyType;
    previousResponseBodyType.current = initialValues.responseBodyType;
  }, [initialValues]);

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
    if (responseTemplate.trim() === "" || responseTemplate === previousDefault) {
      setResponseTemplate(DEFAULT_RESPONSE_TEMPLATES[responseBodyType]);
    }
    previousResponseBodyType.current = responseBodyType;
  }, [responseBodyType, responseTemplate]);

  const identifierLocationOptions =
    protocolType === "HTTP"
      ? HTTP_IDENTIFIER_LOCATION_OPTIONS
      : WEBSOCKET_IDENTIFIER_LOCATION_OPTIONS;

  return (
    <Form method="post" action={action} className="space-y-6">
      {errors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {errors.general}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Icon className="h-5 w-5" />
            Basic Info
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Field data-invalid={Boolean(errors?.name)}>
              <FieldLabel htmlFor="name">Name</FieldLabel>
              <Input
                id="name"
                name="name"
                type="text"
                required
                defaultValue={initialValues.name}
                placeholder="Profile unique name"
              />
              <FieldError>{errors?.name}</FieldError>
            </Field>
            <Field data-invalid={Boolean(errors?.password)}>
              <FieldLabel htmlFor="password">
                {mode === "create" ? "Password" : "Password (Optional)"}
              </FieldLabel>
              <Input
                id="password"
                name="password"
                type="password"
                required={mode === "create"}
                defaultValue={initialValues.password}
                placeholder={
                  mode === "create" ? "Private secret key" : "Leave blank to keep unchanged"
                }
              />
              <FieldError>{errors?.password}</FieldError>
            </Field>
            <Field>
              <FieldLabel>Protocol Type</FieldLabel>
              <input type="hidden" name="protocolType" value={protocolType} />
              <Select
                value={protocolType}
                onValueChange={(value) => setProtocolType(value as ProtocolType)}
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

      <Card>
        <CardHeader>
          <CardTitle>Identifier Config</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Field>
              <FieldLabel>Location</FieldLabel>
              <input type="hidden" name="identifierLocation" value={identifierLocation} />
              <Select
                value={identifierLocation}
                onValueChange={(value) => setIdentifierLocation(value ?? "")}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select location" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {identifierLocationOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel>Operator</FieldLabel>
              <input type="hidden" name="identifierOperator" value={identifierOperator} />
              <Select
                value={identifierOperator}
                onValueChange={(value) => setIdentifierOperator(value ?? "")}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select operator" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {IDENTIFIER_OPERATOR_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
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
                defaultValue={initialValues.identifierName}
                placeholder="e.g., X-Profile"
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="identifierValue">Value</FieldLabel>
              <Input
                id="identifierValue"
                name="identifierValue"
                defaultValue={initialValues.identifierValue}
                placeholder="e.g., alpha"
              />
            </Field>
          </div>
        </CardContent>
      </Card>

      {protocolType === "HTTP" ? (
        <Card>
          <CardHeader>
            <CardTitle>HTTP Config</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="space-y-4">
                <Field>
                  <FieldLabel>Request Method</FieldLabel>
                  <input type="hidden" name="requestMethod" value={method} />
                  <Select value={method} onValueChange={(value) => setMethod(value ?? "")}>
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Select method" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {REQUEST_METHOD_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel>Request Body Type</FieldLabel>
                  <input type="hidden" name="requestBodyType" value={requestBodyType} />
                  <Select
                    value={requestBodyType}
                    onValueChange={(value) => setRequestBodyType(value as HttpRequestBodyType)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Select body type" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {REQUEST_BODY_TYPE_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              </div>

              <div className="space-y-4">
                <Field data-invalid={Boolean(errors?.responseStatusCode)}>
                  <FieldLabel htmlFor="responseStatusCode">Response Status Code</FieldLabel>
                  <Input
                    id="responseStatusCode"
                    name="responseStatusCode"
                    placeholder="e.g., 200"
                    defaultValue={initialValues.responseStatusCode}
                  />
                  <FieldError>{errors?.responseStatusCode}</FieldError>
                </Field>
                <Field>
                  <FieldLabel>Response Body Type</FieldLabel>
                  <input type="hidden" name="responseBodyType" value={responseBodyType} />
                  <Select
                    value={responseBodyType}
                    onValueChange={(value) => setResponseBodyType(value as HttpResponseBodyType)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Select body type" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {RESPONSE_BODY_TYPE_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field data-invalid={Boolean(errors?.requestHeaders)}>
                <FieldLabel htmlFor="requestHeaders">Request Headers (JSON)</FieldLabel>
                <Textarea
                  id="requestHeaders"
                  name="requestHeaders"
                  placeholder='{"Content-Type":"application/json"}'
                  rows={6}
                  className="font-mono text-sm"
                  defaultValue={initialValues.requestHeaders}
                />
                <FieldError>{errors?.requestHeaders}</FieldError>
              </Field>
              <Field data-invalid={Boolean(errors?.responseHeaders)}>
                <FieldLabel htmlFor="responseHeaders">Response Headers (JSON)</FieldLabel>
                <Textarea
                  id="responseHeaders"
                  name="responseHeaders"
                  placeholder='{"Cache-Control":"no-cache"}'
                  rows={6}
                  className="font-mono text-sm"
                  defaultValue={initialValues.responseHeaders}
                />
                <FieldError>{errors?.responseHeaders}</FieldError>
              </Field>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="requestTemplate">Request Template</FieldLabel>
                <Textarea
                  id="requestTemplate"
                  name="requestTemplate"
                  rows={10}
                  className="font-mono text-sm leading-6"
                  value={requestTemplate}
                  onChange={(event) => setRequestTemplate(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="responseTemplate">Response Template</FieldLabel>
                <Textarea
                  id="responseTemplate"
                  name="responseTemplate"
                  rows={10}
                  className="font-mono text-sm leading-6"
                  value={responseTemplate}
                  onChange={(event) => setResponseTemplate(event.target.value)}
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
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="subprotocol">Subprotocol</FieldLabel>
                <Input
                  id="subprotocol"
                  name="subprotocol"
                  placeholder="e.g., graphql-ws"
                  defaultValue={initialValues.subprotocol}
                />
              </Field>
              <Field>
                <FieldLabel>Message Format</FieldLabel>
                <input type="hidden" name="messageFormat" value={messageFormat} />
                <Select
                  value={messageFormat}
                  onValueChange={(value) => setMessageFormat(value as "TEXT" | "BINARY")}
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

            <Field data-invalid={Boolean(errors?.handshakeHeaders)}>
              <FieldLabel htmlFor="handshakeHeaders">Handshake Headers (JSON)</FieldLabel>
              <Textarea
                id="handshakeHeaders"
                name="handshakeHeaders"
                placeholder='{"Authorization":"Bearer token"}'
                rows={4}
                className="font-mono text-sm"
                defaultValue={initialValues.handshakeHeaders}
              />
              <FieldError>{errors?.handshakeHeaders}</FieldError>
            </Field>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="messageTemplate">Message Template</FieldLabel>
                <Textarea
                  id="messageTemplate"
                  name="messageTemplate"
                  rows={10}
                  className="font-mono text-sm leading-6"
                  defaultValue={initialValues.messageTemplate}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="wsResponseTemplate">Response Template</FieldLabel>
                <Textarea
                  id="wsResponseTemplate"
                  name="wsResponseTemplate"
                  rows={10}
                  className="font-mono text-sm leading-6"
                  defaultValue={initialValues.wsResponseTemplate}
                />
              </Field>
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Transformers</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-3">
            <FieldLabel>Request Transformers</FieldLabel>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <TransformerSelect
                label="Compression"
                name="requestCompression"
                options={COMPRESSION_OPTIONS}
                value={reqCompression}
                onValueChange={setReqCompression}
              />
              <TransformerSelect
                label="Encryption"
                name="requestEncryption"
                options={ENCRYPTION_OPTIONS}
                value={reqEncryption}
                onValueChange={setReqEncryption}
              />
              <TransformerSelect
                label="Encoding"
                name="requestEncoding"
                options={ENCODING_OPTIONS}
                value={reqEncoding}
                onValueChange={setReqEncoding}
              />
            </div>
          </div>

          <div className="space-y-3">
            <FieldLabel>Response Transformers</FieldLabel>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <TransformerSelect
                label="Compression"
                name="responseCompression"
                options={COMPRESSION_OPTIONS}
                value={resCompression}
                onValueChange={setResCompression}
              />
              <TransformerSelect
                label="Encryption"
                name="responseEncryption"
                options={ENCRYPTION_OPTIONS}
                value={resEncryption}
                onValueChange={setResEncryption}
              />
              <TransformerSelect
                label="Encoding"
                name="responseEncoding"
                options={ENCODING_OPTIONS}
                value={resEncoding}
                onValueChange={setResEncoding}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="flex gap-4 pt-2">
        <Button type="submit" className="flex items-center gap-2">
          <Icon className="h-4 w-4" />
          {submitLabel}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </Form>
  );
}

function TransformerSelect({
  label,
  name,
  options,
  value,
  onValueChange,
}: {
  label: string;
  name: string;
  options: Array<{ label: string; value: string }>;
  value: string;
  onValueChange: (value: string) => void;
}) {
  return (
    <Field>
      <FieldLabel>{label}</FieldLabel>
      <input type="hidden" name={name} value={value} />
      <Select value={value} onValueChange={(nextValue) => onValueChange(nextValue ?? "None")}>
        <SelectTrigger>
          <SelectValue placeholder={`Select ${label.toLowerCase()}`} />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {options.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
    </Field>
  );
}
