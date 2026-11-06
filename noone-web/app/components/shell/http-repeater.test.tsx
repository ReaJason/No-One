import { fireEvent, render, screen } from "@testing-library/react";

import HttpRepeater from "@/components/shell/http-repeater";

vi.mock("@/hooks/use-shell-route-fetcher", () => ({
  useShellRouteFetcher: () => ({ submit: vi.fn() }),
}));

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe("HttpRepeater", () => {
  beforeEach(() => {
    vi.stubGlobal("crypto", {});
    vi.stubGlobal("ResizeObserver", ResizeObserverMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders and adds parameter rows when randomUUID is unavailable", () => {
    render(<HttpRepeater shellId={1} actionPath="/shells/1/repeater" />);

    expect(screen.getAllByPlaceholderText("Param name")).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: "Add" }));

    expect(screen.getAllByPlaceholderText("Param name")).toHaveLength(2);
  });
});
