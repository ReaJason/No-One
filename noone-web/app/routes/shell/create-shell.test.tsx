import { fireEvent, render, screen } from "@testing-library/react";

import CreateOrEditShell, { shouldRevalidate } from "@/routes/shell/create-shell";

const navigateMock = vi.fn();
const useLoaderDataMock = vi.fn();
const useParamsMock = vi.fn();
const submitMock = vi.fn();

vi.mock("react-router", async () => {
  const React = await import("react");
  const actual = await vi.importActual<typeof import("react-router")>("react-router");

  type Option = {
    label: string;
    value: string;
  };

  const SelectItem = ({ children }: React.PropsWithChildren<{ value: string }>) => <>{children}</>;

  function flattenText(node: React.ReactNode): string {
    if (typeof node === "string" || typeof node === "number") {
      return String(node);
    }
    if (!node) {
      return "";
    }
    if (Array.isArray(node)) {
      return node.map(flattenText).join("");
    }
    if (React.isValidElement<{ children?: React.ReactNode }>(node)) {
      return flattenText(node.props.children);
    }
    return "";
  }

  function collectOptions(children: React.ReactNode): Option[] {
    const options: Option[] = [];

    React.Children.forEach(children, (child) => {
      if (!React.isValidElement<{ children?: React.ReactNode; value?: string }>(child)) {
        return;
      }

      if (child.type === SelectItem) {
        options.push({
          label: flattenText(child.props.children),
          value: child.props.value ?? "",
        });
        return;
      }

      if (child.props.children) {
        options.push(...collectOptions(child.props.children));
      }
    });

    return options;
  }

  const SelectContext = React.createContext<{
    onValueChange?: (value: string) => void;
    options: Option[];
    value?: string;
  }>({ options: [] });

  function Select({
    children,
    onValueChange,
    value,
  }: React.PropsWithChildren<{ onValueChange?: (value: string) => void; value?: string }>) {
    const options = React.useMemo(() => collectOptions(children), [children]);

    return (
      <SelectContext.Provider value={{ onValueChange, options, value }}>
        {children}
      </SelectContext.Provider>
    );
  }

  function SelectTrigger({
    className,
    id,
    ...props
  }: React.ComponentPropsWithoutRef<"select"> & { className?: string }) {
    const context = React.useContext(SelectContext);

    return (
      <select
        {...props}
        id={id}
        className={className}
        data-slot="mock-select-trigger"
        value={context.value ?? ""}
        onChange={(event) => context.onValueChange?.(event.target.value)}
      >
        {context.options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    );
  }

  return {
    ...actual,
    Select,
    SelectContent: ({ children }: React.PropsWithChildren) => <>{children}</>,
    SelectGroup: ({ children }: React.PropsWithChildren) => <>{children}</>,
    SelectItem,
    SelectTrigger,
    SelectValue: ({ placeholder }: { placeholder?: string }) => <span>{placeholder}</span>,
    useFetcher: () => ({
      state: "idle",
      data: undefined,
      submit: submitMock,
      load: vi.fn(),
    }),
    useLoaderData: () => useLoaderDataMock(),
    useNavigate: () => navigateMock,
    useParams: () => useParamsMock(),
  };
});

vi.mock("@/components/ui/select", async () => {
  const React = await import("react");

  type Option = {
    label: string;
    value: string;
  };

  const SelectItem = ({ children }: React.PropsWithChildren<{ value: string }>) => <>{children}</>;

  function flattenText(node: React.ReactNode): string {
    if (typeof node === "string" || typeof node === "number") {
      return String(node);
    }
    if (!node) {
      return "";
    }
    if (Array.isArray(node)) {
      return node.map(flattenText).join("");
    }
    if (React.isValidElement<{ children?: React.ReactNode }>(node)) {
      return flattenText(node.props.children);
    }
    return "";
  }

  function collectOptions(children: React.ReactNode): Option[] {
    const options: Option[] = [];

    React.Children.forEach(children, (child) => {
      if (!React.isValidElement<{ children?: React.ReactNode; value?: string }>(child)) {
        return;
      }

      if (child.type === SelectItem) {
        options.push({
          label: flattenText(child.props.children),
          value: child.props.value ?? "",
        });
        return;
      }

      if (child.props.children) {
        options.push(...collectOptions(child.props.children));
      }
    });

    return options;
  }

  const SelectContext = React.createContext<{
    onValueChange?: (value: string) => void;
    options: Option[];
    value?: string;
  }>({ options: [] });

  function Select({
    children,
    onValueChange,
    value,
  }: React.PropsWithChildren<{ onValueChange?: (value: string) => void; value?: string }>) {
    const options = React.useMemo(() => collectOptions(children), [children]);

    return (
      <SelectContext.Provider value={{ onValueChange, options, value }}>
        {children}
      </SelectContext.Provider>
    );
  }

  function SelectTrigger({
    className,
    id,
    ...props
  }: React.ComponentPropsWithoutRef<"select"> & { className?: string }) {
    const context = React.useContext(SelectContext);

    return (
      <select
        {...props}
        id={id}
        className={className}
        data-slot="mock-select-trigger"
        value={context.value ?? ""}
        onChange={(event) => context.onValueChange?.(event.target.value)}
      >
        {context.options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    );
  }

  return {
    Select,
    SelectContent: ({ children }: React.PropsWithChildren) => <>{children}</>,
    SelectGroup: ({ children }: React.PropsWithChildren) => <>{children}</>,
    SelectItem,
    SelectTrigger,
    SelectValue: ({ placeholder }: { placeholder?: string }) => <span>{placeholder}</span>,
  };
});

vi.mock("@/components/ui/checkbox", () => ({
  Checkbox: ({
    checked,
    onCheckedChange,
    id,
    disabled,
  }: {
    checked?: boolean;
    onCheckedChange?: (checked: boolean) => void;
    id?: string;
    disabled?: boolean;
  }) => (
    <input
      id={id}
      type="checkbox"
      checked={checked}
      disabled={disabled}
      onChange={(event) => onCheckedChange?.(event.target.checked)}
    />
  ),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

describe("CreateOrEditShell", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    submitMock.mockReset();
    useLoaderDataMock.mockReset();
    useParamsMock.mockReset();

    useParamsMock.mockReturnValue({});
  });

  it("includes prefilled shellType in form data even when the visible field is disabled", () => {
    useLoaderDataMock.mockReturnValue({
      projects: [{ id: "7", name: "Alpha" }],
      profiles: [{ id: "1", name: "HTTP", protocolType: "HTTP" }],
      prefill: {
        url: "http://127.0.0.1:8082/app/b64asdfasdf",
        shellType: "Listener",
        staging: true,
        loaderProfileId: "1",
        firstProfileId: "1",
        firstProjectId: "7",
      },
    });

    render(<CreateOrEditShell />);

    const shellTypeInput = screen.getByLabelText("Shell Type");
    expect(shellTypeInput).toBeDisabled();
    expect(shellTypeInput).toHaveValue("Listener");
  });

  it("preserves typed input when loader data is revalidated with an equal prefill", () => {
    const buildLoaderData = () => ({
      projects: [{ id: "7", name: "Alpha" }],
      profiles: [{ id: "1", name: "HTTP", protocolType: "HTTP" }],
      prefill: {
        url: "http://127.0.0.1:8082/app/b64asdfasdf",
        shellType: "Listener",
        staging: false,
        loaderProfileId: "1",
        firstProfileId: "1",
        firstProjectId: "7",
      },
    });

    useLoaderDataMock.mockReturnValue(buildLoaderData());
    const { rerender } = render(<CreateOrEditShell />);

    const nameInput = screen.getByLabelText("Name *");
    fireEvent.change(nameInput, { target: { value: "typed shell name" } });
    expect(nameInput).toHaveValue("typed shell name");

    // Simulate the loader revalidation that follows the "Test Connection" action:
    // the same values arrive in freshly allocated objects.
    useLoaderDataMock.mockReturnValue(buildLoaderData());
    rerender(<CreateOrEditShell />);

    expect(screen.getByLabelText("Name *")).toHaveValue("typed shell name");
  });
});

describe("shouldRevalidate", () => {
  it("skips loader revalidation after a connection test", () => {
    const formData = new FormData();
    formData.set("intent", "test-config");

    expect(shouldRevalidate({ formData, defaultShouldRevalidate: true } as never)).toBe(false);
  });

  it("keeps the router default for other submissions", () => {
    const formData = new FormData();
    formData.set("intent", "create-shell");

    expect(shouldRevalidate({ formData, defaultShouldRevalidate: true } as never)).toBe(true);
    expect(shouldRevalidate({ defaultShouldRevalidate: true } as never)).toBe(true);
  });
});
