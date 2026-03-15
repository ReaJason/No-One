import type { Profile } from "@/types/profile";

import { useEffect, useMemo, useState } from "react";

import { Card, CardContent } from "@/components/ui/card";
import { Field, FieldContent, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";

import { OptionalClassFormField } from "./classname-field";

const DELIVERY_MODE_OPTIONS = [
  {
    value: "false",
    title: "Stageless",
    description: "Loads the core directly with a single runtime profile.",
    profileLabel: "Core Profile",
    profileHint: "Use the profile that should execute the final payload.",
  },
  {
    value: "true",
    title: "Staging",
    description: "Drops a lightweight loader first, then brings in the staged core.",
    profileLabel: "Loader Profile",
    profileHint: "Use the profile responsible for the initial loader handoff.",
  },
] as const;

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
  const profileOptions = useMemo(
    () =>
      profiles.map((p) => ({
        label: p.name,
        value: p.id,
      })),
    [profiles],
  );
  const defaultProfileId = profileOptions[0]?.value ?? "";
  const [shellType, setShellType] = useState(shellTypes[0] || " ");
  const [urlPattern, setUrlPattern] = useState("/*");
  const [staging, setStaging] = useState(false);
  const [coreProfileId, setCoreProfileId] = useState(defaultProfileId);
  const [loaderProfileId, setLoaderProfileId] = useState(defaultProfileId);

  useEffect(() => {
    if (shellTypes.length > 0 && !shellTypes.includes(shellType)) {
      setShellType(shellTypes[0]);
    }
  }, [shellTypes, shellType]);

  useEffect(() => {
    onShellTypeChange?.(shellType);
  }, [onShellTypeChange, shellType]);

  useEffect(() => {
    if (profileOptions.length === 0) {
      if (coreProfileId !== "") {
        setCoreProfileId("");
      }
      if (loaderProfileId !== "") {
        setLoaderProfileId("");
      }
      return;
    }

    if (!profileOptions.some((option) => option.value === coreProfileId)) {
      setCoreProfileId(defaultProfileId);
    }

    if (!profileOptions.some((option) => option.value === loaderProfileId)) {
      setLoaderProfileId(defaultProfileId);
    }
  }, [coreProfileId, defaultProfileId, loaderProfileId, profileOptions]);

  const handleShellTypeChange = (value: string | null) => {
    setShellType(value as string);
    setUrlPattern("/*");
  };

  const error = errors?.shellType;
  const urlPatternError = errors?.urlPattern;
  const selectedMode = staging ? DELIVERY_MODE_OPTIONS[1] : DELIVERY_MODE_OPTIONS[0];

  return (
    <TabsContent value="NoOne">
      <Card>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2 md:grid md:grid-cols-2">
            <Field data-invalid={!!error}>
              <FieldContent>
                <FieldLabel>Shell MountType</FieldLabel>
                <input type="hidden" name="shellType" value={shellType} />
                <Select onValueChange={handleShellTypeChange} value={shellType}>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select shell type" />
                  </SelectTrigger>
                  <SelectContent key={shellTypes.join(",")}>
                    {shellTypes.length ? (
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
                {urlPatternError && <FieldError errors={[{ message: urlPatternError }]} />}
              </FieldContent>
            </Field>
          </div>

          <input type="hidden" name="staging" value={staging.toString()} />
          <div className="space-y-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium">Delivery Mode</Label>
              <p className="text-xs text-muted-foreground">
                Choose how NoOne reaches the target runtime before the payload becomes active.
              </p>
            </div>
            <RadioGroup
              value={staging.toString()}
              onValueChange={(value) => setStaging(value === "true")}
              className="grid gap-3 md:grid-cols-2"
            >
              {DELIVERY_MODE_OPTIONS.map((option) => (
                <Label
                  key={option.value}
                  htmlFor={`delivery-mode-${option.value}`}
                  className="flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition-colors hover:bg-muted/40 has-[[data-state=checked]]:border-primary has-[[data-state=checked]]:bg-primary/5"
                >
                  <RadioGroupItem
                    value={option.value}
                    id={`delivery-mode-${option.value}`}
                    className="mt-0.5"
                  />
                  <div className="flex flex-col gap-1">
                    <span className="font-medium">{option.title}</span>
                    <span className="text-xs leading-5 text-muted-foreground">
                      {option.description}
                    </span>
                  </div>
                </Label>
              ))}
            </RadioGroup>
          </div>

          <div className="rounded-xl border bg-muted/20 p-4">
            <div className="mb-3 space-y-1">
              <Label
                htmlFor={staging ? "loaderProfileId" : "coreProfileId"}
                className="text-sm font-medium"
              >
                {selectedMode.profileLabel}
              </Label>
              <p className="text-xs text-muted-foreground">{selectedMode.profileHint}</p>
            </div>
            {staging ? (
              <Field>
                <FieldContent>
                  <input type="hidden" name="loaderProfileId" value={loaderProfileId} />
                  <Select
                    value={loaderProfileId}
                    onValueChange={(value) => setLoaderProfileId(value ?? "")}
                    items={profileOptions}
                  >
                    <SelectTrigger className="w-full" id="loaderProfileId">
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
            ) : (
              <Field>
                <FieldContent>
                  <input type="hidden" name="coreProfileId" value={coreProfileId} />
                  <Select
                    value={coreProfileId}
                    items={profileOptions}
                    onValueChange={(value) => setCoreProfileId(value ?? "")}
                  >
                    <SelectTrigger className="w-full" id="coreProfileId">
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
            )}
          </div>
          <OptionalClassFormField />
        </CardContent>
      </Card>
    </TabsContent>
  );
}
