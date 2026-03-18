import { render, screen } from "@testing-library/react";
import { MemoryRouter, UNSAFE_ErrorResponseImpl } from "react-router";

import { ErrorBoundary } from "@/routes/admin/permissions/permission-editor";

describe("PermissionEditor ErrorBoundary", () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("renders the permission-specific 404 state with a return link", () => {
    render(
      <MemoryRouter>
        <ErrorBoundary
          error={new UNSAFE_ErrorResponseImpl(404, "Not Found", "Permission not found", false)}
          params={{ permissionId: "missing-permission" }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Permission not found" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to Permissions" })).toHaveAttribute(
      "href",
      "/admin/permissions",
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
