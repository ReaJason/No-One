const DEFAULT_AUTH_REDIRECT_PATH = "/auth/unauthorized";

export class AuthRedirectError extends Error {
  readonly redirectTo: string;
  readonly reason: string;

  constructor(reason: string, redirectTo = DEFAULT_AUTH_REDIRECT_PATH) {
    super("Authentication is no longer valid");
    this.name = "AuthRedirectError";
    this.reason = reason;
    this.redirectTo = redirectTo;
  }
}

export function isAuthRedirectError(error: unknown): error is AuthRedirectError {
  return (
    error instanceof AuthRedirectError ||
    (error instanceof Error && (error.message?.includes("Authentication") as boolean))
  );
}
