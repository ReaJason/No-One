export function resolveApiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message.trim();
  }
  if (!error || typeof error !== "object") {
    return fallback;
  }

  const candidate = error as {
    error?: string;
    errorMessage?: string;
    message?: string;
    details?: {
      error?: string;
      errorMessage?: string;
      message?: string;
    };
  };

  if (typeof candidate.error === "string" && candidate.error.trim()) {
    return candidate.error.trim();
  }
  if (typeof candidate.errorMessage === "string" && candidate.errorMessage.trim()) {
    return candidate.errorMessage.trim();
  }
  if (typeof candidate.message === "string" && candidate.message.trim()) {
    return candidate.message.trim();
  }
  if (typeof candidate.details?.error === "string" && candidate.details.error.trim()) {
    return candidate.details.error.trim();
  }
  if (
    typeof candidate.details?.errorMessage === "string" &&
    candidate.details.errorMessage.trim()
  ) {
    return candidate.details.errorMessage.trim();
  }
  if (typeof candidate.details?.message === "string" && candidate.details.message.trim()) {
    return candidate.details.message.trim();
  }

  return fallback;
}
