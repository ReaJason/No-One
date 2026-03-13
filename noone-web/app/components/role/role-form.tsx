import type { Permission } from "@/types/admin";
import type { LucideIcon } from "lucide-react";

import { Search } from "lucide-react";
import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { Form } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  buildPermissionCategoryGroups,
  type RoleFormSeed,
} from "@/routes/admin/roles/role-form.shared";

type RoleFormProps = {
  action?: string;
  errors?: Record<string, string>;
  icon: LucideIcon;
  initialValues: RoleFormSeed;
  mode: "create" | "edit";
  onCancel: () => void;
  permissions: Permission[];
  submitLabel: string;
};

export function RoleForm({
  action,
  errors,
  icon: Icon,
  initialValues,
  mode,
  onCancel,
  permissions,
  submitLabel,
}: RoleFormProps) {
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<Set<number>>(
    () => new Set(initialValues.permissionIds),
  );

  useEffect(() => {
    setQuery("");
    setSelectedPermissionIds(new Set(initialValues.permissionIds));
  }, [initialValues]);

  const groups = useMemo(() => buildPermissionCategoryGroups(permissions), [permissions]);

  const normalizedQuery = deferredQuery.trim().toLowerCase();

  const filteredGroups = useMemo(() => {
    return groups
      .map(({ category, nodes }) => ({
        category,
        nodes: normalizedQuery
          ? nodes.filter((node) => {
              const haystack = `${node.name} ${node.code}`.toLowerCase();
              return haystack.includes(normalizedQuery);
            })
          : nodes,
      }))
      .filter((group) => group.nodes.length > 0);
  }, [groups, normalizedQuery]);

  const onToggle = (permissionId: number, checked: boolean) => {
    setSelectedPermissionIds((previous) => {
      const next = new Set(previous);
      if (checked) {
        next.add(permissionId);
      } else {
        next.delete(permissionId);
      }
      return next;
    });
  };

  const onToggleAll = (permissionIds: number[], checked: boolean) => {
    setSelectedPermissionIds((previous) => {
      const next = new Set(previous);
      for (const permissionId of permissionIds) {
        if (checked) {
          next.add(permissionId);
        } else {
          next.delete(permissionId);
        }
      }
      return next;
    });
  };

  return (
    <Form method="post" action={action} className="space-y-6">
      {[...selectedPermissionIds].map((permissionId) => (
        <input key={permissionId} type="hidden" name="permissionIds" value={String(permissionId)} />
      ))}

      {errors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {errors.general}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Icon className="h-5 w-5" />
            Role Details
          </CardTitle>
          <CardDescription>
            {mode === "create"
              ? "Name the role and choose the permissions it should grant."
              : "Refine the role name and access surface without leaving the editor."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Role Details</FieldLegend>
            <FieldGroup className="gap-6">
              <Field data-invalid={Boolean(errors?.name)}>
                <FieldLabel htmlFor="name">Role Name *</FieldLabel>
                <Input
                  id="name"
                  name="name"
                  type="text"
                  defaultValue={initialValues.name}
                  placeholder="Operations Lead"
                  aria-invalid={Boolean(errors?.name) || undefined}
                  required
                />
                <FieldDescription>Use a clear, operator-facing name.</FieldDescription>
                <FieldError>{errors?.name}</FieldError>
              </Field>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Permissions</CardTitle>
          <CardDescription>
            Search, review, and toggle permission categories before saving the role.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Permissions</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="flex items-center justify-between gap-3">
                <div className="space-y-1">
                  <FieldLabel>Assigned Permissions *</FieldLabel>
                  <FieldDescription>
                    Group selection is preserved when the form re-renders after validation.
                  </FieldDescription>
                  <FieldError>{errors?.permissionIds}</FieldError>
                </div>

                <div className="relative">
                  <Input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Search permissions..."
                    className="h-8 w-60 pl-8"
                  />
                  <Search className="absolute top-1/2 left-2 size-4 -translate-y-1/2 text-muted-foreground" />
                </div>
              </div>

              <div className="rounded-md border">
                <ScrollArea className="h-[360px] p-3">
                  <div className="space-y-4">
                    {filteredGroups.map(({ category, nodes }) => {
                      const ids = nodes.map((node) => node.id);
                      const selectedCount = ids.filter((id) =>
                        selectedPermissionIds.has(id),
                      ).length;
                      const allChecked = ids.length > 0 && selectedCount === ids.length;
                      const indeterminate = selectedCount > 0 && selectedCount < ids.length;

                      return (
                        <div key={category} className="space-y-2">
                          <div className="flex items-center gap-2">
                            <Checkbox
                              id={`cat-${category}`}
                              checked={indeterminate || allChecked}
                              onCheckedChange={(checked) => onToggleAll(ids, checked === true)}
                            />
                            <Label htmlFor={`cat-${category}`} className="font-medium">
                              {category}
                            </Label>
                            <span className="text-xs text-muted-foreground">
                              {selectedCount}/{ids.length}
                            </span>
                          </div>

                          <div className="grid grid-cols-1 gap-2 pl-6 sm:grid-cols-2">
                            {nodes.map((permission) => (
                              <div key={permission.id} className="flex items-center gap-2">
                                <Checkbox
                                  id={`perm-${permission.id}`}
                                  checked={selectedPermissionIds.has(permission.id)}
                                  onCheckedChange={(checked) =>
                                    onToggle(permission.id, checked === true)
                                  }
                                />
                                <Label
                                  htmlFor={`perm-${permission.id}`}
                                  className="text-sm font-normal"
                                >
                                  {permission.name}
                                  <span className="text-muted-foreground">
                                    {" "}
                                    ・ {permission.code}
                                  </span>
                                </Label>
                              </div>
                            ))}
                          </div>
                        </div>
                      );
                    })}

                    {filteredGroups.length === 0 ? (
                      <div className="text-sm text-muted-foreground">
                        No permissions match the current query.
                      </div>
                    ) : null}
                  </div>
                </ScrollArea>
              </div>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <div className="flex gap-4 pt-2">
        <Button type="submit" className="flex items-center gap-2">
          <Icon className="h-4 w-4" />
          {submitLabel}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </Form>
  );
}
