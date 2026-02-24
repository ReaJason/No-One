export interface PluginArgSchemaField {
  name: string;
  type: string;
  label?: string;
  required?: boolean;
  description?: string;
  default?: string;
}

export interface PluginResultSchema {
  type: string;
  fields?: PluginArgSchemaField[];
}

export interface PluginAction {
  name: string;
  description?: string;
  argSchema?: PluginArgSchemaField[];
  resultSchema?: PluginResultSchema;
}

export interface Plugin {
  id: string;
  name: string;
  version: string;
  language: string;
  type: string;
  actions?: Record<string, PluginAction>;
  createdAt: string;
  updatedAt: string;
}
