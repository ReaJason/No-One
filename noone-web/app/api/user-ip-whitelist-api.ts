import type { AuthFetch } from "@/api/api.server";
import type { UserIpWhitelistEntry } from "@/types/admin";

const baseUrl = "/users";

export interface CreateUserIpWhitelistRequest {
  ipAddress: string;
}

export type ReplaceUserIpWhitelistRequest = string[];

export async function getUserIpWhitelist(
  id: number,
  authFetch: AuthFetch,
): Promise<UserIpWhitelistEntry[]> {
  return await authFetch<UserIpWhitelistEntry[]>(`${baseUrl}/${id}/ip-whitelist`);
}

export async function createUserIpWhitelistEntry(
  id: number,
  body: CreateUserIpWhitelistRequest,
  authFetch: AuthFetch,
): Promise<UserIpWhitelistEntry> {
  return await authFetch<UserIpWhitelistEntry>(`${baseUrl}/${id}/ip-whitelist`, {
    method: "POST",
    body,
  });
}

export async function replaceUserIpWhitelist(
  id: number,
  body: ReplaceUserIpWhitelistRequest,
  authFetch: AuthFetch,
): Promise<UserIpWhitelistEntry[]> {
  return await authFetch<UserIpWhitelistEntry[]>(`${baseUrl}/${id}/ip-whitelist`, {
    method: "PUT",
    body,
  });
}

export async function deleteUserIpWhitelistEntry(
  id: number,
  entryId: number,
  authFetch: AuthFetch,
): Promise<void> {
  await authFetch(`${baseUrl}/${id}/ip-whitelist/${entryId}`, {
    method: "DELETE",
  });
}
