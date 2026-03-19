export function readWhitelistIntent(formData: FormData) {
  return String(formData.get("intent") ?? "");
}

export function readWhitelistIpAddress(formData: FormData) {
  return String(formData.get("ipAddress") ?? "").trim();
}

export function readWhitelistEntryId(formData: FormData) {
  return Number(formData.get("entryId"));
}
