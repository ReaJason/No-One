import type { PermissionFormSeed } from "@/routes/admin/permissions/permission-form.shared";
import type { LucideIcon } from "lucide-react";

import { Form } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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

type PermissionFormProps = {
  action?: string;
  errors?: Record<string, string>;
  icon: LucideIcon;
  initialValues: PermissionFormSeed;
  mode: "create" | "edit";
  onCancel: () => void;
  submitLabel: string;
};

export function PermissionForm({
  action,
  errors,
  icon: Icon,
  initialValues,
  mode,
  onCancel,
  submitLabel,
}: PermissionFormProps) {
  return (
    <Form method="post" action={action} className="space-y-6">
      {errors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {errors.general}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Icon className="h-5 w-5" />
            Permission Details
          </CardTitle>
          <CardDescription>
            {mode === "create"
              ? "Register a new permission code for access control and policy checks."
              : "Update the permission label and machine-readable code used by the system."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Permission Details</FieldLegend>
            <FieldGroup className="gap-6">
              <Field data-invalid={Boolean(errors?.name)}>
                <FieldLabel htmlFor="name">Permission Name *</FieldLabel>
                <Input
                  id="name"
                  name="name"
                  type="text"
                  defaultValue={initialValues.name}
                  placeholder="View Users"
                  aria-invalid={Boolean(errors?.name) || undefined}
                  required
                />
                <FieldDescription>Readable name shown in admin interfaces.</FieldDescription>
                <FieldError>{errors?.name}</FieldError>
              </Field>

              <Field data-invalid={Boolean(errors?.code)}>
                <FieldLabel htmlFor="code">Permission Code *</FieldLabel>
                <Input
                  id="code"
                  name="code"
                  type="text"
                  defaultValue={initialValues.code}
                  placeholder="user:view"
                  aria-invalid={Boolean(errors?.code) || undefined}
                  required
                />
                <FieldDescription>Use a stable format like `module:action`.</FieldDescription>
                <FieldError>{errors?.code}</FieldError>
              </Field>
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
