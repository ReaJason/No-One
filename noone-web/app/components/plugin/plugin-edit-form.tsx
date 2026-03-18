import type { Plugin } from "@/types/plugin";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader, Save } from "lucide-react";
import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { useFetcher } from "react-router";

import { Badge } from "@/components/ui/badge";
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
import { Textarea } from "@/components/ui/textarea";
import {
  type PluginEditActionData,
  type PluginEditFormValues,
  pluginEditSchema,
} from "@/routes/plugins/plugin-edit-form.shared";

type PluginEditFormProps = {
  plugin: Plugin;
  onCancel: () => void;
};

export function PluginEditForm({ plugin, onCancel }: PluginEditFormProps) {
  const {
    control,
    formState: { errors: formErrors },
    handleSubmit,
    reset,
  } = useForm<PluginEditFormValues>({
    resolver: zodResolver(pluginEditSchema),
    defaultValues: {
      name: plugin.name ?? "",
      description: plugin.description ?? "",
      author: plugin.author ?? "",
      type: plugin.type ?? "",
      runMode: plugin.runMode ?? "",
    },
  });

  const fetcher = useFetcher<PluginEditActionData>();
  const isSubmitting = fetcher.state === "submitting";
  const serverErrors = fetcher.data?.errors;

  useEffect(() => {
    reset({
      name: plugin.name ?? "",
      description: plugin.description ?? "",
      author: plugin.author ?? "",
      type: plugin.type ?? "",
      runMode: plugin.runMode ?? "",
    });
  }, [plugin, reset]);

  const fieldError = (name: keyof PluginEditFormValues) =>
    formErrors[name]?.message ?? serverErrors?.[name];

  const onSubmit = async (data: PluginEditFormValues) => {
    const formData = new FormData();
    formData.set("name", data.name);
    formData.set("type", data.type);
    formData.set("description", data.description ?? "");
    formData.set("author", data.author ?? "");
    formData.set("runMode", data.runMode ?? "");

    await fetcher.submit(formData, { method: "post" });
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {serverErrors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {serverErrors.general}
        </div>
      ) : null}

      {/* Card 1: Plugin Identity (read-only) */}
      <Card>
        <CardHeader>
          <CardTitle>Plugin Identity</CardTitle>
          <CardDescription>
            Core identifiers assigned when the plugin was registered.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <dt className="text-sm font-medium text-muted-foreground">ID</dt>
              <dd className="mt-1 text-sm font-mono break-all">{plugin.id}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">Version</dt>
              <dd className="mt-1 text-sm">{plugin.version}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">Language</dt>
              <dd className="mt-1">
                <Badge variant="secondary">{plugin.language}</Badge>
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-muted-foreground">Source</dt>
              <dd className="mt-1">
                <Badge variant="outline">{plugin.source ?? "UNKNOWN"}</Badge>
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      {/* Card 2: Metadata (editable) */}
      <Card>
        <CardHeader>
          <CardTitle>Metadata</CardTitle>
          <CardDescription>
            Update the plugin display name, classification, and descriptive fields.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Metadata</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <Field data-invalid={Boolean(fieldError("name"))}>
                  <FieldLabel htmlFor="name">Name *</FieldLabel>
                  <Controller
                    name="name"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="name"
                        type="text"
                        placeholder="my-plugin"
                        aria-invalid={Boolean(fieldError("name")) || undefined}
                        required
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldError>{fieldError("name")}</FieldError>
                </Field>

                <Field data-invalid={Boolean(fieldError("type"))}>
                  <FieldLabel htmlFor="type">Type *</FieldLabel>
                  <Controller
                    name="type"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="type"
                        type="text"
                        placeholder="http"
                        aria-invalid={Boolean(fieldError("type")) || undefined}
                        required
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldError>{fieldError("type")}</FieldError>
                </Field>

                <Field>
                  <FieldLabel htmlFor="author">Author</FieldLabel>
                  <Controller
                    name="author"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="author"
                        type="text"
                        placeholder="Author name"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                </Field>

                <Field>
                  <FieldLabel htmlFor="runMode">Run Mode</FieldLabel>
                  <Controller
                    name="runMode"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="runMode"
                        type="text"
                        placeholder="sync / async / scheduled"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                </Field>
              </div>

              <Field>
                <FieldLabel htmlFor="description">Description</FieldLabel>
                <Controller
                  name="description"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      {...field}
                      id="description"
                      placeholder="Describe what this plugin does."
                      rows={3}
                      value={field.value ?? ""}
                    />
                  )}
                />
                <FieldDescription>Brief summary shown in plugin listings.</FieldDescription>
              </Field>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      {/* Card 3: Actions (read-only, conditional) */}
      {plugin.actions ? (
        <Card>
          <CardHeader>
            <CardTitle>Actions</CardTitle>
            <CardDescription>
              Declared actions exposed by this plugin (read-only).
            </CardDescription>
          </CardHeader>
          <CardContent>
            <pre className="overflow-auto rounded-md bg-muted p-4 text-sm">
              <code>{JSON.stringify(plugin.actions, null, 2)}</code>
            </pre>
          </CardContent>
        </Card>
      ) : null}

      <div className="flex gap-4 pt-2">
        <Button type="submit" className="flex items-center gap-2" disabled={isSubmitting}>
          {isSubmitting ? (
            <Loader className="h-4 w-4 animate-spin" />
          ) : (
            <Save className="h-4 w-4" />
          )}
          Save
        </Button>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
