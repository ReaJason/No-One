import type { FetchOptions } from "ofetch";
import type { AuthFetch } from "@/api.server";
import type { ShellPluginDispatchRequest } from "@/types/shell";
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
