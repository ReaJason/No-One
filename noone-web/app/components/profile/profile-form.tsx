import type { ProfileFormSeed } from "@/routes/profile/profile-form.shared";
import type { LucideIcon } from "lucide-react";

import { Loader } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Form, useNavigation } from "react-router";

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
  type MessageFormat,
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

type ProfileFormDraft = Pick<
  ProfileFormSeed,
  | "identifierLocation"
  | "identifierOperator"
  | "messageFormat"
  | "protocolType"
  | "requestBodyType"
  | "requestCompression"
  | "requestEncoding"
  | "requestEncryption"
  | "requestMethod"
  | "requestTemplate"
  | "responseBodyType"
  | "responseCompression"
  | "responseEncoding"
  | "responseEncryption"
  | "responseTemplate"
>;

const IDENTIFIER_LOCATION_OPTIONS_BY_PROTOCOL = {
  HTTP: HTTP_IDENTIFIER_LOCATION_OPTIONS,
  WEBSOCKET: WEBSOCKET_IDENTIFIER_LOCATION_OPTIONS,
} satisfies Record<ProtocolType, Array<{ label: string; value: string }>>;

const UNCONTROLLED_FIELD_NAMES = [
  "handshakeHeaders",
  "identifierName",
  "identifierValue",
  "messageTemplate",
  "name",
  "password",
  "requestHeaders",
  "responseHeaders",
  "responseStatusCode",
  "subprotocol",
  "wsResponseTemplate",
] as const;

type UncontrolledFieldName = (typeof UNCONTROLLED_FIELD_NAMES)[number];

function buildProfileFormDraft(initialValues: ProfileFormSeed): ProfileFormDraft {
  return {
    identifierLocation: initialValues.identifierLocation,
    identifierOperator: initialValues.identifierOperator,
    messageFormat: initialValues.messageFormat,
    protocolType: initialValues.protocolType,
    requestBodyType: initialValues.requestBodyType,
    requestCompression: initialValues.requestCompression,
    requestEncoding: initialValues.requestEncoding,
    requestEncryption: initialValues.requestEncryption,
    requestMethod: initialValues.requestMethod,
    requestTemplate: initialValues.requestTemplate,
    responseBodyType: initialValues.responseBodyType,
    responseCompression: initialValues.responseCompression,
    responseEncoding: initialValues.responseEncoding,
    responseEncryption: initialValues.responseEncryption,
    responseTemplate: initialValues.responseTemplate,
  };
}

function getUncontrolledFieldValues(
  initialValues: ProfileFormSeed,
): Record<UncontrolledFieldName, string> {
  return {
    handshakeHeaders: initialValues.handshakeHeaders,
    identifierName: initialValues.identifierName,
    identifierValue: initialValues.identifierValue,
    messageTemplate: initialValues.messageTemplate,
    name: initialValues.name,
    password: initialValues.password,
    requestHeaders: initialValues.requestHeaders,
    responseHeaders: initialValues.responseHeaders,
    responseStatusCode: initialValues.responseStatusCode,
    subprotocol: initialValues.subprotocol,
    wsResponseTemplate: initialValues.wsResponseTemplate,
  };
}

function syncUncontrolledField(form: HTMLFormElement, name: UncontrolledFieldName, value: string) {
  const element = form.elements.namedItem(name);
  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
    element.defaultValue = value;
    element.value = value;
  }
}

function syncUncontrolledFields(form: HTMLFormElement | null, initialValues: ProfileFormSeed) {
  if (!form) {
    return;
  }

  const values = getUncontrolledFieldValues(initialValues);
  for (const name of UNCONTROLLED_FIELD_NAMES) {
    syncUncontrolledField(form, name, values[name]);
  }
}

function getNextTemplateValue<T extends string>(
  currentTemplate: string,
  previousType: T,
  nextType: T,
  defaults: Record<T, string>,
) {
  const previousDefault = defaults[previousType];
  if (currentTemplate.trim() === "" || currentTemplate === previousDefault) {
    return defaults[nextType];
  }
  return currentTemplate;
}

