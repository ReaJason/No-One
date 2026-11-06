import type { ShellConnection } from "@/types/shell-connection";

import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes } from "react-router";

import ShellFilesRoute from "@/routes/shell/shell-files";

vi.mock("@/components/shell/file-manager", () => ({
  default: ({ initialDirectory }: { initialDirectory: Record<string, unknown> | null }) => (
    <div
      data-testid="file-manager"
      data-initial-directory={initialDirectory === null ? "async" : "ready"}
    />
  ),
}));

const shell = {
  id: 1,
  basicInfo: null,
  os: "linux",
} as ShellConnection;

function ShellContext() {
  return <Outlet context={{ shell }} />;
}

describe("ShellFilesRoute", () => {
  it("renders the file manager before the initial directory request completes", async () => {
    render(
      <MemoryRouter initialEntries={["/shells/1/files"]}>
        <Routes>
          <Route element={<ShellContext />}>
            <Route path="/shells/1/files" element={<ShellFilesRoute />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    const fileManager = await screen.findByTestId("file-manager");
    expect(fileManager).toHaveAttribute("data-initial-directory", "async");
  });
});
