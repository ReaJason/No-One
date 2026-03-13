import { ArrowLeft, PencilLine } from "lucide-react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import { redirect, useActionData, useLoaderData, useNavigate } from "react-router";
import { createAuthFetch } from "@/api.server";
import { getProjectById, updateProject } from "@/api/project-api";
import { getAllUsers } from "@/api/user-api";
import { ProjectForm } from "@/components/project/project-form";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  getProjectFormSeed,
  parseProjectFormData,
  type ProjectActionData,
} from "@/routes/project/project-form.shared";
import type { User } from "@/types/admin";
import type { Project } from "@/types/project";

interface LoaderData {
  project: Project;
  users: User[];
}

export async function loader({
  context,
  params,
  request,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const projectId = params.projectId as string | undefined;
  if (!projectId) {
    throw new Response("Invalid project ID", { status: 400 });
  }

  const authFetch = createAuthFetch(request, context);
  const [project, users] = await Promise.all([
    getProjectById(projectId, authFetch),
    getAllUsers(authFetch),
  ]);
  if (!project) {
    throw new Response("Project not found", { status: 404 });
  }

  return { project, users };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const projectId = params.projectId as string | undefined;
  if (!projectId) {
    throw new Response("Invalid project ID", { status: 400 });
  }

  const formData = await request.formData();
  const parsed = parseProjectFormData(formData);
  if (parsed.errors) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    } satisfies ProjectActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await updateProject(projectId, parsed.payload, authFetch);
    return redirect("/projects");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to update project" },
      success: false,
      values: parsed.values,
    } satisfies ProjectActionData;
  }
}

export default function EditProject() {
  const { project, users } = useLoaderData() as LoaderData;
  const actionData = useActionData() as ProjectActionData | undefined;
  const navigate = useNavigate();
  const formSeed = actionData?.values ?? getProjectFormSeed(project);

  return (
    <div className="container mx-auto max-w-7xl p-6">
      <section className="mb-8 rounded-2xl border border-border/70 bg-gradient-to-br from-muted/40 via-background to-background p-6 shadow-sm">
        <Button
          variant="ghost"
          onClick={() => navigate("/projects")}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to project list
        </Button>

        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">Edit mode</Badge>
            <Badge variant="outline">{project.status}</Badge>
          </div>

          <div className="space-y-2">
            <h1 className="text-3xl font-bold text-balance">Edit Project</h1>
            <p className="max-w-3xl text-sm text-muted-foreground sm:text-base">
              Update business details, lifecycle information, and team membership for{" "}
              <span className="font-semibold">{project.name}</span>.
            </p>
          </div>
        </div>
      </section>

      <ProjectForm
        key={`edit:${project.id}:${JSON.stringify(formSeed)}`}
        mode="edit"
        icon={PencilLine}
        submitLabel="Update Project"
        users={users}
        initialValues={formSeed}
        errors={actionData?.errors}
        onCancel={() => navigate("/projects")}
      />
    </div>
  );
}
