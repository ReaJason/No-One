import {ArrowLeft, User, Users} from "lucide-react";
import type {LoaderFunctionArgs} from "react-router";
import {useActionData, useFetcher, useLoaderData, useNavigate,} from "react-router";
import {getAllRoles} from "@/api/role-api";
import {getUserById} from "@/api/user-api";
import {Button} from "@/components/ui/button";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Checkbox} from "@/components/ui/checkbox";
import {Label} from "@/components/ui/label";
import type {Role, User as UserType} from "@/types/admin";

export async function loader({ params }: LoaderFunctionArgs) {
  const userId = parseInt(params.userId as string, 10);

  if (Number.isNaN(userId)) {
    throw new Response("Invalid user ID", { status: 400 });
  }

  // Load user data and available roles
  const [user, roles] = await Promise.all([getUserById(userId), getAllRoles()]);

  if (!user) {
    throw new Response("User not found", { status: 404 });
  }

  return { user, roles };
}

export default function EditUserRoles() {
  const { user, roles } = useLoaderData() as { user: UserType; roles: Role[] };
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();
  const fetcher = useFetcher();
  const currentRoleIds = user.roles?.map((role) => role.id);

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

        <h1 className="text-3xl font-bold text-balance">Edit User Roles</h1>
        <p className="text-muted-foreground mt-2">
          Update roles for user:{" "}
          <span className="font-semibold">{user.username}</span>
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Users className="h-5 w-5" />
            Role Assignment
          </CardTitle>
        </CardHeader>
        <CardContent>
          <fetcher.Form
            method="post"
            action={`/admin/users/update/${user.id}`}
            className="space-y-6"
          >
            {actionData?.errors?.general && (
              <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
                {actionData.errors.general}
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="roleIds">Select Roles *</Label>
              <div className="space-y-2">
                {roles.map((role) => (
                  <div key={role.id} className="flex items-center space-x-2">
                    <Checkbox
                      id={`role-${role.id}`}
                      name="roleIds"
                      value={role.id}
                      defaultChecked={currentRoleIds?.includes(role.id)}
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
              <Button type="submit" className="flex items-center gap-2">
                <User className="h-4 w-4" />
                Update Roles
              </Button>

              <Button
                type="button"
                variant="outline"
                onClick={() => navigate("/admin/users")}
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
