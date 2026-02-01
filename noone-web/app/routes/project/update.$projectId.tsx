import type { ActionFunctionArgs } from "react-router";
import { redirect } from "react-router";
import { toast } from "sonner";
import { updateProject } from "@/api/project-api";
import type { Project } from "@/types/project";

export async function action({ request, params }: ActionFunctionArgs) {
  const projectId = params.projectId as string | undefined;
  console.log("projectId", projectId);
  if (!projectId) {
    throw new Response("Invalid project ID", { status: 400 });
  }

  const formData = await request.formData();
  const name = (formData.get("name") as string)?.trim();
  const code = (formData.get("code") as string)?.trim();
  const status = formData.get("status") as string | null;
  const memberIds = (formData.getAll("memberIds") as string[])
    .map((id) => Number(id))
    .filter((n) => Number.isFinite(n));

  const errors: Record<string, string> = {};
  if (!name) errors.name = "Project name is required";
  if (!code) errors.code = "Project code is required";
  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    await updateProject(projectId, {
      name,
      code,
      status: status as Project["status"] | undefined,
      memberIds: memberIds.length > 0 ? memberIds : undefined,
    });
    toast.success("Project updated successfully");
    return redirect("/projects");
  } catch (error: any) {
    console.error("Error updating project:", error);
    toast.error(error?.message || "Failed to update project");
    return {
      errors: { general: error?.message || "Failed to update project" },
      success: false,
    };
  }
}
