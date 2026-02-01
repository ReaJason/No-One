import { ArrowLeft, ChevronDown, Edit, Settings, Wifi } from "lucide-react";
import { useState } from "react";
import type { LoaderFunctionArgs } from "react-router";
import { Form, useActionData, useLoaderData, useNavigate } from "react-router";
import { toast } from "sonner";
import { getAllProfiles } from "@/api/profile-api";
import { getAllProjects } from "@/api/project-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getShellConnectionById,
  testShellConfig,
} from "@/lib/shell-connection-api";
import type { Profile } from "@/types/profile";
import type { Project } from "@/types/project";
import type { ShellConnection } from "@/types/shell-connection";

export async function loader({ params }: LoaderFunctionArgs) {
  const shellId = params.shellId as string | undefined;
  if (!shellId) {
    throw new Response("Invalid shell ID", { status: 400 });
  }
  const [shell, projects, profiles] = await Promise.all([
    getShellConnectionById(shellId),
    getAllProjects(),
    getAllProfiles(),
  ]);
  if (!shell) {
    throw new Response("Shell connection not found", { status: 404 });
  }
  return { shell, projects, profiles } as {
    shell: ShellConnection;
    projects: Project[];
    profiles: Profile[];
  };
}

export const handle = createBreadcrumb(({ params }) => ({
  id: "shells-edit",
  label: "Edit Shell",
  to: params.shellId ? `/shells/edit/${params.shellId}` : "/shells",
}));

