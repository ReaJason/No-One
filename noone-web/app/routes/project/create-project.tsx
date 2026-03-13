import { ArrowLeft, Plus } from "lucide-react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import { redirect, useActionData, useLoaderData, useNavigate } from "react-router";
import { createAuthFetch } from "@/api.server";
import { createProject } from "@/api/project-api";
import { ProjectForm } from "@/components/project/project-form";
import { Badge } from "@/components/ui/badge";
import { getAllUsers } from "@/api/user-api";
import { Button } from "@/components/ui/button";
import {
  getProjectFormSeed,
  parseProjectFormData,
  type ProjectActionData,
} from "@/routes/project/project-form.shared";
import type { User } from "@/types/admin";

interface LoaderData {
  users: User[];
}

export async function loader({ request, context }: LoaderFunctionArgs): Promise<LoaderData> {
  const authFetch = createAuthFetch(request, context);
  const users = await getAllUsers(authFetch);
  return { users };
}

export async function action({ request, context }: ActionFunctionArgs) {
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
    await createProject(parsed.payload, authFetch);
    return redirect("/projects");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to create project" },
      success: false,
      values: parsed.values,
    } satisfies ProjectActionData;
  }
}

export default function CreateProject() {
  const { users } = useLoaderData() as LoaderData;
  const navigate = useNavigate();
  const actionData = useActionData() as ProjectActionData | undefined;
  const formSeed = actionData?.values ?? getProjectFormSeed();

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
            <Badge>New project</Badge>
          </div>

          <div className="space-y-2">
            <h1 className="text-3xl font-bold text-balance">Create Project</h1>
            <p className="max-w-3xl text-sm text-muted-foreground sm:text-base">
              Register a project with business context, member assignments, and lifecycle notes in
              one structured form.
            </p>
          </div>
        </div>
      </section>

      <ProjectForm
        key={`create:${JSON.stringify(formSeed)}`}
        mode="create"
        icon={Plus}
        submitLabel="Create Project"
        users={users}
        initialValues={formSeed}
        errors={actionData?.errors}
        onCancel={() => navigate("/projects")}
      />
    </div>
  );
}
