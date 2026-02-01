import { Card, CardContent } from "@/components/ui/card";
import { Field, FieldContent, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";
import type { Profile } from "@/types/profile";
import { OptionalClassFormField } from "./classname-field";
import { ShellTypeFormField } from "./shelltype-field";

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
  return (
    <TabsContent value="NoOne">
      <Card>
        <CardContent className="flex flex-col gap-2">
          <ShellTypeFormField
            shellTypes={shellTypes}
            error={errors?.shellType}
            urlPatternError={errors?.urlPattern}
            onShellTypeChange={onShellTypeChange}
          />
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
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
          </div>
          <OptionalClassFormField />
        </CardContent>
      </Card>
    </TabsContent>
  );
}
