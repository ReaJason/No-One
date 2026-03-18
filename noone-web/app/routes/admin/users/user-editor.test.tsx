import { render, screen } from "@testing-library/react";
import { MemoryRouter, UNSAFE_ErrorResponseImpl } from "react-router";

import { ErrorBoundary } from "@/routes/admin/users/user-editor";

describe("UserEditor ErrorBoundary", () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("renders the user-specific 404 state with a return link", () => {
    render(
      <MemoryRouter>
        <ErrorBoundary
          error={new UNSAFE_ErrorResponseImpl(404, "Not Found", "User not found", false)}
          params={{ userId: "missing-user" }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "User not found" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to Users" })).toHaveAttribute(
      "href",
      "/admin/users",
    );
  });

  it("rethrows unexpected errors to the parent boundary", () => {
    expect(() =>
      render(
        <MemoryRouter>
          <ErrorBoundary error={new Error("boom")} params={{}} />
        </MemoryRouter>,
      ),
    ).toThrow("boom");
  });
});
