import { ArrowLeft, FolderTree, Plus } from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import {
  Form,
  redirect,
  useActionData,
  useLoaderData,
  useNavigate,
} from "react-router";
import { getAllPermissions } from "@/api/permission-api";
import { type CreateRoleRequest, createRole } from "@/api/role-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import type { Permission } from "@/types/admin";

type LoaderData = {
  permissions: Permission[];
};

export async function loader(_args: LoaderFunctionArgs): Promise<LoaderData> {
  const permissions = await getAllPermissions();
  return { permissions };
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const name = (formData.get("name") as string)?.trim();
  const permissionIds = (formData.getAll("permissionIds") as string[])
    .map((id) => Number(id))
    .filter((n) => Number.isFinite(n));

  const errors: Record<string, string> = {};
  if (!name) errors.name = "Role name is required";
  if (permissionIds.length === 0)
    errors.permissionIds = "Select at least one permission";

  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    const payload: CreateRoleRequest = { name, permissionIds };
    await createRole(payload);
    return redirect("/admin/roles");
  } catch (error: any) {
    console.error("Error creating role:", error);
    return {
      errors: { general: error?.message || "Failed to create role" },
      success: false,
    };
  }
}

interface PermissionNode extends Permission {
  children?: PermissionNode[];
}

function buildCategoryTree(
  permissions: Permission[],
): { category: string; nodes: PermissionNode[] }[] {
  const byCategory: Record<string, Permission[]> = {};
  for (const p of permissions) {
    const key = p.category || "General";
    if (!byCategory[key]) byCategory[key] = [];
    byCategory[key].push(p);
  }
  return Object.entries(byCategory).map(([category, list]) => ({
    category,
    nodes: list.map((p) => ({ ...p })),
  }));
}

export default function CreateRole() {
  const { permissions } = useLoaderData() as LoaderData;
  const actionData = useActionData() as
    | { errors?: Record<string, string> }
    | undefined;
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  const grouped = useMemo(() => buildCategoryTree(permissions), [permissions]);

  const [selected, setSelected] = useState<Set<number>>(new Set());

  // Optimize: Use useCallback for stable callbacks (rerender-functional-setstate)
  const onToggle = useCallback((id: number, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  }, []);

  // Optimize: Use for...of instead of forEach to avoid lint error (js-early-exit)
  const onToggleAll = useCallback((ids: number[], checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) {
        for (const id of ids) next.add(id);
      } else {
        for (const id of ids) next.delete(id);
      }
      return next;
    });
  }, []);

  const filterMatch = useCallback(
    (name: string) => name.toLowerCase().includes(query.toLowerCase()),
    [query],
  );

  return (
    <div className="container mx-auto p-6 max-w-3xl">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={() => navigate("/admin/roles")}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to role list
        </Button>
        <h1 className="text-3xl font-bold text-balance">Create New Role</h1>
        <p className="text-muted-foreground mt-2">
          Define role name and assign permissions
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FolderTree className="h-5 w-5" />
            Role Information
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
              <Label htmlFor="name">Role Name</Label>
              <Input
                id="name"
                name="name"
                type="text"
                placeholder="Enter role name"
                required
                className={actionData?.errors?.name ? "border-destructive" : ""}
              />
              {actionData?.errors?.name && (
                <p className="text-sm text-destructive">
                  {actionData.errors.name}
                </p>
              )}
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <div className="space-y-1">
                  <Label>Permissions</Label>
                  {actionData?.errors?.permissionIds && (
                    <p className="text-sm text-destructive">
                      {actionData.errors.permissionIds}
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <Input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Search permissions..."
                    className="h-8 w-56"
                  />
                </div>
              </div>

              <div className="rounded-md border">
                <ScrollArea className="h-[360px] p-3">
                  <div className="space-y-4">
                    {grouped.map(({ category, nodes }) => {
                      const visible = nodes.filter(
                        (n) =>
                          !query || filterMatch(n.name) || filterMatch(n.code),
                      );
                      if (visible.length === 0) return null;

                      const ids = visible.map((n) => n.id);
                      const selectedCount = ids.filter((id) =>
                        selected.has(id),
                      ).length;
                      const allChecked =
                        selectedCount === ids.length && ids.length > 0;
                      const indeterminate =
                        selectedCount > 0 && selectedCount < ids.length;

                      return (
                        <div key={category} className="space-y-2">
                          <div className="flex items-center gap-2">
                            <Checkbox
                              id={`cat-${category}`}
                              checked={indeterminate || allChecked}
                              onCheckedChange={(c) =>
                                onToggleAll(ids, Boolean(c))
                              }
                            />
                            <Label
                              htmlFor={`cat-${category}`}
                              className="font-medium"
                            >
                              {category}
                            </Label>
                            <span className="text-xs text-muted-foreground">
                              {selectedCount}/{ids.length}
                            </span>
                          </div>
                          <div className="pl-6 grid grid-cols-1 sm:grid-cols-2 gap-2">
                            {visible.map((perm) => (
                              <div
                                key={perm.id}
                                className="flex items-center gap-2"
                              >
                                <Checkbox
                                  id={`perm-${perm.id}`}
                                  checked={selected.has(perm.id)}
                                  onCheckedChange={(c) =>
                                    onToggle(perm.id, Boolean(c))
                                  }
                                />
                                <Label
                                  htmlFor={`perm-${perm.id}`}
                                  className="text-sm font-normal"
                                >
                                  {perm.name}
                                  <span className="text-muted-foreground">
                                    {" "}
                                    ・ {perm.code}
                                  </span>
                                </Label>
                              </div>
                            ))}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </ScrollArea>
              </div>
              <p className="text-sm text-muted-foreground">
                Use the group checkbox to select/deselect a whole category.
                Search narrows the list in real-time.
              </p>
            </div>

            {/* Submit */}
            <div className="flex gap-4 pt-4">
              {/* Explicitly render selected ids as fields for non-JS/SSR compatibility */}
              {[...selected].map((id) => (
                <input
                  key={id}
                  type="hidden"
                  name="permissionIds"
                  value={String(id)}
                />
              ))}
              <Button type="submit" className="flex items-center gap-2">
                <Plus className="h-4 w-4" />
                Create Role
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => navigate("/admin/roles")}
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
