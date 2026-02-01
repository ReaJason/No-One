import { ArrowLeft, Edit, Shield } from "lucide-react";
import type { LoaderFunctionArgs } from "react-router";
import {
  useActionData,
  useFetcher,
  useLoaderData,
  useNavigate,
} from "react-router";
import { getPermissionById } from "@/api/permission-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Permission } from "@/types/admin";

export async function loader({ params }: LoaderFunctionArgs) {
  const permissionId = parseInt(params.permissionId as string, 10);

  if (Number.isNaN(permissionId)) {
    throw new Response("Invalid permission ID", { status: 400 });
  }

  const permission = await getPermissionById(permissionId);

  if (!permission) {
    throw new Response("Permission not found", { status: 404 });
  }

  return { permission };
}

export default function EditPermission() {
  const { permission } = useLoaderData() as { permission: Permission };
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();
  const fetcher = useFetcher();

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

        <h1 className="text-3xl font-bold text-balance">Edit Permission</h1>
        <p className="text-muted-foreground mt-2">
          Update permission:{" "}
          <span className="font-semibold">{permission.name}</span>
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
          <fetcher.Form
            method="post"
            action={`/admin/permissions/update/${permission.id}`}
            className="space-y-6"
          >
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
                defaultValue={permission.name}
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
                defaultValue={permission.code}
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
                <Edit className="h-4 w-4" />
                Update Permission
              </Button>

              <Button
                type="button"
                variant="outline"
                onClick={() => navigate("/admin/permissions")}
              >
                Cancel
              </Button>
            </div>
          </fetcher.Form>
        </CardContent>
      </Card>
    </div>
  );
}
