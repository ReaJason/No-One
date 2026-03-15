import type { PackerSchemaField } from "@/types/memshell";

import { memo } from "react";

import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";

type ConfigValue = string | number | boolean | undefined;

type Props = {
  fields: PackerSchemaField[];
  values: Record<string, ConfigValue>;
  onValueChange: (key: string, value: ConfigValue) => void;
  baseName?: string;
};

export const PackerCustomConfigFields = memo(function PackerCustomConfigFields({
  fields,
  values,
  onValueChange,
  baseName = "packerCustomConfig",
}: Readonly<Props>) {
  if (fields.length === 0) {
    return null;
  }

  const supportedFields = fields.filter((field) =>
    ["BOOLEAN", "STRING", "ENUM", "INTEGER"].includes(field.type),
  );

  if (supportedFields.length === 0) {
    return null;
  }

  return (
    <Field className="mt-2 gap-1">
      <FieldLabel>Packer Params</FieldLabel>
      {supportedFields.map((schemaField) => {
        const fieldName = `${baseName}.${schemaField.key}`;
        const fieldDescription = schemaField.description;
        const rawValue = values[schemaField.key] ?? schemaField.defaultValue;

        switch (schemaField.type) {
          case "BOOLEAN": {
            const isChecked = Boolean(rawValue);
            return (
              <Field key={schemaField.key} orientation="horizontal">
                <input type="hidden" name={fieldName} value={isChecked.toString()} />
                <Switch
                  id={fieldName}
                  checked={isChecked}
                  onCheckedChange={(v) => onValueChange(schemaField.key, v)}
                />
                <FieldContent>
                  <FieldLabel htmlFor={fieldName}>{schemaField.key}</FieldLabel>
                  {fieldDescription ? (
                    <FieldDescription>{fieldDescription}</FieldDescription>
                  ) : null}
                </FieldContent>
              </Field>
            );
          }
          case "ENUM": {
            const enumValue = typeof rawValue === "string" ? rawValue : undefined;
            return (
              <Field key={schemaField.key} orientation="vertical">
                <input type="hidden" name={fieldName} value={enumValue ?? ""} />
                <FieldContent>
                  <FieldLabel htmlFor={fieldName}>{schemaField.key}</FieldLabel>
                  <Select
                    value={enumValue}
                    onValueChange={(v) => onValueChange(schemaField.key, v as string)}
                  >
                    <SelectTrigger id={fieldName}>
                      <SelectValue placeholder="Select..." />
                    </SelectTrigger>
                    <SelectContent>
                      {(schemaField.options ?? []).map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label || option.value}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {fieldDescription ? (
                    <FieldDescription>{fieldDescription}</FieldDescription>
                  ) : null}
                </FieldContent>
              </Field>
            );
          }
          case "INTEGER": {
            const intValue = typeof rawValue === "number" ? rawValue : "";
            return (
              <Field key={schemaField.key} orientation="vertical">
                <FieldContent>
                  <FieldLabel htmlFor={fieldName}>{schemaField.key}</FieldLabel>
                  <Input
                    id={fieldName}
                    name={fieldName}
                    type="number"
                    step={1}
                    value={intValue}
                    onChange={(event) => {
                      const raw = event.target.value;
                      if (raw === "") {
                        onValueChange(schemaField.key, undefined);
                        return;
                      }
                      const parsed = Number.parseInt(raw, 10);
                      onValueChange(schemaField.key, Number.isFinite(parsed) ? parsed : undefined);
                    }}
                  />
                  {fieldDescription ? (
                    <FieldDescription>{fieldDescription}</FieldDescription>
                  ) : null}
                </FieldContent>
              </Field>
            );
          }
          case "STRING": {
            const strValue = typeof rawValue === "string" ? rawValue : "";
            return (
              <Field key={schemaField.key} orientation="vertical">
                <FieldContent>
                  <FieldLabel htmlFor={fieldName}>{schemaField.key}</FieldLabel>
                  <Input
                    id={fieldName}
                    name={fieldName}
                    type="text"
                    value={strValue}
                    onChange={(e) => onValueChange(schemaField.key, e.target.value)}
                  />
                  {fieldDescription ? (
                    <FieldDescription>{fieldDescription}</FieldDescription>
                  ) : null}
                </FieldContent>
              </Field>
            );
          }
          default:
            return null;
        }
      })}
    </Field>
  );
});
