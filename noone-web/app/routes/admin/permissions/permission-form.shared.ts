import type { Permission } from "@/types/admin";

export type PermissionActionData = {
  errors?: Record<string, string>;
  success?: boolean;
  values?: PermissionFormSeed;
};

export type PermissionFormSeed = {
  name: string;
  code: string;
};

export function getPermissionFormSeed(permission?: Pick<Permission, "name" | "code">) {
  return {
    name: permission?.name ?? "",
    code: permission?.code ?? "",
  } satisfies PermissionFormSeed;
}

export function parsePermissionFormData(formData: FormData) {
  const values: PermissionFormSeed = {
    name: String(formData.get("name") ?? "").trim(),
    code: String(formData.get("code") ?? "").trim(),
  };

  const errors: Record<string, string> = {};

  if (!values.name) {
    errors.name = "Permission name is required";
  }

  if (!values.code) {
    errors.code = "Permission code is required";
  } else if (!/^[a-zA-Z0-9:_-]+$/.test(values.code)) {
    errors.code =
      "Permission code can only contain letters, numbers, colons, underscores, and hyphens";
  }

  return {
    errors: Object.keys(errors).length > 0 ? errors : undefined,
    payload: values,
    values,
  };
}
