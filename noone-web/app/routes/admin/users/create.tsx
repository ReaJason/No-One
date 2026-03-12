import { ArrowLeft, Plus, User } from "lucide-react";
import { useCallback, useState } from "react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import { Form, redirect, useActionData, useLoaderData, useNavigate } from "react-router";
import { createAuthFetch } from "@/api.server";
import { getAllRoles } from "@/api/role-api";
import { createUser } from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { Role, UserStatus } from "@/types/admin";

export async function loader({ request, context }: LoaderFunctionArgs) {
  const authFetch = createAuthFetch(request, context);
  const roles = await getAllRoles(authFetch);
  return {
    roles: roles.map(({ id, name }) => ({ id, name })),
  };
}

export async function action({ request, context }: ActionFunctionArgs) {
  const formData = await request.formData();
  const username = formData.get("username") as string;
  const password = formData.get("password") as string;
  const email = formData.get("email") as string;
  const status = formData.get("status") as UserStatus;
  const roleIds = (formData.getAll("roleIds") as string[]).map((id) => Number.parseInt(id, 10));

  const errors: Record<string, string> = {};

  if (!username?.trim()) {
    errors.username = "Username is required";
  }
  if (!password?.trim()) {
    errors.password = "Password is required";
  } else if (password.length < 6) {
    errors.password = "Password must be at least 6 characters";
  }
  if (!email?.trim()) {
    errors.email = "Email is required";
  } else if (!email.includes("@")) {
    errors.email = "Email format is invalid";
  }
  if (!status) {
    errors.status = "Status is required";
  }
  if (roleIds.length === 0) {
    errors.roleIds = "At least one role must be selected";
  } else if (roleIds.some(Number.isNaN)) {
    errors.roleIds = "Invalid role id";
  }

  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    const authFetch = createAuthFetch(request, context);
    const userData = {
      username: username.trim(),
      password,
      email: email.trim(),
      status,
      roleIds,
    };
    await createUser(userData, authFetch);
    return redirect("/admin/users");
  } catch (error: any) {
    return {
      errors: { general: error.message || "Failed to create user" },
      success: false,
    };
  }
}

export default function CreateUser() {
  const { roles } = useLoaderData() as { roles: Pick<Role, "id" | "name">[] };
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();
  const [status] = useState<UserStatus>("UNACTIVATED");

  const handleNavigateToUsers = useCallback(() => {
    navigate("/admin/users");
  }, [navigate]);

  return (
    <div className="container mx-auto max-w-6xl p-6">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={handleNavigateToUsers}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to user list
        </Button>

        <h1 className="text-3xl font-bold text-balance">Create New User</h1>
        <p className="mt-2 text-muted-foreground">
          Create an administrator-managed account with a temporary password and assigned roles
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
            {actionData?.errors?.general ? (
              <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
                {actionData.errors.general}
              </div>
            ) : null}

            <div className="space-y-2">
              <Label htmlFor="username">Username *</Label>
              <Input
                id="username"
                name="username"
                type="text"
                placeholder="Enter username"
                required
                className={actionData?.errors?.username ? "border-destructive" : ""}
              />
              {actionData?.errors?.username ? (
                <p className="text-sm text-destructive">{actionData.errors.username}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password *</Label>
              <Input
                id="password"
                name="password"
                type="password"
                placeholder="Enter password"
                required
                className={actionData?.errors?.password ? "border-destructive" : ""}
              />
              {actionData?.errors?.password ? (
                <p className="text-sm text-destructive">{actionData.errors.password}</p>
              ) : null}
              <p className="text-sm text-muted-foreground">
                The user must replace this temporary password during first login
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">Email *</Label>
              <Input
                id="email"
                name="email"
                type="email"
                placeholder="Enter email"
                required
                className={actionData?.errors?.email ? "border-destructive" : ""}
              />
              {actionData?.errors?.email ? (
                <p className="text-sm text-destructive">{actionData.errors.email}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="status">Initial Status *</Label>
              <input type="hidden" name="status" value={status} />
              <Select value={status} disabled>
                <SelectTrigger id="status" className="w-full">
                  <SelectValue placeholder="Select status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="UNACTIVATED">Unactivated</SelectItem>
                </SelectContent>
              </Select>
              {actionData?.errors?.status ? (
                <p className="text-sm text-destructive">{actionData.errors.status}</p>
              ) : null}
              <p className="text-sm text-muted-foreground">
                Newly created accounts must complete password change and 2FA binding before access
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
                      value={String(role.id)}
                      className="rounded border-gray-300"
                    />
                    <Label htmlFor={`role-${role.id}`} className="text-sm font-normal">
                      {role.name}
                    </Label>
                  </div>
                ))}
              </div>
              {actionData?.errors?.roleIds ? (
                <p className="text-sm text-destructive">{actionData.errors.roleIds}</p>
              ) : null}
              <p className="text-sm text-muted-foreground">
                Select one or more roles for this user
              </p>
            </div>

            <div className="flex gap-4 pt-4">
              <Button type="submit">
                <Plus className="h-4 w-4" />
                Create User
              </Button>

              <Button type="button" variant="outline" onClick={handleNavigateToUsers}>
                Cancel
              </Button>
            </div>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}
