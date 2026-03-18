import React from "react";

import { isAuthRedirectError } from "@/lib/auth-redirect-error";

type AuthRedirectErrorBoundaryProps = {
  children: React.ReactNode;
};

type AuthRedirectErrorBoundaryState = {
  error: unknown;
};

export class AuthRedirectErrorBoundary extends React.Component<
  AuthRedirectErrorBoundaryProps,
  AuthRedirectErrorBoundaryState
> {
  state: AuthRedirectErrorBoundaryState = {
    error: null,
  };

  static getDerivedStateFromError(error: unknown): AuthRedirectErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: unknown) {
    if (isAuthRedirectError(error)) {
      location.replace(
        "/auth/unauthorized?returnTo=" +
          encodeURIComponent(location.href.slice(location.origin.length)),
      );
    }
  }

  render() {
    const { error } = this.state;
    if (error) {
      if (isAuthRedirectError(error)) {
        return null;
      }
      throw error;
    }

    return this.props.children;
  }
}
