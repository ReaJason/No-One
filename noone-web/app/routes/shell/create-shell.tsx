import type { Profile } from "@/types/profile";
import type { Project } from "@/types/project";
import type { ShellConnection, ShellLanguage } from "@/types/shell-connection";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { useEffect, useRef, useState } from "react";
import {
  Form,
  redirect,
  useActionData,
  useFetcher,
  useLoaderData,
  useNavigate,
  useNavigation,
  useParams,
} from "react-router";
import { toast } from "sonner";

import { createAuthFetch } from "@/api.server";
import { getAllProfiles } from "@/api/profile-api";
import { getAllProjects } from "@/api/project-api";
import {
  createShellConnection,
  type CreateShellConnectionRequest,
  getShellConnectionById,
  testShellConfig,
  type TestShellConfigRequest,
  updateShellConnection,
  type UpdateShellConnectionRequest,
} from "@/api/shell-connection-api";
import { FormPageShell } from "@/components/form-page-shell";
import ShellFormActions from "@/components/shell/shell-form-actions";
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
import { createBreadcrumb } from "@/lib/breadcrumb-utils";

type LoaderData =
  | {
      shell: ShellConnection;
      projects: Project[];
      profiles: Profile[];
    }
  | {
      shell?: undefined;
      shellUrlParam: string;
      profileIdParam: string;
      loaderProfileIdParam: string;
      shellTypeParam: string;
      stagingParam: boolean;
      languageParam?: ShellLanguage;
      projects: Project[];
      profiles: Profile[];
      initialProjectId?: number;
    };

type ShellActionData = {
  errors?: Record<string, string>;
  success?: boolean;
};

type ShellFormSeed = {
  name: string;
  url: string;
  shellType: string;
  staging: boolean;
  projectId: string;
  profileId: string;
  loaderProfileId: string;
  language: ShellLanguage;
  proxyUrl: string;
  customHeaders: string;
  connectTimeoutMs: string;
  readTimeoutMs: string;
  skipSslVerify: boolean;
  maxRetries: string;
  retryDelayMs: string;
};

type SelectOption = {
  label: string;
  value: string;
};

const LANGUAGE_ITEMS: SelectOption[] = [
  { label: "Java", value: "java" },
  { label: "NodeJs", value: "nodejs" },
  { label: "DotNet", value: "dotnet" },
];

