import { ArrowLeft, Plus } from "lucide-react";
import { useCallback } from "react";
import type { ActionFunctionArgs } from "react-router";
import { Form, redirect, useActionData, useNavigate } from "react-router";
import { createPlugin } from "@/api/plugin-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const jsonText = formData.get("pluginJson") as string;

  const errors: Record<string, string> = {};

  if (!jsonText?.trim()) {
    errors.pluginJson = "Plugin JSON is required";
    return { errors, success: false };
  }

  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(jsonText);
  } catch {
    errors.pluginJson = "Invalid JSON format";
    return { errors, success: false };
  }

  const requiredFields = ["id", "name", "version", "language", "type"];
  for (const field of requiredFields) {
    if (!parsed[field] || String(parsed[field]).trim() === "") {
      errors.pluginJson = `Missing required field: "${field}"`;
      return { errors, success: false };
    }
  }

  try {
    await createPlugin(parsed);
    return redirect("/plugins");
  } catch (error: any) {
    return {
      errors: { general: error.message || "Failed to create plugin" },
      success: false,
    };
  }
}

export default function CreatePlugin() {
  const actionData = useActionData() as
    | { errors?: Record<string, string>; success?: boolean }
    | undefined;
  const navigate = useNavigate();

  const handleNavigateToPlugins = useCallback(() => {
    navigate("/plugins");
  }, [navigate]);

  return (
    <div className="container mx-auto max-w-6xl p-6">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={handleNavigateToPlugins}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to plugin list
        </Button>

        <h1 className="text-3xl font-bold text-balance">Add Plugin</h1>
        <p className="mt-2 text-muted-foreground">
          Paste the plugin JSON definition to add or update a plugin
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Plugin JSON</CardTitle>
        </CardHeader>
        <CardContent>
          <Form method="post" className="space-y-6">
            {actionData?.errors?.general ? (
              <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
                {actionData.errors.general}
              </div>
            ) : null}

            <div className="space-y-2">
              <Label htmlFor="pluginJson">Plugin Definition *</Label>
              <Textarea
                id="pluginJson"
                name="pluginJson"
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
                  actionData?.errors?.pluginJson
                    ? "border-destructive font-mono text-sm"
                    : "font-mono text-sm"
                }
              />
              {actionData?.errors?.pluginJson ? (
                <p className="text-sm text-destructive">{actionData.errors.pluginJson}</p>
              ) : null}
              <p className="text-sm text-muted-foreground">
                Required fields: id, name, version, language, type. If a plugin with the same name
                and version already exists, it will be overwritten.
              </p>
            </div>

            <div className="flex gap-4 pt-4">
              <Button type="submit">
                <Plus className="h-4 w-4" />
                Add Plugin
              </Button>

              <Button type="button" variant="outline" onClick={handleNavigateToPlugins}>
                Cancel
              </Button>
            </div>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}
