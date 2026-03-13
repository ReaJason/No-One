import type { PluginFormSeed } from "@/routes/plugins/plugin-form.shared";

import { Plus } from "lucide-react";
import { Form } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

type PluginFormProps = {
  action?: string;
  errors?: Record<string, string>;
  initialValues: PluginFormSeed;
  onCancel: () => void;
};

export function PluginForm({ action, errors, initialValues, onCancel }: PluginFormProps) {
  return (
    <Form method="post" action={action} className="space-y-6">
      {errors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {errors.general}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Plugin JSON</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            <Label htmlFor="pluginJson">Plugin Definition *</Label>
            <Textarea
              id="pluginJson"
              name="pluginJson"
              defaultValue={initialValues.pluginJson}
              placeholder={`{
  "id": "PortScan",
  "name": "PortScan",
  "version": "1.0.0",
  "language": "java",
  "type": "reconnaissance",
  "actions": { ... }
}`}
              rows={16}
              className={
                errors?.pluginJson ? "border-destructive font-mono text-sm" : "font-mono text-sm"
              }
            />
            {errors?.pluginJson ? (
              <p className="text-sm text-destructive">{errors.pluginJson}</p>
            ) : null}
            <p className="text-sm text-muted-foreground">
              Required fields: id, name, version, language, type. If a plugin with the same name and
              version already exists, it will be overwritten.
            </p>
          </div>
        </CardContent>
      </Card>

      <div className="flex gap-4 pt-2">
        <Button type="submit" className="flex items-center gap-2">
          <Plus className="h-4 w-4" />
          Add Plugin
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </Form>
  );
}
