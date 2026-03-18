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

export type PluginParsedResult =
  | { payload: Record<string, unknown>; values: PluginFormSeed }
  | { errors: Record<string, string>; values: PluginFormSeed };

export function parsePluginJson(jsonText: string): PluginParsedResult {
  const values: PluginFormSeed = { pluginJson: jsonText };
  const trimmed = jsonText.trim();
  if (!trimmed) {
    return { errors: { pluginJson: "Plugin JSON is required" }, values };
  }

  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    return { errors: { pluginJson: "Invalid JSON format" }, values };
  }

  const requiredFields = ["id", "name", "version", "language", "type"];
  for (const field of requiredFields) {
    if (!parsed[field] || String(parsed[field]).trim() === "") {
      return { errors: { pluginJson: `Missing required field: "${field}"` }, values };
    }
  }

  return { payload: parsed, values };
}

export function parsePluginFormData(formData: FormData): PluginParsedResult {
  const jsonText = String(formData.get("pluginJson") ?? "");
  return parsePluginJson(jsonText);
}
