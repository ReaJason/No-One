export interface ShellRouteSuccess<T> {
  ok: true;
  data: T;
  requestId?: string;
}

export interface ShellRouteFailure {
  ok: false;
  error: string;
  requestId?: string;
}

export type ShellRouteResult<T> = ShellRouteSuccess<T> | ShellRouteFailure;

export function buildShellRouteFormData(
  intent: string,
  payload: unknown,
  requestId: string,
): FormData {
  const formData = new FormData();
  formData.set("intent", intent);
  formData.set("payload", JSON.stringify(payload));
  formData.set("requestId", requestId);
  return formData;
}

export function createShellRouteRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function getShellRouteError<T>(result: ShellRouteResult<T> | undefined): string | null {
  if (!result || result.ok) {
    return null;
  }
  return result.error;
}
