import type { Profile } from "@/types/profile";
import type { Project } from "@/types/project";
import type { ShellConnection, ShellLanguage } from "@/types/shell-connection";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { useMemo } from "react";
import { redirect, useLoaderData, useNavigate, useParams } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getAllProfiles } from "@/api/profile-api";
import { getAllProjects } from "@/api/project-api";
import {
  type CreateShellConnectionRequest,
  createShellConnection,
  getShellConnectionById,
  testShellConfig,
  updateShellConnection,
} from "@/api/shell-connection-api";
import { FormPageShell } from "@/components/form-page-shell";
import { ShellForm } from "@/components/shell/shell-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getDefaultValues,
  parseShellFormData,
  parseTestConfigFormData,
  type ShellActionData,
  type ShellFormPrefill,
} from "@/routes/shell/shell-form.shared";

type LoaderData = {
  shell?: ShellConnection;
  projects: Project[];
  profiles: Profile[];
  prefill?: ShellFormPrefill;
};

function isShellLanguage(value: string): value is ShellLanguage {
  return value === "java" || value === "nodejs" || value === "dotnet";
}

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
  const interfaceNameParam = url.searchParams.get("interfaceName") ?? "";
  const parsedProjectId = Number(projectIdParam);

  const prefill: ShellFormPrefill = {
    url: shellUrlParam || undefined,
    profileId: profileIdParam || undefined,
    loaderProfileId: loaderProfileIdParam || undefined,
    shellType: shellTypeParam || undefined,
    staging:
      stagingParamRaw === "true" || stagingParamRaw === "1" || stagingParamRaw === "on"
        ? true
        : undefined,
    language: isShellLanguage(languageParamRaw) ? languageParamRaw : undefined,
    projectId:
      Number.isFinite(parsedProjectId) && parsedProjectId !== 0 ? parsedProjectId : undefined,
    interfaceName: interfaceNameParam || undefined,
    firstProfileId: profiles[0]?.id,
    firstProjectId: projects[0]?.id,
  };

  return { projects, profiles, prefill } satisfies LoaderData;
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const formData = await request.formData();
  const intent = formData.get("intent");
  const authFetch = createAuthFetch(request, context);

  if (intent === "test-config") {
    const parsed = parseTestConfigFormData(formData);
    if ("errors" in parsed) {
      return { errors: parsed.errors, success: false } satisfies ShellActionData;
    }

    try {
      const result = await testShellConfig(parsed.payload, authFetch);
      return { success: result.connected } satisfies ShellActionData;
    } catch (error: any) {
      return {
        errors: { general: error?.message || "Connection test failed" },
        success: false,
      } satisfies ShellActionData;
    }
  }

  const shellId = params.shellId;
  const mode = shellId ? "edit" : "create";
  const parsed = parseShellFormData(formData, { mode });

  if ("errors" in parsed) {
    return { errors: parsed.errors, success: false } satisfies ShellActionData;
  }

  try {
    if (shellId) {
      await updateShellConnection(shellId, parsed.payload, authFetch);
    } else {
      await createShellConnection(parsed.payload as CreateShellConnectionRequest, authFetch);
    }
    return redirect("/shells");
  } catch (error: any) {
    return {
      errors: {
        general:
          error?.message ||
          (shellId ? "Failed to update shell connection" : "Failed to create shell connection"),
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
  const navigate = useNavigate();
  const params = useParams();
  const { shell, profiles, projects, prefill } = loaderData;
  const isEdit = Boolean(params.shellId);
  const hasPrefill = hasIncomingPrefill(prefill);

  const profileItems = useMemo(
    () =>
      profiles.map((profile) => ({
        label: `${profile.name} (${profile.protocolType})`,
        value: String(profile.id),
      })),
    [profiles],
  );

  const projectItems = useMemo(
    () =>
      projects.map((project) => ({
        label: project.name,
        value: String(project.id),
      })),
    [projects],
  );

  const initialValues = useMemo(() => getDefaultValues(shell, prefill), [shell, prefill]);

  const formKey = shell
    ? `edit:${shell.id}:${shell.updatedAt}`
    : `create:${initialValues.url}:${initialValues.profileId}:${initialValues.loaderProfileId}:${initialValues.language}:${initialValues.projectId}:${initialValues.shellType}:${initialValues.staging}`;

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
        mode={isEdit ? "edit" : "create"}
        initialValues={initialValues}
        isPrefilled={hasPrefill}
        profileItems={profileItems}
        projectItems={projectItems}
        onCancel={() => navigate("/shells")}
      />
    </FormPageShell>
  );
}

function hasIncomingPrefill(prefill?: ShellFormPrefill) {
  if (!prefill) return false;
  return Boolean(
    prefill.url ||
    prefill.profileId ||
    prefill.loaderProfileId ||
    prefill.shellType ||
    prefill.staging ||
    prefill.language ||
    prefill.projectId != null,
  );
}