export default function EditShell() {
  const { shell, projects, profiles } = useLoaderData() as {
    shell: ShellConnection;
    projects: Project[];
    profiles: Profile[];
  };
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();
  const [projectId, setProjectId] = useState<string>(
    shell.projectId ? String(shell.projectId) : "none",
  );
  const [profileId, setProfileId] = useState<string>(String(shell.profileId));
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [skipSslVerify, setSkipSslVerify] = useState(
    shell.skipSslVerify ?? false,
  );
  const [url, setUrl] = useState(shell.url);
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

        <h1 className="text-3xl font-bold text-balance">Edit Shell</h1>
        <p className="text-muted-foreground mt-2">
          Update shell: <span className="font-semibold">#{shell.id}</span>
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Edit className="h-5 w-5" />
            Shell Information
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Form
            method="post"
            action={`/shells/update/${shell.id}`}
            className="space-y-6"
          >
            {actionData?.errors?.general ? (
              <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
                {actionData.errors.general}
              </div>
            ) : null}

            <div className="space-y-2">
              <Label htmlFor="url">Shell URL *</Label>
              <Input
                id="url"
                name="url"
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                className={actionData?.errors?.url ? "border-destructive" : ""}
                required
              />
              {actionData?.errors?.url ? (
                <p className="text-sm text-destructive">
                  {actionData.errors.url}
                </p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label>Profile *</Label>
              <input type="hidden" name="profileId" value={profileId} />
              <Select value={profileId} onValueChange={setProfileId}>
                <SelectTrigger
                  className={`w-full ${actionData?.errors?.profileId ? "border-destructive" : ""}`}
                >
                  <SelectValue placeholder="Select profile" />
                </SelectTrigger>
                <SelectContent>
                  {profiles.map((profile) => (
                    <SelectItem key={profile.id} value={String(profile.id)}>
                      {profile.name} ({profile.protocolType})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {actionData?.errors?.profileId ? (
                <p className="text-sm text-destructive">
                  {actionData.errors.profileId}
                </p>
              ) : null}
              <p className="text-xs text-muted-foreground">
                Profile determines the request format and protocol settings
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="group">Group</Label>
              <Input
                id="group"
                name="group"
                type="text"
                defaultValue={shell.group || ""}
                placeholder="Optional group"
              />
            </div>

            <div className="space-y-2">
              <Label>Project</Label>
              <input
                type="hidden"
                name="projectId"
                value={projectId === "none" ? "" : projectId}
              />
              <Select value={projectId} onValueChange={setProjectId}>
                <SelectTrigger className="w-64">
                  <SelectValue placeholder="Select project" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">No project</SelectItem>
                  {projects.map((project) => (
                    <SelectItem key={project.id} value={String(project.id)}>
                      {project.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {actionData?.errors?.projectId ? (
                <p className="text-sm text-destructive">
                  {actionData.errors.projectId}
                </p>
              ) : null}
            </div>

            {/* Advanced Settings */}
            <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
              <CollapsibleTrigger>
                <Button
                  variant="ghost"
                  type="button"
                  className="flex items-center gap-2 p-0 h-auto"
                >
                  <Settings className="h-4 w-4" />
                  <span>Advanced Settings</span>
                  <ChevronDown
                    className={`h-4 w-4 transition-transform ${advancedOpen ? "rotate-180" : ""}`}
                  />
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent className="space-y-4 pt-4">
                <div className="space-y-2">
                  <Label htmlFor="proxyUrl">Proxy URL</Label>
                  <Input
                    id="proxyUrl"
                    name="proxyUrl"
                    type="text"
                    defaultValue={shell.proxyUrl || ""}
                    placeholder="http://proxy:8080 or socks5://user:pass@proxy:1080"
                  />
                  <p className="text-xs text-muted-foreground">
                    Override the profile's proxy setting
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="customHeaders">Custom Headers (JSON)</Label>
                  <Input
                    id="customHeaders"
                    name="customHeaders"
                    type="text"
                    defaultValue={
                      shell.customHeaders
                        ? JSON.stringify(shell.customHeaders)
                        : ""
                    }
                    placeholder='{"Cookie": "session=xxx", "Authorization": "Bearer xxx"}'
                  />
                  {actionData?.errors?.customHeaders ? (
                    <p className="text-sm text-destructive">
                      {actionData.errors.customHeaders}
                    </p>
                  ) : null}
                  <p className="text-xs text-muted-foreground">
                    Additional headers to include in requests (merged with
                    profile headers)
                  </p>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="connectTimeoutMs">
                      Connect Timeout (ms)
                    </Label>
                    <Input
                      id="connectTimeoutMs"
                      name="connectTimeoutMs"
                      type="number"
                      defaultValue={shell.connectTimeoutMs || ""}
                      placeholder="30000"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="readTimeoutMs">Read Timeout (ms)</Label>
                    <Input
                      id="readTimeoutMs"
                      name="readTimeoutMs"
                      type="number"
                      defaultValue={shell.readTimeoutMs || ""}
                      placeholder="60000"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="maxRetries">Max Retries</Label>
                    <Input
                      id="maxRetries"
                      name="maxRetries"
                      type="number"
                      min="0"
                      defaultValue={shell.maxRetries || ""}
                      placeholder="0"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="retryDelayMs">Retry Delay (ms)</Label>
                    <Input
                      id="retryDelayMs"
                      name="retryDelayMs"
                      type="number"
                      min="0"
                      defaultValue={shell.retryDelayMs || ""}
                      placeholder="1000"
                    />
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  <input
                    type="hidden"
                    name="skipSslVerify"
                    value={skipSslVerify ? "on" : "off"}
                  />
                  <Checkbox
                    id="skipSslVerify"
                    checked={skipSslVerify}
                    onCheckedChange={(checked) =>
                      setSkipSslVerify(checked === true)
                    }
                  />
                  <Label
                    htmlFor="skipSslVerify"
                    className="text-sm font-normal"
                  >
                    Skip SSL certificate verification
                  </Label>
                </div>
                <p className="text-xs text-muted-foreground">
                  Enable this for self-signed certificates (not recommended for
                  production)
                </p>
              </CollapsibleContent>
            </Collapsible>

            <div className="flex gap-4 pt-4">
              <Button type="submit" className="flex items-center gap-2">
                <Edit className="h-4 w-4" />
                Update Shell
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
