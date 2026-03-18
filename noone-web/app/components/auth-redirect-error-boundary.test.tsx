import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";

import { AuthRedirectErrorBoundary } from "@/components/auth-redirect-error-boundary";
import { AuthRedirectError } from "@/lib/auth-redirect-error";

function ThrowAuthRedirectError() {
  throw new AuthRedirectError("refresh-failed");
  return null;
}

function ThrowGenericError() {
  throw new Error("boom");
  return null;
}

describe("AuthRedirectErrorBoundary", () => {
  it("navigates to /auth/unauthorized when an auth redirect error is thrown", async () => {
    render(
      <MemoryRouter initialEntries={["/profiles"]}>
        <Routes>
          <Route
            path="/profiles"
            element={
              <AuthRedirectErrorBoundary>
                <ThrowAuthRedirectError />
              </AuthRedirectErrorBoundary>
            }
          />
          <Route path="/auth/unauthorized" element={<div>Unauthorized route</div>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText("Unauthorized route")).toBeInTheDocument();
  });

  it("rethrows non-auth errors", () => {
    expect(() =>
      render(
        <MemoryRouter initialEntries={["/profiles"]}>
          <Routes>
            <Route
              path="/profiles"
              element={
                <AuthRedirectErrorBoundary>
                  <ThrowGenericError />
                </AuthRedirectErrorBoundary>
              }
            />
          </Routes>
        </MemoryRouter>,
      ),
    ).toThrow("boom");
  });
});