const COMMON_SHELL_TYPES = [
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

export async function loader({ request, context, params }: LoaderFunctionArgs) {
  const shellId = params.shellId;
  const authFetch = createAuthFetch(request, context);
  const [projects, profiles] = await Promise.all([
    getAllProjects(authFetch),
    getAllProfiles(authFetch),
  ]);

  if (shellId) {
    const shell = await getShellConnectionById(shellId, authFetch);
    if (!shell) {
      throw new Response("Shell connection not found", { status: 404 });
    }

    return { shell, projects, profiles } satisfies LoaderData;
  }

  const url = new URL(request.url);
  const shellUrlParam = url.searchParams.get("shellUrl") ?? "";
  const profileIdParam = url.searchParams.get("profileId") ?? "";
  const loaderProfileIdParam = url.searchParams.get("loaderProfileId") ?? "";
  const shellTypeParam = (url.searchParams.get("shellType") ?? "").trim();
  const stagingParamRaw = (url.searchParams.get("staging") ?? "").trim().toLowerCase();
  const languageParamRaw = (url.searchParams.get("language") ?? "").trim().toLowerCase();
  const projectIdParam = url.searchParams.get("projectId") ?? "";
  const parsedProjectId = Number(projectIdParam);
  return {
    shellUrlParam,
    profileIdParam,
    loaderProfileIdParam,
    shellTypeParam,
    stagingParam: stagingParamRaw === "true" || stagingParamRaw === "1" || stagingParamRaw === "on",
    languageParam: isShellLanguage(languageParamRaw) ? languageParamRaw : undefined,
    projects,
    profiles,
    initialProjectId:
      Number.isFinite(parsedProjectId) && parsedProjectId !== 0 ? parsedProjectId : undefined,
  } satisfies LoaderData;
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const formData = await request.formData();
  const intent = formData.get("intent");
  const authFetch = createAuthFetch(request, context);

  if (intent === "test-config") {
    const parsed = parseTestConfigFormData(formData);
    if (parsed.errors) {
      return { errors: parsed.errors, success: false } satisfies ShellActionData;
    }

    try {
      const result = await testShellConfig(parsed.payload, authFetch);
      return { success: result.connected } satisfies ShellActionData;
    } catch (error: any) {
      return {
        errors: {
          general: error?.message || "Connection test failed",
        },
        success: false,
      } satisfies ShellActionData;
    }
  }

  const shellId = params.shellId;

  if (shellId) {
    const parsed = parseShellFormData(formData, { isEdit: true });
    if (parsed.errors) {
      return { errors: parsed.errors, success: false } satisfies ShellActionData;
    }

    try {
      await updateShellConnection(shellId, parsed.payload, authFetch);
      return redirect("/shells");
    } catch (error: any) {
      return {
        errors: {
          general: error?.message || "Failed to update shell connection",
        },
        success: false,
      } satisfies ShellActionData;
    }
  }

  const parsed = parseShellFormData(formData, { isEdit: false });
  if (parsed.errors) {
    return { errors: parsed.errors, success: false } satisfies ShellActionData;
  }

  try {
    await createShellConnection(parsed.payload, authFetch);
    return redirect("/shells");
  } catch (error: any) {
    return {
      errors: {
        general: error?.message || "Failed to create shell connection",
      },
      success: false,
    } satisfies ShellActionData;
  }
}

export const handle = createBreadcrumb(({ params }) => {
  if (params.shellId) {
    return {
      id: "shells-edit",
      label: "Edit Shell",
      to: `/shells/edit/${params.shellId}`,
    };
  }

  return {
    id: "shells-create",
    label: "Create Shell",
    to: "/shells/create",
  };
});

export default function CreateOrEditShell() {
  const loaderData = useLoaderData() as LoaderData;
  const actionData = useActionData() as ShellActionData | undefined;
  const navigate = useNavigate();
  const params = useParams();
  const shell = loaderData.shell;
  const isEdit = Boolean(params.shellId);
  const hasPrefill = hasIncomingPrefill(loaderData);

  const profileItems = loaderData.profiles.map((profile) => ({
    label: `${profile.name} (${profile.protocolType})`,
    value: String(profile.id),
  }));
  const projectItems = loaderData.projects.map((project) => ({
    label: project.name,
    value: String(project.id),
  }));

  const formSeed = getShellFormSeed(loaderData);
  const formKey = shell
    ? `edit:${shell.id}:${shell.updatedAt}`
    : `create:${formSeed.url}:${formSeed.profileId}:${formSeed.loaderProfileId}:${formSeed.language}:${formSeed.projectId}:${formSeed.shellType}:${formSeed.staging}`;

  return (
    <FormPageShell
      backHref="/shells"
      backLabel="Return to shell list"
      badges={[
        {
          label: isEdit ? "Edit mode" : "New connection",
          variant: isEdit ? "secondary" : "default",
        },
        ...(hasPrefill ? [{ label: "Pre-filled", variant: "outline" as const }] : []),
      ]}
      title={isEdit ? "Edit Shell" : "Create Shell"}
      description={
        shell
          ? `Update shell #${shell.id} and verify the connection details before saving.`
          : "Register a new shell connection with a clearer overview of runtime, profile, and transport settings."
      }
    >
      <ShellForm
        key={formKey}
        formSeed={formSeed}
        errors={actionData?.errors}
        isEdit={isEdit}
        isPrefilled={hasPrefill}
        projectItems={projectItems}
        profileItems={profileItems}
        onCancel={() => navigate("/shells")}
      />
    </FormPageShell>
  );
}

type ShellFormProps = {
  formSeed: ShellFormSeed;
  errors?: Record<string, string>;
  isEdit: boolean;
  isPrefilled: boolean;
  projectItems: SelectOption[];
  profileItems: SelectOption[];
  onCancel: () => void;
};

function ShellForm({
  formSeed,
  errors,
  isEdit,
  isPrefilled,
  projectItems,
  profileItems,
  onCancel,
}: ShellFormProps) {
  const formRef = useRef<HTMLFormElement>(null);
  const navigation = useNavigation();
  const testFetcher = useFetcher<ShellActionData>();
  const [projectId, setProjectId] = useState(formSeed.projectId);
  const [profileId, setProfileId] = useState(formSeed.profileId);
  const [loaderProfileId, setLoaderProfileId] = useState(formSeed.loaderProfileId);
  const [shellType, setShellType] = useState(formSeed.shellType);
  const [staging, setStaging] = useState(formSeed.staging);
  const [language, setLanguage] = useState<ShellLanguage>(formSeed.language);
  const [skipSslVerify, setSkipSslVerify] = useState(formSeed.skipSslVerify);

  const isSubmitting =
    navigation.state !== "idle" && navigation.formMethod?.toLowerCase() === "post";
  const isTesting = testFetcher.state !== "idle";

  useEffect(() => {
    if (testFetcher.state !== "idle" || !testFetcher.data) {
      return;
    }

    if (testFetcher.data.success) {
      toast.success("Connection test successful");
      return;
    }

    if (testFetcher.data.errors?.general) {
      toast.error(testFetcher.data.errors.general);
    }
  }, [testFetcher.data, testFetcher.state]);

  useEffect(() => {
    if (staging && !loaderProfileId) {
      setLoaderProfileId(profileId);
    }
  }, [loaderProfileId, profileId, staging]);

  const handleTestConnection = () => {
    const formElement = formRef.current;
    if (!formElement) {
      toast.error("Unable to read shell form");
      return;
    }

    const formData = new FormData(formElement);
    const currentUrl = readTrimmedField(formData, "url");
    const currentProfileId = readTrimmedField(formData, "profileId");

    if (!currentUrl || !currentProfileId) {
      toast.error("URL and Profile are required to test connection");
      return;
    }

    formData.set("intent", "test-config");
    testFetcher.submit(formData, { method: "post" });
  };

  return (
    <Form ref={formRef} method="post" className="space-y-6">
      {errors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {errors.general}
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
                <Field data-invalid={Boolean(errors?.name)}>
                  <FieldLabel htmlFor="name">Name *</FieldLabel>
                  <Input
                    id="name"
                    name="name"
                    type="text"
                    defaultValue={formSeed.name}
                    placeholder="TestShell"
                    aria-invalid={Boolean(errors?.name) || undefined}
                    aria-describedby="name-description"
                    required
                  />
                  <FieldDescription id="name-description">Shell name for search.</FieldDescription>
                  <FieldError>{errors?.name}</FieldError>
                </Field>

                <Field data-invalid={Boolean(errors?.language)}>
                  <FieldLabel htmlFor="language">Language *</FieldLabel>
                  <input type="hidden" name="language" value={language} />
                  <Select
                    items={LANGUAGE_ITEMS}
                    value={language}
                    onValueChange={(value) => {
                      if (value && isShellLanguage(value)) {
                        setLanguage(value);
                      }
                    }}
                    disabled={isPrefilled}
                  >
                    <SelectTrigger
                      id="language"
                      aria-invalid={Boolean(errors?.language) || undefined}
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
                  <FieldDescription>
                    Language determines plugin runtime and encoding.
                  </FieldDescription>
                  <FieldError>{errors?.language}</FieldError>
                </Field>

                <Field data-invalid={Boolean(errors?.url)} className="md:col-span-2">
                  <FieldLabel htmlFor="url">Shell URL *</FieldLabel>
                  <Input
                    id="url"
                    name="url"
                    type="url"
                    defaultValue={formSeed.url}
                    placeholder="http://example.com/shell.jsp"
                    aria-invalid={Boolean(errors?.url) || undefined}
                    aria-describedby="url-description"
                    required
                  />
                  <FieldDescription id="url-description">
                    Target endpoint that accepts your payloads.
                  </FieldDescription>
                  <FieldError>{errors?.url}</FieldError>
                </Field>

                <Field data-invalid={Boolean(errors?.profileId)}>
                  <FieldLabel htmlFor="profileId">Profile *</FieldLabel>
                  <input type="hidden" name="profileId" value={profileId} />
                  <Select
                    items={profileItems}
                    value={profileId}
                    onValueChange={(value) => setProfileId(value ?? "")}
                    disabled={isPrefilled}
                  >
                    <SelectTrigger
                      id="profileId"
                      aria-invalid={Boolean(errors?.profileId) || undefined}
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
                  <FieldDescription>
                    Profile determines the request format and protocol settings.
                  </FieldDescription>
                  <FieldError>{errors?.profileId}</FieldError>
                </Field>

                <Field data-invalid={Boolean(errors?.shellType)}>
                  <FieldLabel htmlFor="shellType">Shell Type</FieldLabel>
                  <Input
                    id="shellType"
                    name="shellType"
                    type="text"
                    list="shell-type-options"
                    value={shellType}
                    onChange={(event) => setShellType(event.target.value)}
                    placeholder="Servlet"
                    aria-invalid={Boolean(errors?.shellType) || undefined}
                    disabled={isPrefilled}
                  />
                  <datalist id="shell-type-options">
                    {COMMON_SHELL_TYPES.map((item) => (
                      <option key={item} value={item} />
                    ))}
                  </datalist>
                  <FieldDescription>
                    Runtime shell type, such as `Servlet`, `Filter`, or `NettyHandler`.
                  </FieldDescription>
                  <FieldError>{errors?.shellType}</FieldError>
                </Field>

                <Field data-invalid={Boolean(errors?.projectId)}>
                  <FieldLabel htmlFor="projectId">Project</FieldLabel>
                  <input type="hidden" name="projectId" value={projectId} />
                  <Select
                    items={projectItems}
                    value={projectId}
                    onValueChange={(value) => setProjectId(value ?? "")}
                  >
                    <SelectTrigger
                      id="projectId"
                      aria-invalid={Boolean(errors?.projectId) || undefined}
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
                  <FieldDescription>Optional grouping for organizing shells.</FieldDescription>
                  <FieldError>{errors?.projectId}</FieldError>
                </Field>
              </div>

              <Field orientation="horizontal">
                <input type="hidden" name="staging" value={staging ? "on" : "off"} />
                <Checkbox
                  id="staging"
                  checked={staging}
                  onCheckedChange={(checked) => setStaging(checked === true)}
                  disabled={isPrefilled}
                />
                <FieldContent>
                  <FieldLabel htmlFor="staging">Use staging loader</FieldLabel>
                  <FieldDescription>
                    Enable two-stage loading and attach a dedicated loader profile.
                  </FieldDescription>
                </FieldContent>
              </Field>

              {staging ? (
                <Field data-invalid={Boolean(errors?.loaderProfileId)}>
                  <FieldLabel htmlFor="loaderProfileId">Loader Profile *</FieldLabel>
                  <input type="hidden" name="loaderProfileId" value={loaderProfileId} />
                  <Select
                    items={profileItems}
                    value={loaderProfileId}
                    onValueChange={(value) => setLoaderProfileId(value ?? "")}
                    disabled={isPrefilled}
                  >
                    <SelectTrigger
                      id="loaderProfileId"
                      aria-invalid={Boolean(errors?.loaderProfileId) || undefined}
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
                  <FieldDescription>
                    Loader profile used before the staged core payload is activated.
                  </FieldDescription>
                  <FieldError>{errors?.loaderProfileId}</FieldError>
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
                  <Input
                    id="proxyUrl"
                    name="proxyUrl"
                    type="text"
                    defaultValue={formSeed.proxyUrl}
                    placeholder="http://proxy:8080 or socks5://user:pass@proxy:1080"
                  />
                  <FieldDescription>Override the profile&apos;s proxy setting.</FieldDescription>
                </Field>

                <Field data-invalid={Boolean(errors?.customHeaders)} className="md:col-span-2">
                  <FieldLabel htmlFor="customHeaders">Custom Headers (JSON)</FieldLabel>
                  <Input
                    id="customHeaders"
                    name="customHeaders"
                    type="text"
                    defaultValue={formSeed.customHeaders}
                    placeholder='{"Cookie": "session=xxx", "Authorization": "Bearer xxx"}'
                    aria-invalid={Boolean(errors?.customHeaders) || undefined}
                  />
                  <FieldDescription>
                    Additional headers merged with profile headers.
                  </FieldDescription>
                  <FieldError>{errors?.customHeaders}</FieldError>
                </Field>

                <Field>
                  <FieldLabel htmlFor="connectTimeoutMs">Connect Timeout (ms)</FieldLabel>
                  <Input
                    id="connectTimeoutMs"
                    name="connectTimeoutMs"
                    type="number"
                    defaultValue={formSeed.connectTimeoutMs}
                    placeholder="30000"
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="readTimeoutMs">Read Timeout (ms)</FieldLabel>
                  <Input
                    id="readTimeoutMs"
                    name="readTimeoutMs"
                    type="number"
                    defaultValue={formSeed.readTimeoutMs}
                    placeholder="60000"
                  />
                </Field>

                <Field>
                  <FieldLabel htmlFor="maxRetries">Max Retries</FieldLabel>
                  <Input
                    id="maxRetries"
                    name="maxRetries"
                    type="number"
                    min="0"
                    defaultValue={formSeed.maxRetries}
                    placeholder="0"
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="retryDelayMs">Retry Delay (ms)</FieldLabel>
                  <Input
                    id="retryDelayMs"
                    name="retryDelayMs"
                    type="number"
                    min="0"
                    defaultValue={formSeed.retryDelayMs}
                    placeholder="1000"
                  />
                </Field>
              </div>

              <Field orientation="horizontal">
                <input type="hidden" name="skipSslVerify" value={skipSslVerify ? "on" : "off"} />
                <Checkbox
                  id="skipSslVerify"
                  checked={skipSslVerify}
                  onCheckedChange={(checked) => setSkipSslVerify(checked === true)}
                />
                <FieldContent>
                  <FieldLabel htmlFor="skipSslVerify">Skip SSL certificate verification</FieldLabel>
                  <FieldDescription>
                    Enable for self-signed certificates (not recommended for production).
                  </FieldDescription>
                </FieldContent>
              </Field>
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
    </Form>
  );
}

function getShellFormSeed(loaderData: LoaderData): ShellFormSeed {
  if (loaderData.shell) {
    const shell = loaderData.shell;

    return {
      name: shell.name ?? "",
      url: shell.url ?? "",
      shellType: shell.shellType ?? "",
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

  const firstProfileId = loaderData.profiles[0]?.id;
  const firstProjectId = loaderData.projects[0]?.id;

  return {
    name: "",
    url: loaderData.shellUrlParam,
    shellType: loaderData.shellTypeParam,
    staging: loaderData.stagingParam,
    projectId:
      loaderData.initialProjectId != null
        ? String(loaderData.initialProjectId)
        : firstProjectId != null
          ? String(firstProjectId)
          : "",
    profileId: loaderData.profileIdParam || (firstProfileId != null ? String(firstProfileId) : ""),
    loaderProfileId:
      loaderData.loaderProfileIdParam ||
      loaderData.profileIdParam ||
      (firstProfileId != null ? String(firstProfileId) : ""),
    language: loaderData.languageParam ?? "java",
    proxyUrl: "",
    customHeaders: "",
    connectTimeoutMs: "",
    readTimeoutMs: "",
    skipSslVerify: false,
    maxRetries: "",
    retryDelayMs: "",
  };
}

function hasIncomingPrefill(loaderData: LoaderData) {
  return (
    !loaderData.shell &&
    Boolean(
      loaderData.shellUrlParam ||
      loaderData.profileIdParam ||
      loaderData.loaderProfileIdParam ||
      loaderData.shellTypeParam ||
      loaderData.stagingParam ||
      loaderData.languageParam ||
      loaderData.initialProjectId != null,
    )
  );
}

function parseTestConfigFormData(
  formData: FormData,
):
  | { payload: TestShellConfigRequest; errors?: undefined }
  | { payload?: undefined; errors: Record<string, string> } {
  const url = readTrimmedField(formData, "url");
  const shellType = readTextField(formData, "shellType");
  const staging = formData.get("staging") === "on";
  const language = parseLanguage(readTrimmedField(formData, "language"));
  const profileId = parseFiniteNumber(readTrimmedField(formData, "profileId"));
  const loaderProfileId = parseFiniteNumber(readTrimmedField(formData, "loaderProfileId"));
  const proxyUrl = readTrimmedField(formData, "proxyUrl");
  const connectTimeoutMs = parseFiniteNumber(readTrimmedField(formData, "connectTimeoutMs"));
  const readTimeoutMs = parseFiniteNumber(readTrimmedField(formData, "readTimeoutMs"));
  const maxRetries = parseFiniteNumber(readTrimmedField(formData, "maxRetries"));
  const retryDelayMs = parseFiniteNumber(readTrimmedField(formData, "retryDelayMs"));
  const skipSslVerify = formData.get("skipSslVerify") === "on";

  if (!url || profileId === undefined) {
    return { errors: { general: "URL and Profile are required to test connection" } };
  }
  if (staging && loaderProfileId === undefined) {
    return { errors: { general: "Loader Profile is required when staging is enabled" } };
  }

  const parsedHeaders = parseCustomHeaders(readTrimmedField(formData, "customHeaders"));
  if (parsedHeaders.error) {
    return { errors: { general: parsedHeaders.error } };
  }

  return {
    payload: {
      url,
      staging: staging || undefined,
      shellType: shellType || undefined,
      language: language ?? "java",
      profileId,
      loaderProfileId: staging ? loaderProfileId : undefined,
      proxyUrl,
      customHeaders: parsedHeaders.value,
      connectTimeoutMs,
      readTimeoutMs,
      skipSslVerify: skipSslVerify || undefined,
      maxRetries,
      retryDelayMs,
    },
  };
}

function parseShellFormData(
  formData: FormData,
  options: { isEdit: true },
):
  | { payload: UpdateShellConnectionRequest; errors?: undefined }
  | { payload?: undefined; errors: Record<string, string> };
function parseShellFormData(
  formData: FormData,
  options: { isEdit: false },
):
  | { payload: CreateShellConnectionRequest; errors?: undefined }
  | { payload?: undefined; errors: Record<string, string> };
function parseShellFormData(
  formData: FormData,
  options: { isEdit: boolean },
):
  | { payload: CreateShellConnectionRequest | UpdateShellConnectionRequest; errors?: undefined }
  | { payload?: undefined; errors: Record<string, string> } {
  const errors: Record<string, string> = {};
  const name = readTrimmedField(formData, "name");
  const url = readTrimmedField(formData, "url");
  const shellType = readTextField(formData, "shellType");
  const staging = formData.get("staging") === "on";
  const language = parseLanguage(readTrimmedField(formData, "language"));
  const profileId = parseFiniteNumber(readTrimmedField(formData, "profileId"));
  const loaderProfileId = parseFiniteNumber(readTrimmedField(formData, "loaderProfileId"));
  const proxyUrl = readTrimmedField(formData, "proxyUrl");
  const connectTimeoutMs = parseFiniteNumber(readTrimmedField(formData, "connectTimeoutMs"));
  const readTimeoutMs = parseFiniteNumber(readTrimmedField(formData, "readTimeoutMs"));
  const maxRetries = parseFiniteNumber(readTrimmedField(formData, "maxRetries"));
  const retryDelayMs = parseFiniteNumber(readTrimmedField(formData, "retryDelayMs"));
  const skipSslVerify = formData.get("skipSslVerify") === "on";
  const projectIdRaw = readTrimmedField(formData, "projectId");

  if (!name) errors.name = "Name is required";
  if (!url) errors.url = "URL is required";
  if (!language) errors.language = "Language is required";
  if (profileId === undefined) {
    errors.profileId = "Profile is required";
  }
  if (staging && loaderProfileId === undefined) {
    errors.loaderProfileId = "Loader Profile is required when staging is enabled";
  }

  let projectId: number | null | undefined;
  if (!projectIdRaw) {
    projectId = options.isEdit ? null : undefined;
  } else {
    projectId = parseFiniteNumber(projectIdRaw);
    if (projectId === undefined) {
      errors.projectId = "Project ID must be a number";
    }
  }

  const parsedHeaders = parseCustomHeaders(readTrimmedField(formData, "customHeaders"));
  if (parsedHeaders.error) {
    errors.customHeaders = parsedHeaders.error;
  }

  if (Object.keys(errors).length > 0) {
    return { errors };
  }

  if (options.isEdit) {
    return {
      payload: {
        name,
        url,
        staging,
        shellType: shellType ?? null,
        language: language as ShellLanguage,
        projectId,
        profileId: profileId as number,
        loaderProfileId: staging ? (loaderProfileId ?? null) : null,
        proxyUrl,
        customHeaders: parsedHeaders.value,
        connectTimeoutMs,
        readTimeoutMs,
        skipSslVerify: skipSslVerify || undefined,
        maxRetries,
        retryDelayMs,
      },
    };
  }

  return {
    payload: {
      name: name as string,
      url: url as string,
      staging: staging || undefined,
      shellType: shellType || undefined,
      language: language as ShellLanguage,
      projectId: projectId ?? undefined,
      profileId: profileId as number,
      loaderProfileId: staging ? loaderProfileId : undefined,
      proxyUrl,
      customHeaders: parsedHeaders.value,
      connectTimeoutMs,
      readTimeoutMs,
      skipSslVerify: skipSslVerify || undefined,
      maxRetries,
      retryDelayMs,
    },
  };
}

function parseCustomHeaders(raw: string | undefined) {
  if (!raw) {
    return { value: undefined };
  }

  try {
    return { value: JSON.parse(raw) as Record<string, string> };
  } catch {
    return { error: "Invalid JSON format for custom headers" };
  }
}

function readTrimmedField(formData: FormData, name: string) {
  const value = formData.get(name);
  if (typeof value !== "string") {
    return undefined;
  }

  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function readTextField(formData: FormData, name: string) {
  const value = formData.get(name);
  if (typeof value !== "string") {
    return undefined;
  }

  return value.trim();
}

function parseFiniteNumber(raw: string | undefined) {
  if (!raw) {
    return undefined;
  }

  const value = Number(raw);
  return Number.isFinite(value) ? value : undefined;
}

function parseLanguage(raw: string | undefined) {
  return raw && isShellLanguage(raw) ? raw : undefined;
}

function isShellLanguage(value: string): value is ShellLanguage {
  return value === "java" || value === "nodejs" || value === "dotnet";
}
