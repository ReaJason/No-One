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
import { createAuthFetch } from "@/api.server";
import { createProject, type CreateProjectRequest } from "@/api/project-api";
import { getAllUsers } from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
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
  const name = (formData.get("name") as string)?.trim();
  const code = (formData.get("code") as string)?.trim();
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
    const authFetch = createAuthFetch(request, context);
    const payload: CreateProjectRequest = {
      name,
      code,
      memberIds: memberIds.length > 0 ? memberIds : undefined,
    };
    await createProject(payload, authFetch);
    return redirect("/projects");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to create project" },
      success: false,
    };
  }
}

export default function CreateProject() {
  const { users } = useLoaderData() as LoaderData;
  const navigate = useNavigate();
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const [query, setQuery] = useState("");
  const [selectedMemberIds, setSelectedMemberIds] = useState<Set<number>>(new Set());

  const filteredUsers = useMemo(() => {
    if (!query) return users;
    const lowerQuery = query.toLowerCase();
    return users.filter((user) => user.username.toLowerCase().includes(lowerQuery));
  }, [users, query]);

  const toggleMember = (userId: number, checked: boolean) => {
    setSelectedMemberIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(userId);
      } else {
        next.delete(userId);
      }
      return next;
    });
  };

  return (
    <div className="container mx-auto max-w-3xl p-6">
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
        <p className="mt-2 text-muted-foreground">Create the project and assign members</p>
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
            {[...selectedMemberIds].map((memberId) => (
              <input key={memberId} type="hidden" name="memberIds" value={memberId} />
            ))}

            {actionData?.errors?.general ? (
              <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
                {actionData.errors.general}
              </div>
            ) : null}

            <div className="space-y-2">
              <Label htmlFor="name">Project Name *</Label>
              <Input
                id="name"
                name="name"
                type="text"
                placeholder="Project name"
                className={actionData?.errors?.name ? "border-destructive" : undefined}
                required
              />
              {actionData?.errors?.name ? (
                <p className="text-sm text-destructive">{actionData.errors.name}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="code">Project Code *</Label>
              <Input
                id="code"
                name="code"
                type="text"
                placeholder="Unique code"
                className={actionData?.errors?.code ? "border-destructive" : undefined}
                required
              />
              {actionData?.errors?.code ? (
                <p className="text-sm text-destructive">{actionData.errors.code}</p>
              ) : null}
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <div className="space-y-1">
                  <Label>Members</Label>
                  <p className="text-sm text-muted-foreground">Select the users for this project</p>
                </div>
                <div className="relative">
                  <Input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Search users..."
                    className="h-8 w-56 pl-8"
                  />
                  <Search className="absolute top-1/2 left-2 size-4 -translate-y-1/2 text-muted-foreground" />
                </div>
              </div>

              <div className="rounded-md border">
                <ScrollArea className="h-[420px] p-3">
                  <div className="space-y-4">
                    {filteredUsers.map((user) => {
                      const isSelected = selectedMemberIds.has(user.id);

                      return (
                        <div key={user.id} className="rounded-lg border p-3">
                          <label className="flex items-center gap-3">
                            <Checkbox
                              checked={isSelected}
                              onCheckedChange={(checked) => toggleMember(user.id, Boolean(checked))}
                            />
                            <div>
                              <div className="font-medium">{user.username}</div>
                              <div className="text-sm text-muted-foreground">{user.email}</div>
                            </div>
                          </label>
                        </div>
                      );
                    })}

                    {filteredUsers.length === 0 ? (
                      <div className="text-sm text-muted-foreground">No users</div>
                    ) : null}
                  </div>
                </ScrollArea>
              </div>
            </div>

            <div className="flex gap-4 pt-4">
              <Button type="submit" className="flex items-center gap-2">
                <Plus className="h-4 w-4" />
                Create Project
              </Button>

              <Button type="button" variant="outline" onClick={() => navigate("/projects")}>
                Cancel
              </Button>
            </div>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}
