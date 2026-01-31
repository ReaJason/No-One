import {ArrowLeft, Plus, Wifi} from "lucide-react";
import {useState} from "react";
import type {ActionFunctionArgs, LoaderFunctionArgs} from "react-router";
import {Form, redirect, useActionData, useLoaderData, useNavigate,} from "react-router";
import {toast} from "sonner";
import {getAllProfiles} from "@/api/profile-api";
import {getAllProjects} from "@/api/project-api";
import {Button} from "@/components/ui/button";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Checkbox} from "@/components/ui/checkbox";
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
import {Input} from "@/components/ui/input";
import {Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue,} from "@/components/ui/select";
import {createBreadcrumb} from "@/lib/breadcrumb-utils";
import {createShellConnection, testShellConfig,} from "@/lib/shell-connection-api";
import type {Profile} from "@/types/profile";
import type {Project} from "@/types/project";

export async function loader({ request }: LoaderFunctionArgs) {
  const url = new URL(request.url);
  const projectIdParam = url.searchParams.get("projectId") ?? "";
  const parsedProjectId = Number(projectIdParam);
  const [projects, profiles] = await Promise.all([
    getAllProjects(),
    getAllProfiles(),
  ]);
  return {
    projects,
    profiles,
    initialProjectId: Number.isFinite(parsedProjectId)
      ? parsedProjectId
      : undefined,
  } as { projects: Project[]; profiles: Profile[]; initialProjectId?: number };
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const url = (formData.get("url") as string)?.trim();
  const group = (formData.get("group") as string)?.trim();
  const projectIdRaw = (formData.get("projectId") as string)?.trim();
  const projectId = projectIdRaw ? Number(projectIdRaw) : undefined;
  const profileIdRaw = (formData.get("profileId") as string)?.trim();
  const profileId = profileIdRaw ? Number(profileIdRaw) : undefined;

  // Advanced settings
  const proxyUrl = (formData.get("proxyUrl") as string)?.trim() || undefined;
  const connectTimeoutMsRaw = (
    formData.get("connectTimeoutMs") as string
  )?.trim();
  const readTimeoutMsRaw = (formData.get("readTimeoutMs") as string)?.trim();
  const maxRetriesRaw = (formData.get("maxRetries") as string)?.trim();
  const retryDelayMsRaw = (formData.get("retryDelayMs") as string)?.trim();
  const connectTimeoutMs = connectTimeoutMsRaw
    ? Number(connectTimeoutMsRaw)
    : undefined;
  const readTimeoutMs = readTimeoutMsRaw ? Number(readTimeoutMsRaw) : undefined;
  const maxRetries = maxRetriesRaw ? Number(maxRetriesRaw) : undefined;
  const retryDelayMs = retryDelayMsRaw ? Number(retryDelayMsRaw) : undefined;
  const skipSslVerify = formData.get("skipSslVerify") === "on";

  // Parse custom headers JSON
  const customHeadersRaw = (formData.get("customHeaders") as string)?.trim();
  let customHeaders: Record<string, string> | undefined;
  if (customHeadersRaw) {
    try {
      customHeaders = JSON.parse(customHeadersRaw);
    } catch {
      return {
        errors: { customHeaders: "Invalid JSON format for custom headers" },
        success: false,
      };
    }
  }

  const errors: Record<string, string> = {};
  if (!url) errors.url = "URL is required";
  if (profileId === undefined || !Number.isFinite(profileId)) {
    errors.profileId = "Profile is required";
  }
  if (projectIdRaw && !Number.isFinite(projectId)) {
    errors.projectId = "Project ID must be a number";
  }

  if (Object.keys(errors).length > 0) return { errors, success: false };

  if (profileId === undefined) {
    return { errors: { profileId: "Profile is required" }, success: false };
  }

  try {
    await createShellConnection({
      url,
      group: group || undefined,
      projectId,
      profileId,
      proxyUrl,
      customHeaders,
      connectTimeoutMs,
      readTimeoutMs,
      skipSslVerify: skipSslVerify || undefined,
      maxRetries,
      retryDelayMs,
    });
    toast.success("Shell connection created successfully");
    return redirect("/shells");
  } catch (error: any) {
    toast.error(error?.message || "Failed to create shell connection");
    return {
      errors: {
        general: error?.message || "Failed to create shell connection",
      },
      success: false,
    };
  }
}

