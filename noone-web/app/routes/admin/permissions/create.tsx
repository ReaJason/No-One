import { ArrowLeft, Plus, Shield } from "lucide-react";
import type { ActionFunctionArgs } from "react-router";
import { Form, redirect, useActionData, useNavigate } from "react-router";
import { toast } from "sonner";
import { createPermission } from "@/api/permission-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const name = formData.get("name") as string;
  const code = formData.get("code") as string;

  // Validation
  const errors: Record<string, string> = {};

  if (!name?.trim()) {
    errors.name = "Permission name is required";
  }
  if (!code?.trim()) {
    errors.code = "Permission code is required";
  } else if (!/^[a-zA-Z0-9:_-]+$/.test(code.trim())) {
    errors.code =
      "Permission code can only contain letters, numbers, colons, underscores, and hyphens";
  }

  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    const permissionData = {
      name: name.trim(),
      code: code.trim(),
    };

    await createPermission(permissionData);
    toast.success("Permission created successfully");
    return redirect("/admin/permissions");
  } catch (error: any) {
    console.error("Error creating permission:", error);
    toast.error(error.message || "Failed to create permission");
    return {
      errors: { general: error.message || "Failed to create permission" },
      success: false,
    };
  }
}

export default function CreatePermission() {
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();

  return (
    <div className="container mx-auto p-6 max-w-2xl">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={() => navigate("/admin/permissions")}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to permission list
        </Button>

        <h1 className="text-3xl font-bold text-balance">
          Create New Permission
        </h1>
        <p className="text-muted-foreground mt-2">
          Add a new permission to the system
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Shield className="h-5 w-5" />
            Permission Information
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
              <Label htmlFor="name">Permission Name *</Label>
              <Input
                id="name"
                name="name"
                type="text"
                placeholder="Enter permission name"
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
              <Label htmlFor="code">Permission Code *</Label>
              <Input
                id="code"
                name="code"
                type="text"
                placeholder="e.g., user:create, admin:manage"
                required
                className={actionData?.errors?.code ? "border-destructive" : ""}
              />
              {actionData?.errors?.code && (
                <p className="text-sm text-destructive">
                  {actionData.errors.code}
                </p>
              )}
              <p className="text-sm text-muted-foreground">
                Use format like "module:action" (e.g., user:create,
                admin:manage)
              </p>
            </div>

            <div className="flex gap-4 pt-4">
              <Button type="submit" className="flex items-center gap-2">
                <Plus className="h-4 w-4" />
                Create Permission
              </Button>

              <Button
                type="button"
                variant="outline"
                onClick={() => navigate("/admin/permissions")}
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
