import type { AuthFetch } from "@/api.server";
import type { PluginRuntimeStatus } from "@/types/plugin";
import type { ShellPluginDispatchRequest } from "@/types/shell";
import type { FetchOptions } from "ofetch";

import { resolveApiErrorMessage } from "@/lib/api-error";

export type DispatchPluginOptions = FetchOptions<"json", any>;

export async function dispatchPlugin(
  dispatch: ShellPluginDispatchRequest,
  authFetch: AuthFetch,
  options: DispatchPluginOptions = {},
) {
  try {
    return await authFetch(`/shells/${dispatch.id}/dispatch`, {
      ...options,
      method: "POST",
      body: dispatch,
    });
  } catch (error) {
    throw new Error(resolveApiErrorMessage(error, "Dispatch plugin failed"));
  }
}

export async function getPluginStatus(
  shellId: number,
  pluginId: string,
  authFetch: AuthFetch,
): Promise<PluginRuntimeStatus> {
  try {
    return await authFetch(`/shells/${shellId}/plugins/${pluginId}/status`, {
      method: "GET",
    });
  } catch (error) {
    throw new Error(resolveApiErrorMessage(error, "Load plugin status failed"));
  }
}

export async function updatePlugin(
  shellId: number,
  pluginId: string,
  authFetch: AuthFetch,
): Promise<PluginRuntimeStatus> {
  try {
    return await authFetch(`/shells/${shellId}/plugins/${pluginId}/update`, {
      method: "POST",
    });
  } catch (error) {
    throw new Error(resolveApiErrorMessage(error, "Update plugin failed"));
  }
}
