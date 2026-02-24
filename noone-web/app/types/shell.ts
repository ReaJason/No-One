export interface ShellPluginDispatchRequest {
  id: number;
  pluginId: string;
  action?: string;
  args?: Record<string, unknown>;
}
