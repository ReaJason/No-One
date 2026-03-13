export type PluginActionData = {
  errors?: Record<string, string>;
  success?: boolean;
  values?: PluginFormSeed;
};

export type PluginFormSeed = {
  pluginJson: string;
};

export function getPluginFormSeed(values?: Partial<PluginFormSeed>): PluginFormSeed {
  return {
    pluginJson: values?.pluginJson ?? "",
  };
}

export function parsePluginFormData(formData: FormData) {
  const values: PluginFormSeed = {
    pluginJson: String(formData.get("pluginJson") ?? ""),
  };

  const errors: Record<string, string> = {};
  const jsonText = values.pluginJson.trim();
  if (!jsonText) {
    errors.pluginJson = "Plugin JSON is required";
    return { errors, values };
  }

  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(jsonText);
  } catch {
    return {
      errors: { pluginJson: "Invalid JSON format" },
      values,
    };
  }

  const requiredFields = ["id", "name", "version", "language", "type"];
  for (const field of requiredFields) {
    if (!parsed[field] || String(parsed[field]).trim() === "") {
      return {
        errors: { pluginJson: `Missing required field: "${field}"` },
        values,
      };
    }
  }

  return {
    payload: parsed,
    values,
  };
}
