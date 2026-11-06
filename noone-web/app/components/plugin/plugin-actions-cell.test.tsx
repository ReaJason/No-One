import type { Plugin } from "@/types/plugin";

import { fireEvent, render, screen } from "@testing-library/react";

import { PluginActionsCell } from "@/components/plugin/plugin-actions-cell";

const navigate = vi.fn();

vi.mock("react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router")>();
  return {
    ...actual,
    useNavigate: () => navigate,
    useFetcher: () => ({
      state: "idle",
      data: undefined,
      Form: ({ children }: { children: React.ReactNode }) => <form>{children}</form>,
    }),
  };
});

const plugin: Plugin = {
  databaseId: 42,
  id: "system-info",
  name: "System Info",
  version: "1.0.0",
  language: "java",
  type: "Standard",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("PluginActionsCell", () => {
  beforeEach(() => {
    navigate.mockClear();
  });

  it("navigates to the numeric database editor route", async () => {
    render(<PluginActionsCell plugin={plugin} />);

    fireEvent.click(screen.getByRole("button", { name: "Open menu" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: "Edit" }));

    expect(navigate).toHaveBeenCalledWith("/plugins/edit/42");
  });

  it("submits the numeric database ID when deleting", async () => {
    render(<PluginActionsCell plugin={plugin} />);

    fireEvent.click(screen.getByRole("button", { name: "Open menu" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: "Delete" }));

    expect(document.querySelector('input[name="pluginDbId"]')).toHaveValue("42");
  });
});