export function ProfileForm({
  action,
  errors,
  icon: Icon,
  initialValues,
  mode,
  onCancel,
  submitLabel,
}: ProfileFormProps) {
  const formRef = useRef<HTMLFormElement>(null);
  const [draft, setDraft] = useState(() => buildProfileFormDraft(initialValues));
  const navigation = useNavigation();
  const isSubmitting = navigation.state === "submitting";

  useEffect(() => {
    setDraft(buildProfileFormDraft(initialValues));
    syncUncontrolledFields(formRef.current, initialValues);
  }, [initialValues]);

  const identifierLocationOptions = IDENTIFIER_LOCATION_OPTIONS_BY_PROTOCOL[draft.protocolType];

  return (
    <Form ref={formRef} method="post" action={action} className="space-y-6">
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
              <input type="hidden" name="protocolType" value={draft.protocolType} />
              <Select
                value={draft.protocolType}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    protocolType: (value as ProtocolType) ?? current.protocolType,
                  }))
                }
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
              <input type="hidden" name="identifierLocation" value={draft.identifierLocation} />
              <Select
                value={draft.identifierLocation}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    identifierLocation: value ?? "",
                  }))
                }
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
              <input type="hidden" name="identifierOperator" value={draft.identifierOperator} />
              <Select
                value={draft.identifierOperator}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    identifierOperator: value ?? "",
                  }))
                }
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

      {draft.protocolType === "HTTP" ? (
        <Card>
          <CardHeader>
            <CardTitle>HTTP Config</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="space-y-4">
                <Field>
                  <FieldLabel>Request Method</FieldLabel>
                  <input type="hidden" name="requestMethod" value={draft.requestMethod} />
                  <Select
                    value={draft.requestMethod}
                    onValueChange={(value) =>
                      setDraft((current) => ({
                        ...current,
                        requestMethod: value ?? "",
                      }))
                    }
                  >
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
                  <input type="hidden" name="requestBodyType" value={draft.requestBodyType} />
                  <Select
                    value={draft.requestBodyType}
                    onValueChange={(value) => {
                      const nextRequestBodyType =
                        (value as HttpRequestBodyType) ?? draft.requestBodyType;

                      setDraft((current) => ({
                        ...current,
                        requestBodyType: nextRequestBodyType,
                        requestTemplate: getNextTemplateValue(
                          current.requestTemplate,
                          current.requestBodyType,
                          nextRequestBodyType,
                          DEFAULT_REQUEST_TEMPLATES,
                        ),
                      }));
                    }}
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
                  <input type="hidden" name="responseBodyType" value={draft.responseBodyType} />
                  <Select
                    value={draft.responseBodyType}
                    onValueChange={(value) => {
                      const nextResponseBodyType =
                        (value as HttpResponseBodyType) ?? draft.responseBodyType;

                      setDraft((current) => ({
                        ...current,
                        responseBodyType: nextResponseBodyType,
                        responseTemplate: getNextTemplateValue(
                          current.responseTemplate,
                          current.responseBodyType,
                          nextResponseBodyType,
                          DEFAULT_RESPONSE_TEMPLATES,
                        ),
                      }));
                    }}
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
                  value={draft.requestTemplate}
                  onChange={(event) =>
                    setDraft((current) => ({
                      ...current,
                      requestTemplate: event.target.value,
                    }))
                  }
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="responseTemplate">Response Template</FieldLabel>
                <Textarea
                  id="responseTemplate"
                  name="responseTemplate"
                  rows={10}
                  className="font-mono text-sm leading-6"
                  value={draft.responseTemplate}
                  onChange={(event) =>
                    setDraft((current) => ({
                      ...current,
                      responseTemplate: event.target.value,
                    }))
                  }
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
                <input type="hidden" name="messageFormat" value={draft.messageFormat} />
                <Select
                  value={draft.messageFormat}
                  onValueChange={(value) =>
                    setDraft((current) => ({
                      ...current,
                      messageFormat: (value as MessageFormat) ?? current.messageFormat,
                    }))
                  }
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
                value={draft.requestCompression}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    requestCompression: value,
                  }))
                }
              />
              <TransformerSelect
                label="Encryption"
                name="requestEncryption"
                options={ENCRYPTION_OPTIONS}
                value={draft.requestEncryption}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    requestEncryption: value,
                  }))
                }
              />
              <TransformerSelect
                label="Encoding"
                name="requestEncoding"
                options={ENCODING_OPTIONS}
                value={draft.requestEncoding}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    requestEncoding: value,
                  }))
                }
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
                value={draft.responseCompression}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    responseCompression: value,
                  }))
                }
              />
              <TransformerSelect
                label="Encryption"
                name="responseEncryption"
                options={ENCRYPTION_OPTIONS}
                value={draft.responseEncryption}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    responseEncryption: value,
                  }))
                }
              />
              <TransformerSelect
                label="Encoding"
                name="responseEncoding"
                options={ENCODING_OPTIONS}
                value={draft.responseEncoding}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    responseEncoding: value,
                  }))
                }
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="flex gap-4 pt-2">
        <Button type="submit" className="flex items-center gap-2" disabled={isSubmitting}>
          {isSubmitting ? (
            <Loader className="h-4 w-4 animate-spin" />
          ) : (
            <Icon className="h-4 w-4" />
          )}
          {isSubmitting ? `${submitLabel}...` : submitLabel}
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
