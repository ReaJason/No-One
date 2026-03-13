import type { CreateUserRequest, UpdateUserRequest } from "@/api/user-api";
import type { Role, User, UserStatus } from "@/types/admin";

export type UserActionData = {
  errors?: Record<string, string>;
  success?: boolean;
  values?: UserFormSeed;
};

export type UserFormSeed = {
  username: string;
  password: string;
  email: string;
  status: UserStatus;
  roleIds: number[];
};

export const USER_STATUS_ITEMS: Array<{ label: string; value: UserStatus }> = [
  { label: "Unactivated", value: "UNACTIVATED" },
  { label: "Enabled", value: "ENABLED" },
  { label: "Disabled", value: "DISABLED" },
  { label: "Locked", value: "LOCKED" },
];

export function getUserFormSeed(user?: Pick<User, "username" | "email" | "status" | "roles">) {
  return {
    username: user?.username ?? "",
    password: "",
    email: user?.email ?? "",
    status: user?.status ?? "UNACTIVATED",
    roleIds: user?.roles?.map((role) => role.id) ?? [],
  } satisfies UserFormSeed;
}

export function parseUserFormData(
  formData: FormData,
  options: { mode: "create" | "edit" },
): {
  createPayload?: CreateUserRequest;
  updatePayload?: UpdateUserRequest;
  errors?: Record<string, string>;
  values: UserFormSeed;
} {
  const values: UserFormSeed = {
    username: String(formData.get("username") ?? "").trim(),
    password: String(formData.get("password") ?? ""),
    email: String(formData.get("email") ?? "").trim(),
    status: readUserStatus(formData),
    roleIds: formData
      .getAll("roleIds")
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value)),
  };

  const errors: Record<string, string> = {};

  if (options.mode === "create" && !values.username) {
    errors.username = "Username is required";
  }

  if (options.mode === "create") {
    if (!values.password.trim()) {
      errors.password = "Password is required";
    } else if (values.password.length < 6) {
      errors.password = "Password must be at least 6 characters";
    }
  }

  if (!values.email) {
    errors.email = "Email is required";
  } else if (!values.email.includes("@")) {
    errors.email = "Email format is invalid";
  }

  if (values.roleIds.length === 0) {
    errors.roleIds = "At least one role must be selected";
  }

  if (Object.keys(errors).length > 0) {
    return {
      errors,
      values,
    };
  }

  if (options.mode === "create") {
    return {
      createPayload: {
        username: values.username,
        password: values.password,
        email: values.email,
        status: values.status,
        roleIds: values.roleIds,
      },
      values,
    };
  }

  return {
    updatePayload: {
      email: values.email,
      status: values.status,
      roleIds: values.roleIds,
    },
    values,
  };
}

export function getRoleSummary(roles: Pick<Role, "id" | "name">[]) {
  return roles.map(({ id, name }) => ({ id, name }));
}

function readUserStatus(formData: FormData): UserStatus {
  const value = String(formData.get("status") ?? "UNACTIVATED").toUpperCase();
  if (value === "ENABLED" || value === "DISABLED" || value === "LOCKED") {
    return value;
  }
  return "UNACTIVATED";
}
