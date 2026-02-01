import { ArrowLeft, Plus, Search } from "lucide-react";
import { useMemo, useState } from "react";
import {
  type ActionFunctionArgs,
  Form,
  type LoaderFunctionArgs,
  redirect,
  useActionData,
  useLoaderData,
  useNavigate,
} from "react-router";
import { toast } from "sonner";
import { type CreateProjectRequest, createProject } from "@/api/project-api";
import { getAllUsers } from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import type { User } from "@/types/admin";

export async function loader(_args: LoaderFunctionArgs) {
  const users = await getAllUsers();
  return { users } as { users: User[] };
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const name = (formData.get("name") as string)?.trim();
  const code = (formData.get("code") as string)?.trim();
  const memberIds = (formData.getAll("memberIds") as string[])
    .map((id) => Number(id))
    .filter((n) => Number.isFinite(n));
  const errors: Record<string, string> = {};
  if (!name) errors.name = "Project name is required";
  if (!code) errors.code = "Project code is required";
  if (Object.keys(errors).length > 0) return { errors, success: false };
  try {
    const payload: CreateProjectRequest = {
      name,
      code,
      memberIds: memberIds.length > 0 ? memberIds : undefined,
    };
    await createProject(payload);
    toast.success("Project created successfully");
    return redirect("/projects");
  } catch (error: any) {
    toast.error(error?.message || "Failed to create project");
    return {
      errors: { general: error?.message || "Failed to create project" },
      success: false,
    };
  }
}

export default function CreateProject() {
  const { users } = useLoaderData() as { users: User[] };
  const navigate = useNavigate();
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => {
    if (!query) return users;
    const q = query.toLowerCase();
    return users.filter((u) => u.username.toLowerCase().includes(q));
  }, [users, query]);

  return (
    <div className="container mx-auto p-6 max-w-2xl">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={() => navigate("/projects")}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to project list
        </Button>

        <h1 className="text-3xl font-bold text-balance">Create New Project</h1>
        <p className="text-muted-foreground mt-2">
          Fill in project basic information
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Plus className="h-5 w-5" />
            Project Information
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Form method="post" className="space-y-6">
            {actionData?.errors?.general && (
              <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
                {actionData.errors.general}
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="name">Project Name *</Label>
              <Input
                id="name"
                name="name"
                type="text"
                placeholder="Please enter project name"
                className={
                  actionData?.errors?.name ? "border-destructive" : "w-full"
                }
                required
              />
              {actionData?.errors?.name && (
                <p className="text-sm text-destructive">
                  {actionData.errors.name}
                </p>
              )}
              <p className="text-sm text-muted-foreground">
                The project name will be used to identify and manage your
                project
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="code">Project Code *</Label>
              <Input
                id="code"
                name="code"
                type="text"
                placeholder="Unique code"
                className={
                  actionData?.errors?.code ? "border-destructive" : "w-full"
                }
                required
              />
              {actionData?.errors?.code && (
                <p className="text-sm text-destructive">
                  {actionData.errors.code}
                </p>
              )}
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <div className="space-y-1">
                  <Label>Members</Label>
                  <p className="text-sm text-muted-foreground">
                    Select one or more users for this project
                  </p>
                </div>
                <div className="relative">
                  <Input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Search users..."
                    className="h-8 w-56 pl-8"
                  />
                  <Search className="absolute left-2 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                </div>
              </div>
              <div className="rounded-md border">
                <ScrollArea className="h-[280px] p-3">
                  <div className="space-y-2">
                    {filtered.map((u) => (
                      <label key={u.id} className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          name="memberIds"
                          value={u.id}
                          className="h-4 w-4 rounded border-gray-300"
                        />
                        <span className="text-sm">{u.username}</span>
                      </label>
                    ))}
                    {filtered.length === 0 && (
                      <div className="text-sm text-muted-foreground">
                        No users
                      </div>
                    )}
                  </div>
                </ScrollArea>
              </div>
            </div>

            <div className="flex gap-4 pt-4">
              <Button type="submit" className="flex items-center gap-2">
                <Plus className="h-4 w-4" />
                Create Project
              </Button>

              <Button
                type="button"
                variant="outline"
                onClick={() => navigate("/projects")}
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
