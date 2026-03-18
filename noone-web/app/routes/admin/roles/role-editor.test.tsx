import { render, screen } from "@testing-library/react";
import { MemoryRouter, UNSAFE_ErrorResponseImpl } from "react-router";

import { ErrorBoundary } from "@/routes/admin/roles/role-editor";

describe("RoleEditor ErrorBoundary", () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("renders the role-specific 404 state with a return link", () => {
    render(
      <MemoryRouter>
        <ErrorBoundary
          error={new UNSAFE_ErrorResponseImpl(404, "Not Found", "Role not found", false)}
          params={{ roleId: "missing-role" }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Role not found" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to Roles" })).toHaveAttribute(
      "href",
      "/admin/roles",
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
