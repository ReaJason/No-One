import type { ProfileActionData, ProfileFormValues } from "@/routes/profile/profile-form.shared";
import type { LucideIcon } from "lucide-react";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader } from "lucide-react";
import { useEffect, useMemo } from "react";
import { Controller, useForm } from "react-hook-form";
import { useFetcher } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
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
import { createProfileFormSchema } from "@/routes/profile/profile-form.shared";
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
  icon: LucideIcon;
  initialValues: ProfileFormValues;
  mode: "create" | "edit";
  onCancel: () => void;
  submitLabel: string;
};

const IDENTIFIER_LOCATION_OPTIONS_BY_PROTOCOL = {
  HTTP: HTTP_IDENTIFIER_LOCATION_OPTIONS,
  WEBSOCKET: WEBSOCKET_IDENTIFIER_LOCATION_OPTIONS,
} satisfies Record<ProtocolType, Array<{ label: string; value: string }>>;

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
  icon: Icon,
  initialValues,
  mode,
  onCancel,
  submitLabel,
}: ProfileFormProps) {
  const schema = useMemo(() => createProfileFormSchema(mode), [mode]);
  const {
    control,
    formState: { errors: formErrors },
    getValues,
    handleSubmit,
    reset,
    setValue,
    watch,
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(schema),
    defaultValues: initialValues,
    shouldUnregister: true,
  });

  const fetcher = useFetcher<ProfileActionData>();
  const isSubmitting = fetcher.state === "submitting";
  const serverErrors = fetcher.data?.errors;

  useEffect(() => {
    reset(initialValues);
  }, [initialValues, reset]);

  const onSubmit = async (data: ProfileFormValues) => {
    await fetcher.submit(data, {
      method: "post",
      action,
    });
  };

  const protocolType = watch("protocolType") ?? initialValues.protocolType ?? "HTTP";
  const identifierLocationOptions = IDENTIFIER_LOCATION_OPTIONS_BY_PROTOCOL[protocolType];

  const fieldError = (name: keyof ProfileFormValues) =>
    formErrors[name]?.message ?? serverErrors?.[name];

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {serverErrors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {serverErrors.general}
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
          <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Field data-invalid={Boolean(fieldError("name"))}>
              <FieldLabel htmlFor="name">Name</FieldLabel>
              <Controller
                name="name"
                control={control}
                render={({ field }) => (
                  <Input
                    {...field}
                    id="name"
                    type="text"
                    required
                    placeholder="Profile unique name"
                    value={field.value ?? ""}
                  />
                )}
              />
              <FieldError>{fieldError("name")}</FieldError>
            </Field>
            <Field data-invalid={Boolean(fieldError("password"))}>
              <FieldLabel htmlFor="password">
                {mode === "create" ? "Password" : "Password (Optional)"}
              </FieldLabel>
              <Controller
                name="password"
                control={control}
                render={({ field }) => (
                  <Input
                    {...field}
                    id="password"
                    type="password"
                    required={mode === "create"}
                    placeholder={
                      mode === "create" ? "Private secret key" : "Leave blank to keep unchanged"
                    }
                    value={field.value ?? ""}
                  />
                )}
              />
              <FieldError>{fieldError("password")}</FieldError>
            </Field>
            <Field>
              <FieldLabel>Protocol Type</FieldLabel>
              <Controller
                name="protocolType"
                control={control}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
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
                )}
              />
            </Field>
          </FieldGroup>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Identifier Config</CardTitle>
        </CardHeader>
        <CardContent>
          <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Field>
              <FieldLabel>Location</FieldLabel>
              <Controller
                name="identifierLocation"
                control={control}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
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
                )}
              />
            </Field>
            <Field>
              <FieldLabel>Operator</FieldLabel>
              <Controller
                name="identifierOperator"
                control={control}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
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
                )}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="identifierName">Name</FieldLabel>
              <Controller
                name="identifierName"
                control={control}
                render={({ field }) => (
                  <Input
                    {...field}
                    id="identifierName"
                    placeholder="e.g., X-Profile"
                    value={field.value ?? ""}
                  />
                )}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="identifierValue">Value</FieldLabel>
              <Controller
                name="identifierValue"
                control={control}
                render={({ field }) => (
                  <Input
                    {...field}
                    id="identifierValue"
                    placeholder="e.g., alpha"
                    value={field.value ?? ""}
                  />
                )}
              />
            </Field>
          </FieldGroup>
        </CardContent>
      </Card>

      {protocolType === "HTTP" ? (
        <Card>
          <CardHeader>
            <CardTitle>HTTP Config</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <FieldGroup>
                <Field>
                  <FieldLabel>Request Method</FieldLabel>
                  <Controller
                    name="requestMethod"
                    control={control}
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
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
                    )}
                  />
                </Field>
                <Field>
                  <FieldLabel>Request Body Type</FieldLabel>
                  <Controller
                    name="requestBodyType"
                    control={control}
                    render={({ field }) => (
                      <Select
                        value={field.value}
                        onValueChange={(value) => {
                          const nextType = value as HttpRequestBodyType;
                          const currentTemplate = getValues("requestTemplate") ?? "";
                          setValue(
                            "requestTemplate",
                            getNextTemplateValue(
                              currentTemplate,
                              field.value!,
                              nextType,
                              DEFAULT_REQUEST_TEMPLATES,
                            ),
                          );
                          field.onChange(nextType);
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
                    )}
                  />
                </Field>
              </FieldGroup>

              <FieldGroup>
                <Field data-invalid={Boolean(fieldError("responseStatusCode"))}>
                  <FieldLabel htmlFor="responseStatusCode">Response Status Code</FieldLabel>
                  <Controller
                    name="responseStatusCode"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="responseStatusCode"
                        placeholder="e.g., 200"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldError>{fieldError("responseStatusCode")}</FieldError>
                </Field>
                <Field>
                  <FieldLabel>Response Body Type</FieldLabel>
                  <Controller
                    name="responseBodyType"
                    control={control}
                    render={({ field }) => (
                      <Select
                        value={field.value}
                        onValueChange={(value) => {
                          const nextType = value as HttpResponseBodyType;
                          const currentTemplate = getValues("responseTemplate") ?? "";
                          setValue(
                            "responseTemplate",
                            getNextTemplateValue(
                              currentTemplate,
                              field.value!,
                              nextType,
                              DEFAULT_RESPONSE_TEMPLATES,
                            ),
                          );
                          field.onChange(nextType);
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
                    )}
                  />
                </Field>
              </FieldGroup>
            </FieldGroup>

            <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field data-invalid={Boolean(fieldError("requestHeaders"))}>
                <FieldLabel htmlFor="requestHeaders">Request Headers (JSON)</FieldLabel>
                <Controller
                  name="requestHeaders"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      {...field}
                      id="requestHeaders"
                      placeholder='{"Content-Type":"application/json"}'
                      rows={6}
                      className="font-mono text-sm"
                      value={field.value ?? ""}
                    />
                  )}
                />
                <FieldError>{fieldError("requestHeaders")}</FieldError>
              </Field>
              <Field data-invalid={Boolean(fieldError("responseHeaders"))}>
                <FieldLabel htmlFor="responseHeaders">Response Headers (JSON)</FieldLabel>
                <Controller
                  name="responseHeaders"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      {...field}
                      id="responseHeaders"
                      placeholder='{"Cache-Control":"no-cache"}'
                      rows={6}
                      className="font-mono text-sm"
                      value={field.value ?? ""}
                    />
                  )}
                />
                <FieldError>{fieldError("responseHeaders")}</FieldError>
              </Field>
            </FieldGroup>

            <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="requestTemplate">Request Template</FieldLabel>
                <Controller
                  name="requestTemplate"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      id="requestTemplate"
                      rows={10}
                      className="font-mono text-sm leading-6"
                      value={field.value}
                      onChange={field.onChange}
                    />
                  )}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="responseTemplate">Response Template</FieldLabel>
                <Controller
                  name="responseTemplate"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      id="responseTemplate"
                      rows={10}
                      className="font-mono text-sm leading-6"
                      value={field.value}
                      onChange={field.onChange}
                    />
                  )}
                />
              </Field>
            </FieldGroup>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>WebSocket Config</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="subprotocol">Subprotocol</FieldLabel>
                <Controller
                  name="subprotocol"
                  control={control}
                  render={({ field }) => (
                    <Input
                      {...field}
                      id="subprotocol"
                      placeholder="e.g., graphql-ws"
                      value={field.value ?? ""}
                    />
                  )}
                />
              </Field>
              <Field>
                <FieldLabel>Message Format</FieldLabel>
                <Controller
                  name="messageFormat"
                  control={control}
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
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
                  )}
                />
              </Field>
            </FieldGroup>

            <Field data-invalid={Boolean(fieldError("handshakeHeaders"))}>
              <FieldLabel htmlFor="handshakeHeaders">Handshake Headers (JSON)</FieldLabel>
              <Controller
                name="handshakeHeaders"
                control={control}
                render={({ field }) => (
                  <Textarea
                    {...field}
                    id="handshakeHeaders"
                    placeholder='{"Authorization":"Bearer token"}'
                    rows={4}
                    className="font-mono text-sm"
                    value={field.value ?? ""}
                  />
                )}
              />
              <FieldError>{fieldError("handshakeHeaders")}</FieldError>
            </Field>

            <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="messageTemplate">Message Template</FieldLabel>
                <Controller
                  name="messageTemplate"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      {...field}
                      id="messageTemplate"
                      rows={10}
                      className="font-mono text-sm leading-6"
                      value={field.value ?? ""}
                    />
                  )}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="wsResponseTemplate">Response Template</FieldLabel>
                <Controller
                  name="wsResponseTemplate"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      {...field}
                      id="wsResponseTemplate"
                      rows={10}
                      className="font-mono text-sm leading-6"
                      value={field.value ?? ""}
                    />
                  )}
                />
              </Field>
            </FieldGroup>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Transformers</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <FieldGroup>
            <FieldLabel>Request Transformers</FieldLabel>
            <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <Controller
                name="requestCompression"
                control={control}
                render={({ field }) => (
                  <TransformerSelect
                    label="Compression"
                    options={COMPRESSION_OPTIONS}
                    value={field.value}
                    onValueChange={field.onChange}
                  />
                )}
              />
              <Controller
                name="requestEncryption"
                control={control}
                render={({ field }) => (
                  <TransformerSelect
                    label="Encryption"
                    options={ENCRYPTION_OPTIONS}
                    value={field.value}
                    onValueChange={field.onChange}
                  />
                )}
              />
              <Controller
                name="requestEncoding"
                control={control}
                render={({ field }) => (
                  <TransformerSelect
                    label="Encoding"
                    options={ENCODING_OPTIONS}
                    value={field.value}
                    onValueChange={field.onChange}
                  />
                )}
              />
            </FieldGroup>
          </FieldGroup>

          <FieldGroup>
            <FieldLabel>Response Transformers</FieldLabel>
            <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <Controller
                name="responseCompression"
                control={control}
                render={({ field }) => (
                  <TransformerSelect
                    label="Compression"
                    options={COMPRESSION_OPTIONS}
                    value={field.value}
                    onValueChange={field.onChange}
                  />
                )}
              />
              <Controller
                name="responseEncryption"
                control={control}
                render={({ field }) => (
                  <TransformerSelect
                    label="Encryption"
                    options={ENCRYPTION_OPTIONS}
                    value={field.value}
                    onValueChange={field.onChange}
                  />
                )}
              />
              <Controller
                name="responseEncoding"
                control={control}
                render={({ field }) => (
                  <TransformerSelect
                    label="Encoding"
                    options={ENCODING_OPTIONS}
                    value={field.value}
                    onValueChange={field.onChange}
                  />
                )}
              />
            </FieldGroup>
          </FieldGroup>
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
    </form>
  );
}

function TransformerSelect({
  label,
  options,
  value,
  onValueChange,
}: {
  label: string;
  options: Array<{ label: string; value: string }>;
  value: string;
  onValueChange: (value: string) => void;
}) {
  return (
    <Field>
      <FieldLabel>{label}</FieldLabel>
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
