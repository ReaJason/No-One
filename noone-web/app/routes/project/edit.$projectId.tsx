import { ArrowLeft, Edit, Search } from "lucide-react";
import { useMemo, useState } from "react";
import type { LoaderFunctionArgs } from "react-router";
import { Form, useActionData, useLoaderData, useNavigate } from "react-router";
import { getProjectById } from "@/api/project-api";
import { getAllUsers } from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { User } from "@/types/admin";
import type { Project } from "@/types/project";

export async function loader({ params }: LoaderFunctionArgs) {
  const projectId = params.projectId as string | undefined;
  if (!projectId) {
    throw new Response("Invalid project ID", { status: 400 });
  }
  const [project, users] = await Promise.all([
    getProjectById(projectId),
    getAllUsers(),
  ]);
  if (!project) {
    throw new Response("Project not found", { status: 404 });
  }
  return { project, users } as { project: Project; users: User[] };
}

export default function EditProject() {
  const { project, users } = useLoaderData() as {
    project: Project;
    users: User[];
  };
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();

  const [status, setStatus] = useState<Project["status"]>(project.status);
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

        <h1 className="text-3xl font-bold text-balance">Edit Project</h1>
        <p className="text-muted-foreground mt-2">
          Update project: <span className="font-semibold">{project.name}</span>
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Edit className="h-5 w-5" />
            Project Information
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Form
            method="post"
            action={`/projects/update/${project.id}`}
            className="space-y-6"
          >
            {actionData?.errors?.general && (
              <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
                {actionData.errors.general}
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="name">Project Name</Label>
              <Input
                id="name"
                name="name"
                type="text"
                defaultValue={project.name}
                required
                className={actionData?.errors?.name ? "border-destructive" : ""}
              />
              {actionData?.errors?.name && (
                <p className="text-sm text-destructive">
                  {actionData.errors.name}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="code">Project Code</Label>
              <Input
                id="code"
                name="code"
                type="text"
                defaultValue={project.code}
                required
                className={actionData?.errors?.code ? "border-destructive" : ""}
              />
              {actionData?.errors?.code && (
                <p className="text-sm text-destructive">
                  {actionData.errors.code}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label>Status</Label>
              <input type="hidden" name="status" value={status} />
              <Select
                value={status}
                onValueChange={(v) => setStatus(v as Project["status"])}
              >
                <SelectTrigger className="w-56">
                  <SelectValue placeholder="Select status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="draft">Draft</SelectItem>
                  <SelectItem value="active">Active</SelectItem>
                  <SelectItem value="inactive">Inactive</SelectItem>
                  <SelectItem value="archived">Archived</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <div className="space-y-1">
                  <Label>Members</Label>
                  <p className="text-sm text-muted-foreground">
                    Manage project members
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
                          defaultChecked={project.members?.some(
                            (m) => m.id === u.id,
                          )}
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
                <Edit className="h-4 w-4" />
                Update Project
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
