function extractDataContainer(payload: unknown): Record<string, unknown> {
  if (!payload || typeof payload !== "object") {
    return {};
  }

  const record = payload as Record<string, unknown>;
  if (record.data && typeof record.data === "object") {
    return record.data as Record<string, unknown>;
  }
  if (record.result && typeof record.result === "object") {
    return record.result as Record<string, unknown>;
  }
  return record;
}

export function ensureShellDispatchPayload(payload: unknown): Record<string, unknown> {
  const container = extractDataContainer(payload);
  const code = container.code;
  if (typeof code === "number" && code !== 0) {
    throw new Error(
      typeof container.error === "string" ? container.error : "Plugin dispatch failed",
    );
  }

  const result = (container.data as Record<string, unknown> | undefined) ?? container;
  if (typeof result.error === "string" && result.error.length > 0) {
    throw new Error(result.error);
  }

  return result;
}
