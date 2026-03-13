import type { Role, UserStatus } from "@/types/admin";
import type { LucideIcon } from "lucide-react";

import { useEffect, useState } from "react";
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
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { USER_STATUS_ITEMS, type UserFormSeed } from "@/routes/admin/users/user-form.shared";

type UserFormProps = {
  action?: string;
  errors?: Record<string, string>;
  icon: LucideIcon;
  initialValues: UserFormSeed;
  mode: "create" | "edit";
  onCancel: () => void;
  roles: Pick<Role, "id" | "name">[];
  submitLabel: string;
};

export function UserForm({
  action,
  errors,
  icon: Icon,
  initialValues,
  mode,
  onCancel,
  roles,
  submitLabel,
}: UserFormProps) {
  const [status, setStatus] = useState<UserStatus>(initialValues.status);
  const [selectedRoleIds, setSelectedRoleIds] = useState<Set<number>>(
    () => new Set(initialValues.roleIds),
  );

  useEffect(() => {
    setStatus(initialValues.status);
    setSelectedRoleIds(new Set(initialValues.roleIds));
  }, [initialValues]);

  const toggleRole = (roleId: number, checked: boolean) => {
    setSelectedRoleIds((previous) => {
      const next = new Set(previous);
      if (checked) {
        next.add(roleId);
      } else {
        next.delete(roleId);
      }
      return next;
    });
  };

  return (
    <Form method="post" action={action} className="space-y-6">
      {[...selectedRoleIds].map((roleId) => (
        <input key={roleId} type="hidden" name="roleIds" value={String(roleId)} />
      ))}
      <input type="hidden" name="status" value={status} />

      {errors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {errors.general}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Icon className="h-5 w-5" />
            Account Details
          </CardTitle>
          <CardDescription>
            {mode === "create"
              ? "Create an administrator-managed account with a temporary password and default onboarding constraints."
              : "Update the user email, account status, and assigned roles from a single editor."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Account Details</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <Field data-invalid={Boolean(errors?.username)}>
                  <FieldLabel htmlFor="username">Username *</FieldLabel>
                  <Input
                    id="username"
                    name="username"
                    type="text"
                    defaultValue={initialValues.username}
                    placeholder="operator"
                    aria-invalid={Boolean(errors?.username) || undefined}
                    required={mode === "create"}
                    disabled={mode === "edit"}
                  />
                  <FieldDescription>
                    {mode === "create"
                      ? "Username is fixed after account creation."
                      : "Username is immutable after the account is created."}
                  </FieldDescription>
                  <FieldError>{errors?.username}</FieldError>
                </Field>

                <Field data-invalid={Boolean(errors?.email)}>
                  <FieldLabel htmlFor="email">Email *</FieldLabel>
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    defaultValue={initialValues.email}
                    placeholder="operator@example.com"
                    aria-invalid={Boolean(errors?.email) || undefined}
                    required
                  />
                  <FieldDescription>Used for notifications and account recovery.</FieldDescription>
                  <FieldError>{errors?.email}</FieldError>
                </Field>

                {mode === "create" ? (
                  <Field data-invalid={Boolean(errors?.password)}>
                    <FieldLabel htmlFor="password">Temporary Password *</FieldLabel>
                    <Input
                      id="password"
                      name="password"
                      type="password"
                      defaultValue={initialValues.password}
                      placeholder="At least 6 characters"
                      aria-invalid={Boolean(errors?.password) || undefined}
                      required
                    />
                    <FieldDescription>
                      The user will replace this password on first sign-in.
                    </FieldDescription>
                    <FieldError>{errors?.password}</FieldError>
                  </Field>
                ) : null}

                <Field data-invalid={Boolean(errors?.status)}>
                  <FieldLabel htmlFor="status">Status *</FieldLabel>
                  <Select
                    value={status}
                    onValueChange={(value) => setStatus((value ?? "UNACTIVATED") as UserStatus)}
                    disabled={mode === "create"}
                  >
                    <SelectTrigger id="status" className="w-full">
                      <SelectValue placeholder="Select status" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {(mode === "create"
                          ? USER_STATUS_ITEMS.filter((item) => item.value === "UNACTIVATED")
                          : USER_STATUS_ITEMS
                        ).map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                  <FieldDescription>
                    {mode === "create"
                      ? "New accounts begin in the unactivated state."
                      : "Switch between enabled, disabled, locked, and unactivated states directly here."}
                  </FieldDescription>
                  <FieldError>{errors?.status}</FieldError>
                </Field>
              </div>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Role Assignment</CardTitle>
          <CardDescription>Choose one or more roles to define this user's access.</CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Role Assignment</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="space-y-3">
                <FieldLabel>Roles *</FieldLabel>
                <FieldError>{errors?.roleIds}</FieldError>

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  {roles.map((role) => {
                    const checked = selectedRoleIds.has(role.id);
                    return (
                      <label
                        key={role.id}
                        className="flex items-center gap-3 rounded-lg border p-3"
                      >
                        <Checkbox
                          checked={checked}
                          onCheckedChange={(next) => toggleRole(role.id, next === true)}
                        />
                        <span className="text-sm font-medium">{role.name}</span>
                      </label>
                    );
                  })}
                </div>
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
