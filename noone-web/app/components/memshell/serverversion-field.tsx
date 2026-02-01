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

export function ServerVersionFormField({
  server,
  error,
}: Readonly<{
  server: string;
  error?: string;
}>) {
  const serverVersionOptions = getServerVersionOptions(server);
  return (
    <Field orientation="vertical" data-invalid={!!error}>
      <FieldContent>
        <FieldLabel htmlFor="serverVersion">Server Version</FieldLabel>
        <Select
          items={serverVersionOptions}
          name="serverVersion"
          defaultValue="Unknown"
        >
          <SelectTrigger
            className="w-full"
            id="serverVersion"
            aria-invalid={!!error}
          >
            <SelectValue placeholder="Select version" />
          </SelectTrigger>
          <SelectContent>
            {serverVersionOptions.map((v) => (
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
}

function getServerVersionOptions(server: string) {
  if (server === "TongWeb") {
    return [
      { label: "6", value: "6" },
      { label: "7", value: "7" },
      { label: "8", value: "8" },
    ];
  } else if (server === "Jetty") {
    return [
      { label: "6", value: "6" },
      { label: "7+", value: "7+" },
      { label: "12", value: "12" },
    ];
  }
  return [{ label: "Unknown", value: "Unknown" }];
}
