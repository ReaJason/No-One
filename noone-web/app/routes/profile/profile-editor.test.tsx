import { render, screen } from "@testing-library/react";
import { MemoryRouter, UNSAFE_ErrorResponseImpl } from "react-router";

import { ErrorBoundary } from "@/routes/profile/profile-editor";

describe("ProfileEditor ErrorBoundary", () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("renders the profile-specific 404 state with a return link", () => {
    render(
      <MemoryRouter>
        <ErrorBoundary
          error={new UNSAFE_ErrorResponseImpl(404, "Not Found", "Profile not found", false)}
          params={{ profileId: "missing-profile" }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText("404")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Profile not found" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "The requested profile could not be found. Return to the profile list to choose another profile.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Return to profile list" })).toHaveAttribute(
      "href",
      "/profiles",
    );
  });

  it("renders the generic error state for unexpected errors", () => {
    render(
      <MemoryRouter>
        <ErrorBoundary error={new Error("boom")} params={{}} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Error" })).toBeInTheDocument();
    expect(screen.getByText("boom")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Return to profile list" })).toHaveAttribute(
      "href",
      "/profiles",
    );
  });
});
