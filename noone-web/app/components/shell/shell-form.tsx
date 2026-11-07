import type { ShellActionData, ShellFormValues } from "@/routes/shell/shell-form.shared";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo } from "react";
import { Controller, useForm } from "react-hook-form";
import { useFetcher } from "react-router";
import { toast } from "sonner";

import ShellFormActions from "@/components/shell/shell-form-actions";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  BROWSER_IMPERSONATION_PROFILES,
  COMMON_SHELL_TYPES,
  createShellFormSchema,
  LANGUAGE_ITEMS,
  toSubmitData,
} from "@/routes/shell/shell-form.shared";

type ShellFormProps = {
  mode: "create" | "edit";
  initialValues: ShellFormValues;
  isPrefilled: boolean;
  profileItems: Array<{ label: string; value: string }>;
  projectItems: Array<{ label: string; value: string }>;
  onCancel: () => void;
};

export function ShellForm({
  mode,
  initialValues,
  isPrefilled,
  profileItems,
  projectItems,
  onCancel,
}: ShellFormProps) {
  const isEdit = mode === "edit";
  const schema = useMemo(() => createShellFormSchema(mode), [mode]);
  const {
    control,
    formState: { errors: formErrors },
    getValues,
    handleSubmit,
    setValue,
    watch,
  } = useForm<ShellFormValues>({
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });

  const fetcher = useFetcher<ShellActionData>();
  const testFetcher = useFetcher<ShellActionData>();
  const isSubmitting = fetcher.state === "submitting";
  const isTesting = testFetcher.state !== "idle";
  // The form is keyed by its identity in the parent route, so a changed shell or
  // prefill remounts it with fresh default values. Resetting on every
  // `initialValues` identity change would wipe user input whenever a fetcher
  // action (such as "Test Connection") triggers loader revalidation.
  const serverErrors = fetcher.data?.errors;

  useEffect(() => {
    if (testFetcher.state !== "idle" || !testFetcher.data) return;
    if (testFetcher.data.success) {
      toast.success("Connection test successful");
      return;
    }
    if (testFetcher.data.errors?.general) {
      toast.error(testFetcher.data.errors.general);
    }
  }, [testFetcher.data, testFetcher.state]);

  const staging = watch("staging");
  const browserImpersonation = watch("browserImpersonation");
  const profileIdValue = watch("profileId");
  const loaderProfileIdValue = watch("loaderProfileId");

  useEffect(() => {
    if (staging && !loaderProfileIdValue) {
      setValue("loaderProfileId", profileIdValue);
    }
  }, [staging, loaderProfileIdValue, profileIdValue, setValue]);

  const onSubmit = (data: ShellFormValues) => {
    fetcher.submit(toSubmitData(data), { method: "post" });
  };

  const handleTestConnection = () => {
    const values = getValues();
    if (!values.url.trim() || !values.profileId.trim()) {
      toast.error("URL and Profile are required to test connection");
      return;
    }
    testFetcher.submit({ ...toSubmitData(values), intent: "test-config" }, { method: "post" });
  };

  const fieldError = (name: keyof ShellFormValues) =>
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
          <CardTitle>Basic Details</CardTitle>
          <CardDescription>
            Define the shell identity, runtime, and the profile used to communicate with it.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Basic Details</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <Field data-invalid={Boolean(fieldError("name"))}>
                  <FieldLabel htmlFor="name">Name *</FieldLabel>
                  <Controller
                    name="name"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="name"
                        type="text"
                        placeholder="TestShell"
                        aria-invalid={Boolean(fieldError("name")) || undefined}
                        aria-describedby="name-description"
                        required
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription id="name-description">Shell name for search.</FieldDescription>
                  <FieldError>{fieldError("name")}</FieldError>
                </Field>

                <Field data-invalid={Boolean(fieldError("language"))}>
                  <FieldLabel htmlFor="language">Language *</FieldLabel>
                  <Controller
                    name="language"
                    control={control}
                    render={({ field }) => (
                      <Select
                        items={LANGUAGE_ITEMS}
                        value={field.value}
                        onValueChange={field.onChange}
                        disabled={isPrefilled}
                      >
                        <SelectTrigger
                          id="language"
                          aria-invalid={Boolean(fieldError("language")) || undefined}
                          className="w-full"
                        >
                          <SelectValue placeholder="Select language" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {LANGUAGE_ITEMS.map((item) => (
                              <SelectItem key={item.value} value={item.value}>
                                {item.label}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    )}
                  />
                  <FieldDescription>
                    Language determines plugin runtime and encoding.
                  </FieldDescription>
                  <FieldError>{fieldError("language")}</FieldError>
                </Field>

                <Field data-invalid={Boolean(fieldError("url"))} className="md:col-span-2">
                  <FieldLabel htmlFor="url">Shell URL *</FieldLabel>
                  <Controller
                    name="url"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="url"
                        type="text"
                        placeholder="http://example.com/shell.jsp or dubbo://host:20880/com.example.Service"
                        aria-invalid={Boolean(fieldError("url")) || undefined}
                        aria-describedby="url-description"
                        required
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription id="url-description">
                    Target endpoint. For Dubbo use dubbo://, hessian://, or tri:// scheme.
                  </FieldDescription>
                  <FieldError>{fieldError("url")}</FieldError>
                </Field>

                <Field data-invalid={Boolean(fieldError("profileId"))}>
                  <FieldLabel htmlFor="profileId">Profile *</FieldLabel>
                  <Controller
                    name="profileId"
                    control={control}
                    render={({ field }) => (
                      <Select
                        items={profileItems}
                        value={field.value}
                        onValueChange={field.onChange}
                        disabled={isPrefilled && !staging}
                      >
                        <SelectTrigger
                          id="profileId"
                          aria-invalid={Boolean(fieldError("profileId")) || undefined}
                          className="w-full"
                        >
                          <SelectValue placeholder="Select profile" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {profileItems.map((profile) => (
                              <SelectItem key={profile.value} value={profile.value}>
                                {profile.label}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    )}
                  />
                  <FieldDescription>
                    Profile determines the request format and protocol settings.
                  </FieldDescription>
                  <FieldError>{fieldError("profileId")}</FieldError>
                </Field>

                <Field data-invalid={Boolean(fieldError("shellType"))}>
                  <FieldLabel htmlFor="shellType">Shell Type</FieldLabel>
                  <Controller
                    name="shellType"
                    control={control}
                    render={({ field }) => (
                      <>
                        <Input
                          id="shellType"
                          type="text"
                          list="shell-type-options"
                          value={field.value}
                          onChange={field.onChange}
                          placeholder="Servlet"
                          aria-invalid={Boolean(fieldError("shellType")) || undefined}
                          disabled={isPrefilled}
                        />
                        <datalist id="shell-type-options">
                          {COMMON_SHELL_TYPES.map((item) => (
                            <option key={item} value={item} />
                          ))}
                        </datalist>
                      </>
                    )}
                  />
                  <FieldDescription>
                    Runtime shell type, such as `Servlet`, `Filter`, or `NettyHandler`.
                  </FieldDescription>
                  <FieldError>{fieldError("shellType")}</FieldError>
                </Field>

                <Field>
                  <FieldLabel htmlFor="interfaceName">Interface Name</FieldLabel>
                  <Controller
                    name="interfaceName"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="interfaceName"
                        type="text"
                        placeholder="com.example.ShellService"
                        aria-describedby="interfaceName-description"
                        disabled={isPrefilled}
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription id="interfaceName-description">
                    Dubbo service interface name for RPC invocation (DUBBO protocol only).
                  </FieldDescription>
                </Field>

                <Field data-invalid={Boolean(fieldError("projectId"))}>
                  <FieldLabel htmlFor="projectId">Project</FieldLabel>
                  <Controller
                    name="projectId"
                    control={control}
                    render={({ field }) => (
                      <Select
                        items={projectItems}
                        value={field.value}
                        onValueChange={field.onChange}
                      >
                        <SelectTrigger
                          id="projectId"
                          aria-invalid={Boolean(fieldError("projectId")) || undefined}
                          className="w-full"
                        >
                          <SelectValue placeholder="Select project" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {projectItems.map((project) => (
                              <SelectItem key={project.value} value={project.value}>
                                {project.label}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    )}
                  />
                  <FieldDescription>Optional grouping for organizing shells.</FieldDescription>
                  <FieldError>{fieldError("projectId")}</FieldError>
                </Field>
              </div>

              <Field orientation="horizontal">
                <Controller
                  name="staging"
                  control={control}
                  render={({ field }) => (
                    <Checkbox
                      id="staging"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      disabled={isPrefilled}
                    />
                  )}
                />
                <FieldContent>
                  <FieldLabel htmlFor="staging">Use staging loader</FieldLabel>
                  <FieldDescription>
                    Enable two-stage loading and attach a dedicated loader profile.
                  </FieldDescription>
                </FieldContent>
              </Field>

              {staging ? (
                <Field data-invalid={Boolean(fieldError("loaderProfileId"))}>
                  <FieldLabel htmlFor="loaderProfileId">Loader Profile *</FieldLabel>
                  <Controller
                    name="loaderProfileId"
                    control={control}
                    render={({ field }) => (
                      <Select
                        items={profileItems}
                        value={field.value}
                        onValueChange={field.onChange}
                        disabled={isPrefilled}
                      >
                        <SelectTrigger
                          id="loaderProfileId"
                          aria-invalid={Boolean(fieldError("loaderProfileId")) || undefined}
                          className="w-full"
                        >
                          <SelectValue placeholder="Select loader profile" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {profileItems.map((profile) => (
                              <SelectItem key={profile.value} value={profile.value}>
                                {profile.label}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    )}
                  />
                  <FieldDescription>
                    Loader profile used before the staged core payload is activated.
                  </FieldDescription>
                  <FieldError>{fieldError("loaderProfileId")}</FieldError>
                </Field>
              ) : null}
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Connection Settings</CardTitle>
          <CardDescription>
            Tune transport behavior, proxy overrides, retries, and certificate validation.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Connection Settings</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <Field className="md:col-span-2">
                  <FieldLabel htmlFor="proxyUrl">Proxy URL</FieldLabel>
                  <Controller
                    name="proxyUrl"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="proxyUrl"
                        type="text"
                        placeholder="http://proxy:8080 or socks5://user:pass@proxy:1080"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription>Override the profile&apos;s proxy setting.</FieldDescription>
                </Field>

                <Field
                  data-invalid={Boolean(fieldError("customHeaders"))}
                  className="md:col-span-2"
                >
                  <FieldLabel htmlFor="customHeaders">Custom Headers (JSON)</FieldLabel>
                  <Controller
                    name="customHeaders"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="customHeaders"
                        type="text"
                        placeholder='{"Cookie": "session=xxx", "Authorization": "Bearer xxx"}'
                        aria-invalid={Boolean(fieldError("customHeaders")) || undefined}
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription>
                    Additional headers merged with profile headers.
                  </FieldDescription>
                  <FieldError>{fieldError("customHeaders")}</FieldError>
                </Field>

                <Field>
                  <FieldLabel htmlFor="connectTimeoutMs">Connect Timeout (ms)</FieldLabel>
                  <Controller
                    name="connectTimeoutMs"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="connectTimeoutMs"
                        type="number"
                        placeholder="30000"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="readTimeoutMs">Read Timeout (ms)</FieldLabel>
                  <Controller
                    name="readTimeoutMs"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="readTimeoutMs"
                        type="number"
                        placeholder="60000"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                </Field>

                <Field>
                  <FieldLabel htmlFor="maxRetries">Max Retries</FieldLabel>
                  <Controller
                    name="maxRetries"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="maxRetries"
                        type="number"
                        min="0"
                        placeholder="0"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="retryDelayMs">Retry Delay (ms)</FieldLabel>
                  <Controller
                    name="retryDelayMs"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="retryDelayMs"
                        type="number"
                        min="0"
                        placeholder="1000"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                </Field>
              </div>

              <Field orientation="horizontal">
                <Controller
                  name="skipSslVerify"
                  control={control}
                  render={({ field }) => (
                    <Checkbox
                      id="skipSslVerify"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  )}
                />
                <FieldContent>
                  <FieldLabel htmlFor="skipSslVerify">Skip SSL certificate verification</FieldLabel>
                  <FieldDescription>
                    Enable for self-signed certificates (not recommended for production).
                  </FieldDescription>
                </FieldContent>
              </Field>

              <Field orientation="horizontal">
                <Controller
                  name="browserImpersonation"
                  control={control}
                  render={({ field }) => (
                    <Switch
                      id="browserImpersonation"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  )}
                />
                <FieldContent>
                  <FieldLabel htmlFor="browserImpersonation">
                    Browser fingerprint impersonation
                  </FieldLabel>
                  <FieldDescription>
                    Mimic a browser&apos;s TLS/JA3/JA4 and HTTP/2 fingerprint. Applies to both the
                    core and loader clients. Use a TLS target (https or wss) for this to take
                    effect.
                  </FieldDescription>
                </FieldContent>
              </Field>

              {browserImpersonation ? (
                <Field>
                  <FieldLabel htmlFor="impersonationProfile">Browser Profile</FieldLabel>
                  <Controller
                    name="impersonationProfile"
                    control={control}
                    render={({ field }) => (
                      <Select
                        items={BROWSER_IMPERSONATION_PROFILES}
                        value={field.value}
                        onValueChange={field.onChange}
                      >
                        <SelectTrigger id="impersonationProfile" className="w-full">
                          <SelectValue placeholder="Select a browser profile" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {BROWSER_IMPERSONATION_PROFILES.map((profile) => (
                              <SelectItem key={profile.value} value={profile.value}>
                                {profile.label}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    )}
                  />
                  <FieldDescription>
                    Fingerprint to impersonate. Defaults to Chrome (macOS) when left empty.
                  </FieldDescription>
                </Field>
              ) : null}

              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <Field className="md:col-span-2">
                  <FieldLabel htmlFor="clientCertificate">Client Certificate (PKCS#12)</FieldLabel>
                  <Controller
                    name="clientCertificate"
                    control={control}
                    render={({ field }) => (
                      <div className="space-y-2">
                        <Input
                          id="clientCertificate"
                          type="file"
                          accept=".p12,.pfx,application/x-pkcs12"
                          onChange={async (event) => {
                            const file = event.target.files?.[0];
                            if (!file) {
                              return;
                            }
                            try {
                              field.onChange(await readFileAsBase64(file));
                            } catch {
                              toast.error("Failed to read the certificate file");
                            }
                          }}
                        />
                        {field.value ? (
                          <div className="flex items-center gap-2 text-sm text-muted-foreground">
                            <span>Certificate configured.</span>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              onClick={() => field.onChange("")}
                            >
                              Remove
                            </Button>
                          </div>
                        ) : null}
                      </div>
                    )}
                  />
                  <FieldDescription>
                    Optional mutual TLS client certificate. Upload a .p12/.pfx keystore; it is
                    stored Base64-encoded in the shell configuration and used for both the core and
                    loader clients.
                  </FieldDescription>
                </Field>

                <Field className="md:col-span-2">
                  <FieldLabel htmlFor="clientCertificatePassword">Certificate Password</FieldLabel>
                  <Controller
                    name="clientCertificatePassword"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="clientCertificatePassword"
                        type="password"
                        autoComplete="new-password"
                        placeholder="Leave empty for an unprotected keystore"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription>
                    Password of the uploaded keystore. Its private key must use the same password.
                  </FieldDescription>
                </Field>
              </div>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <ShellFormActions
        isEdit={isEdit}
        isSubmitting={isSubmitting}
        isTesting={isTesting}
        onCancel={onCancel}
        onTestConnection={handleTestConnection}
      />
    </form>
  );
}

function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = typeof reader.result === "string" ? reader.result : "";
      // readAsDataURL returns "data:<mime>;base64,<payload>"; the server wants only the payload.
      const comma = result.indexOf(",");
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.onerror = () => reject(reader.error ?? new Error("Failed to read the file"));
    reader.readAsDataURL(file);
  });
}
