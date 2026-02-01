import { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import {
  Field,
  FieldContent,
  FieldError,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";
import { notNeedUrlPattern } from "@/lib/utils";
import type { Profile } from "@/types/profile";
import { OptionalClassFormField } from "./classname-field";

export function NoOneTabContent({
  shellTypes,
  errors,
  profiles,
  onShellTypeChange,
}: Readonly<{
  shellTypes: Array<string>;
  errors?: Record<string, string>;
  profiles: Profile[];
  onShellTypeChange?: (shellType: string) => void;
}>) {
  const profileOptions = profiles.map((p) => ({
    label: p.name,
    value: p.id,
  }));
  const [shellType, setShellType] = useState(shellTypes[0] || " ");
  const [urlPattern, setUrlPattern] = useState("/*");
  const _needUrlPattern = !notNeedUrlPattern(shellType);

  // Reset shellType to first option when shellTypes changes (e.g., server change)
  useEffect(() => {
    if (shellTypes.length > 0 && !shellTypes.includes(shellType)) {
      setShellType(shellTypes[0]);
    }
  }, [shellTypes, shellType]);

  useEffect(() => {
    onShellTypeChange?.(shellType);
  }, [onShellTypeChange, shellType]);

  const handleShellTypeChange = (value: string | null) => {
    setShellType(value as string);
    setUrlPattern("/*");
  };
  const error = errors?.shellType;
  const urlPatternError = errors?.urlPattern;
  return (
    <TabsContent value="NoOne">
      <Card>
        <CardContent className="flex flex-col gap-2">
          <div className={"flex flex-col gap-2 md:grid md:grid-cols-3"}>
            <Field>
              <FieldContent>
                <FieldLabel htmlFor="server">Profile</FieldLabel>
                <Select
                  name="profileId"
                  items={profileOptions}
                  defaultValue={profiles[0].id}
                >
                  <SelectTrigger className="w-full" id="profile">
                    <SelectValue placeholder="Select profile" />
                  </SelectTrigger>
                  <SelectContent>
                    {profileOptions.map((p) => (
                      <SelectItem key={p.value} value={p.value}>
                        {p.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FieldContent>
            </Field>
            <Field data-invalid={!!error}>
              <FieldContent>
                <FieldLabel>Shell MountType</FieldLabel>
                <input type="hidden" name="shellType" value={shellType} />
                <Select onValueChange={handleShellTypeChange} value={shellType}>
                  <SelectTrigger className="w-full">
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
              </FieldContent>
            </Field>
            <Field data-invalid={!!urlPatternError}>
              <FieldContent>
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
              </FieldContent>
            </Field>
          </div>
          <OptionalClassFormField />
        </CardContent>
      </Card>
    </TabsContent>
  );
}
