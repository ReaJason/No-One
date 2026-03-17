import { render, screen } from "@testing-library/react";
import { MemoryRouter, UNSAFE_ErrorResponseImpl } from "react-router";

import { ErrorBoundary } from "@/routes/project/project-editor";

describe("ProjectEditor ErrorBoundary", () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("renders the project-specific 404 state with a return link", () => {
    render(
      <MemoryRouter>
        <ErrorBoundary
          error={new UNSAFE_ErrorResponseImpl(404, "Not Found", "Project not found", false)}
          params={{ projectId: "missing-project" }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText("404")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Project not found" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to Project" })).toHaveAttribute(
      "href",
      "/projects",
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
