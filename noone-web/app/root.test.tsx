import { render, screen } from "@testing-library/react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router";

import { AuthRedirectError } from "@/lib/auth-redirect-error";
import { ErrorBoundary } from "@/root";

describe("Root ErrorBoundary", () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("renders the unauthorized recovery page for auth redirect errors", () => {
    const authRedirectError = new AuthRedirectError("refresh-failed");

    render(
      <MemoryRouter>
        <ErrorBoundary error={authRedirectError} params={{}} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Session expired" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Log in again" })).toBeInTheDocument();
  });

  it("does not throw during server rendering for auth redirect errors", () => {
    const authRedirectError = new AuthRedirectError("refresh-failed");

    expect(() =>
      renderToStaticMarkup(<ErrorBoundary error={authRedirectError} params={{}} />),
    ).not.toThrow();
  });

  it("renders the unauthorized recovery page for serialized auth redirect errors", () => {
    render(
      <MemoryRouter>
        <ErrorBoundary error={new Error("__AUTH_REDIRECT__:refresh-failed")} params={{}} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Session expired" })).toBeInTheDocument();
  });
});
