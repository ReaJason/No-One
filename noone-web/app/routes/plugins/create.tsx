import type { ActionFunctionArgs } from "react-router";

import { redirect, useActionData, useNavigate } from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { createPlugin } from "@/api/plugin-api";
import { FormPageShell } from "@/components/form-page-shell";
import { PluginUploadForm } from "@/components/plugin/plugin-upload-form";
import {
  getPluginFormSeed,
  parsePluginFormData,
  type PluginActionData,
} from "@/routes/plugins/plugin-form.shared";

export async function action({ request, context }: ActionFunctionArgs) {
  const parsed = parsePluginFormData(await request.formData());
  if (parsed.errors) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    } satisfies PluginActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);
    await createPlugin(parsed.payload!, authFetch);
    return redirect("/plugins");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to create plugin" },
      success: false,
      values: parsed.values,
    } satisfies PluginActionData;
  }
}

export default function CreatePlugin() {
  const actionData = useActionData() as PluginActionData | undefined;
  const navigate = useNavigate();
  const initialValues = actionData?.values ?? getPluginFormSeed();

  return (
    <FormPageShell
      backHref="/plugins"
      backLabel="Return to plugin list"
      badges={[{ label: "New plugin" }]}
      title="Add Plugin"
      description="Paste the plugin JSON definition to register or replace a plugin version."
    >
      <PluginUploadForm
        key={`create:${JSON.stringify(initialValues)}`}
        initialValues={initialValues}
        errors={actionData?.errors}
        onCancel={() => navigate("/plugins")}
      />
    </FormPageShell>
  );
}
