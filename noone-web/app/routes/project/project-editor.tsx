import type { Route } from "./+types/project-editor";
import type { User } from "@/types/admin";
import type { Project } from "@/types/project";

import { PencilLine, Plus } from "lucide-react";
import { useMemo } from "react";
import {
  type ActionFunctionArgs,
  isRouteErrorResponse,
  type LoaderFunctionArgs,
  redirect,
  useLoaderData,
  useNavigate,
  useParams,
} from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { createProject, getProjectById, updateProject } from "@/api/project-api";
import { getAllUsers } from "@/api/user-api";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { ProjectForm } from "@/components/project/project-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getDefaultValues,
  parseProjectFormData,
  type ProjectActionData,
} from "@/routes/project/project-form.shared";

type LoaderData = {
  project?: Project;
  users: User[];
};

export async function loader({
  context,
  params,
  request,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const authFetch = createAuthFetch(request, context);
  const usersPromise = getAllUsers(authFetch);
  const projectId = params.projectId;

  if (!projectId) {
    return { users: await usersPromise };
  }

  const [project, users] = await Promise.all([getProjectById(projectId, authFetch), usersPromise]);
  if (!project) {
    throw new Response("Project not found", { status: 404 });
  }

  return { project, users };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const mode = params.projectId ? "edit" : "create";
  const parsed = parseProjectFormData(await request.formData(), { mode });
  if ("errors" in parsed) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    } satisfies ProjectActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);
    if (params.projectId) {
      await updateProject(params.projectId, parsed.payload, authFetch);
    } else {
      await createProject(parsed.payload, authFetch);
    }

    return redirect("/projects");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to save project" },
      success: false,
      values: parsed.values,
    } satisfies ProjectActionData;
  }
}

export const handle = createBreadcrumb(({ params }) => {
  if (params.projectId) {
    return {
      id: "projects-edit",
      label: "Edit Project",
      to: `/projects/edit/${params.projectId}`,
    };
  }

  return {
    id: "projects-create",
    label: "Create Project",
    to: "/projects/create",
  };
});

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  const params = useParams();
  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <NotFoundErrorBoundary
        title={"Project not found"}
        backLabel={"Back to Project"}
        backHref={"/projects"}
        resourceType={"Project"}
        resourceId={params.projectId}
      />
    );
  }
  throw error;
}

export default function ProjectEditor() {
  const { project, users } = useLoaderData() as LoaderData;
  const navigate = useNavigate();
  const isEdit = Boolean(project);
  const mode = isEdit ? "edit" : "create";
  const initialValues = useMemo(() => getDefaultValues(project), [project]);

  const pageMeta = isEdit
    ? {
        badge: { label: "Edit mode", variant: "secondary" as const },
        description: `Update business details, lifecycle information, and team membership for ${project?.name}.`,
        icon: PencilLine,
        submitLabel: "Update Project",
        title: "Edit Project",
      }
    : {
        badge: { label: "New project", variant: "default" as const },
        description:
          "Register a project with business context, member assignments, and lifecycle notes in one structured form.",
        icon: Plus,
        submitLabel: "Create Project",
        title: "Create Project",
      };

  return (
    <FormPageShell
      backHref="/projects"
      backLabel="Return to project list"
      badges={[
        pageMeta.badge,
        ...(project ? [{ label: project.status, variant: "outline" as const }] : []),
      ]}
      title={pageMeta.title}
      description={pageMeta.description}
    >
      <ProjectForm
        mode={mode}
        icon={pageMeta.icon}
        submitLabel={pageMeta.submitLabel}
        users={users}
        initialValues={initialValues}
        onCancel={() => navigate(-1)}
      />
    </FormPageShell>
  );
}
