import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";

import FileManager from "@/components/shell/file-manager";

const fetcherMocks = vi.hoisted(() => ({
  load: vi.fn(),
  submit: vi.fn(),
}));

const editorHarness = vi.hoisted(() => ({
  renders: 0,
  props: null as {
    filePath: string | null;
    value: string;
    isDirty: boolean;
    onChange: (value: string) => void;
    onSave: () => void;
  } | null,
}));

vi.mock("@/hooks/use-shell-route-fetcher", () => ({
  useShellRouteFetcher: () => ({
    load: fetcherMocks.load,
    submit: fetcherMocks.submit,
  }),
}));

vi.mock("@tanstack/react-virtual", () => ({
  useVirtualizer: ({
    count,
    getItemKey,
  }: {
    count: number;
    getItemKey: (index: number) => string | number;
  }) => ({
    getVirtualItems: () =>
      Array.from({ length: count }, (_, index) => ({
        index,
        key: getItemKey(index),
        start: index * 40,
        end: (index + 1) * 40,
      })),
    getTotalSize: () => count * 40,
    scrollToOffset: vi.fn(),
    measure: vi.fn(),
  }),
}));

vi.mock("@/components/shell/file-editor-pane", () => ({
  default: (props: NonNullable<typeof editorHarness.props>) => {
    editorHarness.renders += 1;
    editorHarness.props = props;
    return <div data-testid="file-editor" />;
  },
}));

vi.mock("@/components/ui/dialog", () => ({
  Dialog: ({ open, children }: { open: boolean; children: React.ReactNode }) =>
    open ? children : null,
  DialogContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogDescription: ({ children }: { children: React.ReactNode }) => <p>{children}</p>,
  DialogFooter: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: React.ReactNode }) => <h2>{children}</h2>,
}));

const initialState = {
  osFamily: "unix" as const,
  cwd: "/",
  activeLocationId: "cwd",
  locations: [{ id: "cwd", label: "CWD", path: "/", kind: "cwd" as const }],
};

const initialDirectory = {
  path: "/",
  entries: [
    {
      name: "test.txt",
      path: "/test.txt",
      entryType: "file",
      fileType: "txt",
      sizeBytes: 8,
      createdAt: "2026-01-01T00:00:00Z",
      modifiedAt: "2026-01-01T00:00:00Z",
      permissions: "-rw-r--r--",
    },
  ],
};

describe("FileManager editor state", () => {
  beforeEach(() => {
    Object.defineProperty(Element.prototype, "scrollTo", {
      configurable: true,
      writable: true,
      value: vi.fn(),
    });
    editorHarness.renders = 0;
    editorHarness.props = null;
    fetcherMocks.load.mockReset();
    fetcherMocks.submit.mockReset();
    fetcherMocks.load.mockImplementation(async (href: string) => {
      const url = new URL(href, "http://localhost");
      if (url.searchParams.get("intent") === "read-all") {
        return { bytes: btoa("original"), fileSize: 8 };
      }
      return { path: "/", entries: [] };
    });
    fetcherMocks.submit.mockResolvedValue({ code: 0, data: {} });
  });

  it("renders only when dirty state changes and saves the latest draft", async () => {
    render(
      <FileManager
        initialState={initialState}
        initialDirectory={initialDirectory}
        dataPath="/shells/1/files/data"
      />,
    );

    fireEvent.doubleClick(screen.getByText("test.txt"));
    await waitFor(() => {
      expect(editorHarness.props?.value).toBe("original");
    });

    const renderCount = editorHarness.renders;
    act(() => {
      editorHarness.props?.onChange("a");
    });
    expect(editorHarness.renders).toBe(renderCount + 1);
    expect(editorHarness.props?.isDirty).toBe(true);

    const dirtyRenderCount = editorHarness.renders;
    act(() => {
      editorHarness.props?.onChange("ab");
      editorHarness.props?.onChange("abc");
    });
    expect(editorHarness.renders).toBe(dirtyRenderCount);

    await act(async () => {
      editorHarness.props?.onSave();
    });

    expect(fetcherMocks.submit).toHaveBeenCalledTimes(1);
    const formData = fetcherMocks.submit.mock.calls[0]?.[0] as FormData;
    const payload = JSON.parse(String(formData.get("payload")));
    expect(atob(payload.bytes)).toBe("abc");
    expect(editorHarness.props?.isDirty).toBe(false);
  });
});
