import { ArrowLeft, Edit, FolderTree } from "lucide-react";
import { useMemo, useState } from "react";
import type { LoaderFunctionArgs } from "react-router";
import {
  useActionData,
  useFetcher,
  useLoaderData,
  useNavigate,
} from "react-router";
import { getAllPermissions } from "@/api/permission-api";
import { getRoleById } from "@/api/role-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import type { Permission, Role } from "@/types/admin";

export async function loader({ params }: LoaderFunctionArgs) {
  const roleId = parseInt(params.roleId as string, 10);
  if (Number.isNaN(roleId)) {
    throw new Response("Invalid role ID", { status: 400 });
  }

  const [role, permissions] = await Promise.all([
    getRoleById(roleId),
    getAllPermissions(),
  ]);

  if (!role) {
    throw new Response("Role not found", { status: 404 });
  }

  return { role, permissions } as { role: Role; permissions: Permission[] };
}

export default function EditRole() {
  const { role, permissions } = useLoaderData() as {
    role: Role;
    permissions: Permission[];
  };
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();
  const fetcher = useFetcher();

  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Set<number>>(
    new Set((role.permissions || []).map((p) => p.id)),
  );

  const grouped = useMemo(() => {
    const byCategory: Record<string, Permission[]> = {};
    for (const p of permissions) {
      const key = p.category || "General";
      if (!byCategory[key]) byCategory[key] = [];
      byCategory[key].push(p);
    }
    return Object.entries(byCategory).map(([category, list]) => ({
      category,
      nodes: list,
    }));
  }, [permissions]);

  const onToggle = (id: number, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };
  const onToggleAll = (ids: number[], checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) {
        for (const id of ids) next.add(id);
      } else {
        for (const id of ids) next.delete(id);
      }
      return next;
    });
  };
  const filterMatch = (name: string) =>
    name.toLowerCase().includes(query.toLowerCase());

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
        <h1 className="text-3xl font-bold text-balance">Edit Role</h1>
        <p className="text-muted-foreground mt-2">
          Update role: <span className="font-semibold">{role.name}</span>
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
          <fetcher.Form
            method="post"
            action={`/admin/roles/update/${role.id}`}
            className="space-y-6"
          >
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
                defaultValue={role.name}
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

            {[...selected].map((id) => (
              <input
                key={id}
                type="hidden"
                name="permissionIds"
                value={String(id)}
              />
            ))}
            <div className="flex gap-4 pt-4">
              <Button type="submit" className="flex items-center gap-2">
                <Edit className="h-4 w-4" />
                Update Role
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => navigate("/admin/roles")}
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
