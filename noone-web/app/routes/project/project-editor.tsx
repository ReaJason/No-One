import type { User } from "@/types/admin";
import type { Project } from "@/types/project";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { PencilLine, Plus } from "lucide-react";
import { redirect, useActionData, useLoaderData, useNavigate } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { getProjectById, updateProject } from "@/api/project-api";
import { createProject } from "@/api/project-api";
import { getAllUsers } from "@/api/user-api";
import { FormPageShell } from "@/components/form-page-shell";
import { ProjectForm } from "@/components/project/project-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getProjectFormSeed,
  parseProjectFormData,
  type ProjectActionData,
} from "@/routes/project/project-form.shared";

interface LoaderData {
  project?: Project;
  users: User[];
}

export async function loader({
  context,
  params,
  request,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const authFetch = createAuthFetch(request, context);
  const usersPromise = getAllUsers(authFetch);
  const projectId = params.projectId as string | undefined;

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
  const parsed = parseProjectFormData(await request.formData());
  if (parsed.errors) {
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

export default function ProjectEditor() {
  const { project, users } = useLoaderData() as LoaderData;
  const actionData = useActionData() as ProjectActionData | undefined;
  const navigate = useNavigate();
  const isEdit = Boolean(project);
  const formSeed = actionData?.values ?? getProjectFormSeed(project);

  return (
    <FormPageShell
      backHref="/projects"
      backLabel="Return to project list"
      badges={[
        {
          label: isEdit ? "Edit mode" : "New project",
          variant: isEdit ? "secondary" : "default",
        },
        ...(project ? [{ label: project.status, variant: "outline" as const }] : []),
      ]}
      title={isEdit ? "Edit Project" : "Create Project"}
      description={
        project
          ? `Update business details, lifecycle information, and team membership for ${project.name}.`
          : "Register a project with business context, member assignments, and lifecycle notes in one structured form."
      }
    >
      <ProjectForm
        key={`${isEdit ? "edit" : "create"}:${JSON.stringify(formSeed)}`}
        mode={isEdit ? "edit" : "create"}
        icon={isEdit ? PencilLine : Plus}
        submitLabel={isEdit ? "Update Project" : "Create Project"}
        users={users}
        initialValues={formSeed}
        errors={actionData?.errors}
        onCancel={() => navigate("/projects")}
      />
    </FormPageShell>
  );
}
