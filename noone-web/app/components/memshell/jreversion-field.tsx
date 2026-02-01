import { memo, useEffect, useState } from "react";
import {
  Field,
  FieldContent,
  FieldError,
  FieldLabel,
} from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const JDKVersion = [
  { label: "Java6", value: "50" },
  { label: "Java8", value: "52" },
  { label: "Java9", value: "53" },
  { label: "Java11", value: "55" },
  { label: "Java17", value: "61" },
  { label: "Java21", value: "65" },
];

interface JREVersionFormFieldProps {
  error?: string;
  onByPassJavaModuleChange?: (value: boolean) => void;
  server?: string;
}

// Optimize: Memoize component (rerender-memo)
export const JREVersionFormField = memo(function JREVersionFormField({
  error,
  onByPassJavaModuleChange,
  server,
}: Readonly<JREVersionFormFieldProps>) {
  const [value, setValue] = useState("50");

  // Optimize: Use effect with primitive dependencies (rerender-dependencies)
  // biome-ignore lint/correctness/useExhaustiveDependencies: value change by itself
  useEffect(() => {
    if (server === "SpringWebFlux" || server?.startsWith("XXL")) {
      const currentVersion = parseInt(value, 10);
      if (currentVersion < 52) {
        setValue("52");
        onByPassJavaModuleChange?.(false);
      }
    } else {
      const currentVersion = parseInt(value, 10);
      if (currentVersion >= 52 && value !== "50") {
        setValue("50");
        onByPassJavaModuleChange?.(false);
      }
    }
  }, [server, onByPassJavaModuleChange]);

  const handleChange = (v: string | null) => {
    setValue(v as string);
    if (onByPassJavaModuleChange) {
      const shouldBypass = parseInt(v ?? "0", 10) >= 53;
      onByPassJavaModuleChange(shouldBypass);
    }
  };

  return (
    <Field orientation="vertical" data-invalid={!!error}>
      <FieldContent>
        <FieldLabel htmlFor="targetJdkVersion">JRE Version</FieldLabel>
        <Select
          items={JDKVersion}
          name="targetJdkVersion"
          onValueChange={handleChange}
          value={value}
        >
          <SelectTrigger
            className="w-full"
            id="targetJdkVersion"
            aria-invalid={!!error}
          >
            <SelectValue placeholder="Select JDK version" />
          </SelectTrigger>
          <SelectContent>
            {JDKVersion.map((v) => (
              <SelectItem key={v.value} value={v.value}>
                {v.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {error && <FieldError errors={[{ message: error }]} />}
      </FieldContent>
    </Field>
  );
});
