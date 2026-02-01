import { ArrowLeft, Plus, User } from "lucide-react";
import type { ActionFunctionArgs } from "react-router";
import {
  Form,
  redirect,
  useActionData,
  useLoaderData,
  useNavigate,
} from "react-router";
import { toast } from "sonner";
import { getAllRoles } from "@/api/role-api";
import { createUser } from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Role } from "@/types/admin";

export async function loader() {
  // Load roles for the select dropdown
  const roles = await getAllRoles();
  return { roles };
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const username = formData.get("username") as string;
  const password = formData.get("password") as string;
  const roleIds = formData.getAll("roleIds") as string[];

  // Validation
  const errors: Record<string, string> = {};

  if (!username?.trim()) {
    errors.username = "Username is required";
  }
  if (!password?.trim()) {
    errors.password = "Password is required";
  } else if (password.length < 6) {
    errors.password = "Password must be at least 6 characters";
  }
  if (roleIds.length === 0) {
    errors.roleIds = "At least one role must be selected";
  }

  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    const userData = {
      username: username.trim(),
      password: password,
      enabled: true,
      roleIds,
    };
    toast.success("User created successfully");

    await createUser(userData);
    return redirect("/admin/users");
  } catch (error: any) {
    console.error("Error creating user:", error);
    toast.error(error.message || "Failed to create user");
    return {
      errors: { general: error.message || "Failed to create user" },
      success: false,
    };
  }
}

export default function CreateUser() {
  const { roles } = useLoaderData() as { roles: Role[] };
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();
  return (
    <div className="container mx-auto p-6 max-w-2xl">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={() => navigate("/admin/users")}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to user list
        </Button>

        <h1 className="text-3xl font-bold text-balance">Create New User</h1>
        <p className="text-muted-foreground mt-2">
          Fill in user basic information and assign roles
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <User className="h-5 w-5" />
            User Information
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
              <Label htmlFor="username">Username *</Label>
              <Input
                id="username"
                name="username"
                type="text"
                placeholder="Enter username"
                required
                className={
                  actionData?.errors?.username ? "border-destructive" : ""
                }
              />
              {actionData?.errors?.username && (
                <p className="text-sm text-destructive">
                  {actionData.errors.username}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password *</Label>
              <Input
                id="password"
                name="password"
                type="password"
                placeholder="Enter password"
                required
                className={
                  actionData?.errors?.password ? "border-destructive" : ""
                }
              />
              {actionData?.errors?.password && (
                <p className="text-sm text-destructive">
                  {actionData.errors.password}
                </p>
              )}
              <p className="text-sm text-muted-foreground">
                Password must be at least 6 characters long
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="roleIds">Roles *</Label>
              <div className="space-y-2">
                {roles.map((role) => (
                  <div key={role.id} className="flex items-center space-x-2">
                    <Checkbox
                      id={`role-${role.id}`}
                      name="roleIds"
                      value={role.id}
                      className="rounded border-gray-300"
                    />
                    <Label
                      htmlFor={`role-${role.id}`}
                      className="text-sm font-normal"
                    >
                      {role.name}
                    </Label>
                  </div>
                ))}
              </div>
              {actionData?.errors?.roleIds && (
                <p className="text-sm text-destructive">
                  {actionData.errors.roleIds}
                </p>
              )}
              <p className="text-sm text-muted-foreground">
                Select one or more roles for this user
              </p>
            </div>

            <div className="flex gap-4 pt-4">
              <Button type="submit">
                <Plus className="h-4 w-4" />
                Create User
              </Button>

              <Button
                type="button"
                variant="outline"
                onClick={() => navigate("/admin/users")}
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
