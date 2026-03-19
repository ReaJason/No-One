import { render, screen } from "@testing-library/react";

import CreateOrEditShell from "@/routes/shell/create-shell";

const navigateMock = vi.fn();
const useActionDataMock = vi.fn();
const useLoaderDataMock = vi.fn();
const useNavigationMock = vi.fn();
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
    Form: ({ children, ...props }: React.ComponentPropsWithoutRef<"form">) => (
      <form {...props}>{children}</form>
    ),
    Select,
    SelectContent: ({ children }: React.PropsWithChildren) => <>{children}</>,
    SelectGroup: ({ children }: React.PropsWithChildren) => <>{children}</>,
    SelectItem,
    SelectTrigger,
    SelectValue: ({ placeholder }: { placeholder?: string }) => <span>{placeholder}</span>,
    useActionData: () => useActionDataMock(),
    useFetcher: () => ({
      state: "idle",
      data: undefined,
      submit: submitMock,
      load: vi.fn(),
    }),
    useLoaderData: () => useLoaderDataMock(),
    useNavigate: () => navigateMock,
    useNavigation: () => useNavigationMock(),
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
    useActionDataMock.mockReset();
    useLoaderDataMock.mockReset();
    useNavigationMock.mockReset();
    useParamsMock.mockReset();

    useActionDataMock.mockReturnValue(undefined);
    useNavigationMock.mockReturnValue({ state: "idle", formMethod: undefined });
    useParamsMock.mockReturnValue({});
  });

  it("includes prefilled shellType in form data even when the visible field is disabled", () => {
    useLoaderDataMock.mockReturnValue({
      shellUrlParam: "http://127.0.0.1:8082/app/b64asdfasdf",
      profileIdParam: "",
      loaderProfileIdParam: "1",
      shellTypeParam: "Listener",
      stagingParam: true,
      languageParam: undefined,
      projects: [{ id: 7, name: "Alpha" }],
      profiles: [{ id: 1, name: "HTTP", protocolType: "HTTP" }],
      initialProjectId: undefined,
    });

    render(<CreateOrEditShell />);

    expect(screen.getByLabelText("Shell Type")).toBeDisabled();

    const form = screen.getByRole("button", { name: "Create Shell" }).closest("form");
    expect(form).not.toBeNull();

    const formData = new FormData(form!);
    expect(formData.get("shellType")).toBe("Listener");
  });
});
