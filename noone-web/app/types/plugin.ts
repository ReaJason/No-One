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

export type PluginRunMode = "sync" | "async" | "scheduled";

export type TaskStatus =
  | "SUBMITTED"
  | "SCHEDULED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export interface Plugin {
  id: string;
  name: string;
  version: string;
  language: string;
  type: string;
  runMode?: PluginRunMode;
  actions?: Record<string, PluginAction>;
  createdAt: string;
  updatedAt: string;
}