export const handle = createBreadcrumb(() => ({
  id: "shells-create",
  label: "Create Shell",
  to: "/shells/create",
}));

export default function CreateShell() {
  const { projects, profiles, initialProjectId } = useLoaderData() as {
    projects: Project[];
    profiles: Profile[];
    initialProjectId?: number;
  };
  const profileItems = profiles.map((profile) => ({
    label: `${profile.name} (${profile.protocolType})`,
    value: String(profile.id),
  }));
  const projectItems = projects.map((project) => ({
    label: project.name,
    value: String(project.id),
  }));
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const errors = actionData?.errors;
  const navigate = useNavigate();
  const [projectId, setProjectId] = useState<string>(
    initialProjectId ? String(initialProjectId) : "",
  );
  const [profileId, setProfileId] = useState<string>("");
  const [skipSslVerify, setSkipSslVerify] = useState(false);
  const [url, setUrl] = useState("");
  const [isTesting, setIsTesting] = useState(false);

  const handleTestConnection = async () => {
    if (!url || !profileId) {
      toast.error("URL and Profile are required to test connection");
      return;
    }

    setIsTesting(true);
    try {
      const formElement = document.querySelector("form") as HTMLFormElement;
      const formData = new FormData(formElement);

      let customHeaders: Record<string, string> | undefined;
      const customHeadersRaw = (
        formData.get("customHeaders") as string
      )?.trim();
      if (customHeadersRaw) {
        try {
          customHeaders = JSON.parse(customHeadersRaw);
        } catch {
          toast.error("Invalid JSON format for custom headers");
          setIsTesting(false);
          return;
        }
      }

      const connectTimeoutMsRaw = formData.get("connectTimeoutMs") as string;
      const readTimeoutMsRaw = formData.get("readTimeoutMs") as string;
      const maxRetriesRaw = formData.get("maxRetries") as string;
      const retryDelayMsRaw = formData.get("retryDelayMs") as string;

      const result = await testShellConfig({
        url,
        profileId: Number(profileId),
        proxyUrl: (formData.get("proxyUrl") as string)?.trim() || undefined,
        customHeaders,
        connectTimeoutMs: connectTimeoutMsRaw
          ? Number(connectTimeoutMsRaw)
          : undefined,
        readTimeoutMs: readTimeoutMsRaw ? Number(readTimeoutMsRaw) : undefined,
        skipSslVerify: skipSslVerify || undefined,
        maxRetries: maxRetriesRaw ? Number(maxRetriesRaw) : undefined,
        retryDelayMs: retryDelayMsRaw ? Number(retryDelayMsRaw) : undefined,
      });

      if (result.connected) {
        toast.success("Connection test successful");
      } else {
        toast.error("Connection test failed");
      }
    } catch (error: any) {
      toast.error(error?.message || "Connection test failed");
    } finally {
      setIsTesting(false);
    }
  };

  return (
    <div className="container mx-auto p-6 max-w-6xl">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={() => navigate("/shells")}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to shell list
        </Button>

        <h1 className="text-3xl font-bold text-balance">Create Shell</h1>
        <p className="text-muted-foreground mt-2">
          Register a new shell connection
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Plus className="h-5 w-5" />
            Shell Information
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Form method="post" className="space-y-6">
            {errors?.general ? (
              <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
                {errors.general}
              </div>
            ) : null}

            <FieldSet>
              <FieldLegend>Basic</FieldLegend>
              <FieldGroup>
                <Field data-invalid={Boolean(errors?.url)}>
                  <FieldLabel htmlFor="url">Shell URL *</FieldLabel>
                  <Input
                    id="url"
                    name="url"
                    type="url"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
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

                <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
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
                            <SelectItem
                              key={project.value}
                              value={project.value}
                            >
                              {project.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                    <FieldDescription>
                      Optional grouping for organizing shells.
                    </FieldDescription>
                    <FieldError>{errors?.projectId}</FieldError>
                  </Field>

                  <Field>
                    <FieldLabel htmlFor="group">Group</FieldLabel>
                    <Input
                      id="group"
                      name="group"
                      type="text"
                      placeholder="Optional group"
                    />
                  </Field>
                </div>
              </FieldGroup>
            </FieldSet>

            <FieldSet>
              <FieldLegend>Connection</FieldLegend>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="proxyUrl">Proxy URL</FieldLabel>
                  <Input
                    id="proxyUrl"
                    name="proxyUrl"
                    type="text"
                    placeholder="http://proxy:8080 or socks5://user:pass@proxy:1080"
                  />
                  <FieldDescription>
                    Override the profile&apos;s proxy setting.
                  </FieldDescription>
                </Field>

                <Field data-invalid={Boolean(errors?.customHeaders)}>
                  <FieldLabel htmlFor="customHeaders">
                    Custom Headers (JSON)
                  </FieldLabel>
                  <Input
                    id="customHeaders"
                    name="customHeaders"
                    type="text"
                    placeholder='{"Cookie": "session=xxx", "Authorization": "Bearer xxx"}'
                    aria-invalid={Boolean(errors?.customHeaders) || undefined}
                  />
                  <FieldDescription>
                    Additional headers merged with profile headers.
                  </FieldDescription>
                  <FieldError>{errors?.customHeaders}</FieldError>
                </Field>

                <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                  <Field>
                    <FieldLabel htmlFor="connectTimeoutMs">
                      Connect Timeout (ms)
                    </FieldLabel>
                    <Input
                      id="connectTimeoutMs"
                      name="connectTimeoutMs"
                      type="number"
                      placeholder="30000"
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="readTimeoutMs">
                      Read Timeout (ms)
                    </FieldLabel>
                    <Input
                      id="readTimeoutMs"
                      name="readTimeoutMs"
                      type="number"
                      placeholder="60000"
                    />
                  </Field>
                </div>

                <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                  <Field>
                    <FieldLabel htmlFor="maxRetries">Max Retries</FieldLabel>
                    <Input
                      id="maxRetries"
                      name="maxRetries"
                      type="number"
                      min="0"
                      placeholder="0"
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="retryDelayMs">
                      Retry Delay (ms)
                    </FieldLabel>
                    <Input
                      id="retryDelayMs"
                      name="retryDelayMs"
                      type="number"
                      min="0"
                      placeholder="1000"
                    />
                  </Field>
                </div>

                <Field orientation="horizontal">
                  <input
                    type="hidden"
                    name="skipSslVerify"
                    value={skipSslVerify ? "on" : "off"}
                  />
                  <Checkbox
                    id="skipSslVerify"
                    checked={skipSslVerify}
                    onCheckedChange={(checked) => setSkipSslVerify(checked)}
                  />
                  <FieldContent>
                    <FieldLabel htmlFor="skipSslVerify">
                      Skip SSL certificate verification
                    </FieldLabel>
                    <FieldDescription>
                      Enable for self-signed certificates (not recommended for
                      production).
                    </FieldDescription>
                  </FieldContent>
                </Field>
              </FieldGroup>
            </FieldSet>

            <div className="flex gap-4 pt-4">
              <Button type="submit" className="flex items-center gap-2">
                <Plus className="h-4 w-4" />
                Create Shell
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={handleTestConnection}
                disabled={isTesting || !url || !profileId}
                className="flex items-center gap-2"
              >
                <Wifi
                  className={`h-4 w-4 ${isTesting ? "animate-pulse" : ""}`}
                />
                {isTesting ? "Testing..." : "Test Connection"}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => navigate("/shells")}
              >
                Cancel
              </Button>
            </div>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}
