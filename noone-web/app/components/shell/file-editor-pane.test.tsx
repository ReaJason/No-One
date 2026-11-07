import { render, screen } from "@testing-library/react";

import FileEditorPane from "@/components/shell/file-editor-pane";

const editorMocks = vi.hoisted(() => ({
  reactImport: vi.fn(),
  monacoImport: vi.fn(),
  loaderConfig: vi.fn(),
}));

vi.mock("next-themes", () => ({
  useTheme: () => ({ resolvedTheme: "light", theme: "light" }),
}));

vi.mock("@monaco-editor/react", () => {
  editorMocks.reactImport();
  return {
    default: () => <div data-testid="monaco-editor" />,
    loader: { config: editorMocks.loaderConfig },
  };
});

vi.mock("@/lib/monaco-editor", () => {
  editorMocks.monacoImport();
  return { editor: {} };
});

const defaultProps = {
  filePath: null,
  language: "plaintext",
  value: "content",
  isDirty: false,
  isLoading: false,
  onMount: vi.fn(),
  onChange: vi.fn(),
  onSave: vi.fn(),
  onClose: vi.fn(),
};

describe("FileEditorPane", () => {
  it("loads Monaco only for an editable open file", async () => {
    const { rerender } = render(<FileEditorPane {...defaultProps} />);

    expect(screen.getByText("Open a text or code file to preview and edit.")).toBeInTheDocument();
    expect(editorMocks.reactImport).not.toHaveBeenCalled();
    expect(editorMocks.monacoImport).not.toHaveBeenCalled();

    rerender(
      <FileEditorPane
        {...defaultProps}
        filePath="/large.bin"
        readOnlyReason="Preview is disabled"
      />,
    );
    expect(screen.getByText("Preview is disabled for this file.")).toBeInTheDocument();
    expect(editorMocks.reactImport).not.toHaveBeenCalled();
    expect(editorMocks.monacoImport).not.toHaveBeenCalled();

    rerender(<FileEditorPane {...defaultProps} filePath="/test.txt" />);
    expect(await screen.findByTestId("monaco-editor")).toBeInTheDocument();
    expect(editorMocks.reactImport).toHaveBeenCalledTimes(1);
    expect(editorMocks.monacoImport).toHaveBeenCalledTimes(1);
    expect(editorMocks.loaderConfig).toHaveBeenCalledTimes(1);
  });
});
