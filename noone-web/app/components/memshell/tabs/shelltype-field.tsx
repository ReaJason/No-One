import { useEffect, useState } from "react";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn, notNeedUrlPattern } from "@/lib/utils";

export function ShellTypeFormField({
  shellTypes,
  defaultShellType,
  defaultUrlPattern = "/*",
  onShellTypeChange,
  error,
  urlPatternError,
}: Readonly<{
  shellTypes: Array<string>;
  defaultShellType?: string;
  defaultUrlPattern?: string;
  onShellTypeChange?: (shellType: string) => void;
  error?: string;
  urlPatternError?: string;
}>) {
  const [shellType, setShellType] = useState(
    defaultShellType || shellTypes[0] || " ",
  );
  const [urlPattern, setUrlPattern] = useState(defaultUrlPattern);
  const needUrlPattern = !notNeedUrlPattern(shellType);

  useEffect(() => {
    if (!shellTypes.length) {
      if (shellType !== " ") {
        setShellType(" ");
      }
      return;
    }

    if (shellTypes.includes(shellType)) {
      return;
    }

    const nextShellType =
      defaultShellType && shellTypes.includes(defaultShellType)
        ? defaultShellType
        : shellTypes[0];
    setShellType(nextShellType);
    setUrlPattern(defaultUrlPattern);
  }, [defaultShellType, defaultUrlPattern, shellType, shellTypes]);

  useEffect(() => {
    onShellTypeChange?.(shellType);
  }, [onShellTypeChange, shellType]);

  const handleShellTypeChange = (value: string | null) => {
    setShellType(value as string);
    setUrlPattern(defaultUrlPattern);
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
      <Field className="gap-1" data-invalid={!!error}>
        <FieldLabel>Shell Mount Type</FieldLabel>
        <input type="hidden" name="shellType" value={shellType} />
        <Select onValueChange={handleShellTypeChange} value={shellType}>
          <SelectTrigger>
            <SelectValue placeholder="Select shell type" />
          </SelectTrigger>
          <SelectContent key={shellTypes?.join(",")}>
            {shellTypes?.length ? (
              shellTypes.map((v) => (
                <SelectItem key={v} value={v}>
                  {v}
                </SelectItem>
              ))
            ) : (
              <SelectItem value=" ">Shell tool not selected</SelectItem>
            )}
          </SelectContent>
        </Select>
        {error && <FieldError errors={[{ message: error }]} />}
      </Field>
      <Field
        className={cn("gap-1", needUrlPattern ? "grid" : "hidden")}
        data-invalid={!!urlPatternError}
      >
        <FieldLabel>URL Pattern</FieldLabel>
        <Input
          name="urlPattern"
          value={urlPattern}
          onChange={(e) => setUrlPattern(e.target.value)}
          placeholder="Enter URL pattern"
        />
        {urlPatternError && (
          <FieldError errors={[{ message: urlPatternError }]} />
        )}
      </Field>
    </div>
  );
}
