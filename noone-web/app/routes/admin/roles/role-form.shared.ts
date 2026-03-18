import type { Permission, Role } from "@/types/admin";

export type RoleActionData = {
  errors?: Record<string, string>;
  success?: boolean;
  values?: RoleFormSeed;
};

export type RoleFormSeed = {
  name: string;
  permissionIds: number[];
};

export type PermissionCategoryGroup = {
  category: string;
  nodes: Permission[];
};

export function getRoleFormSeed(role?: Pick<Role, "name" | "permissions">) {
  return {
    name: role?.name ?? "",
    permissionIds: role?.permissions?.map((permission) => permission.id) ?? [],
  } satisfies RoleFormSeed;
}

export function parseRoleFormData(formData: FormData) {
  const values: RoleFormSeed = {
    name: String(formData.get("name") ?? "").trim(),
    permissionIds: formData
      .getAll("permissionIds")
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value)),
  };

  const errors: Record<string, string> = {};
  if (!values.name) {
    errors.name = "Role name is required";
  }
  if (values.permissionIds.length === 0) {
    errors.permissionIds = "Select at least one permission";
  }

  return {
    permissionIds: values.permissionIds,
    errors: Object.keys(errors).length > 0 ? errors : undefined,
    payload: { name: values.name },
    values,
  };
}

export function buildPermissionCategoryGroups(
  permissions: Permission[],
): PermissionCategoryGroup[] {
  const byCategory: Record<string, Permission[]> = {};

  for (const permission of permissions) {
    const category = permission.category || "General";
    byCategory[category] ??= [];
    byCategory[category].push(permission);
  }

  return Object.entries(byCategory).map(([category, nodes]) => ({
    category,
    nodes,
  }));
}
