import type { ProjectFormValues } from "@/routes/project/project-form.shared";
import type { ComponentProps } from "react";

import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Plus } from "lucide-react";

import { ProjectForm } from "@/components/project/project-form";

const submitMock = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual<typeof import("react-router")>("react-router");

  return {
    ...actual,
    useFetcher: () => ({
      state: "idle",
      data: undefined,
      submit: submitMock,
      load: vi.fn(),
    }),
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
  }: {
    checked?: boolean;
    onCheckedChange?: (checked: boolean) => void;
  }) => (
    <input
      type="checkbox"
      checked={checked}
      onChange={(event) => onCheckedChange?.(event.target.checked)}
    />
  ),
}));

vi.mock("@/components/ui/popover", () => ({
  Popover: ({ children }: React.PropsWithChildren) => <>{children}</>,
  PopoverContent: ({ children }: React.PropsWithChildren) => <>{children}</>,
  PopoverTrigger: ({ render }: { render: React.ReactNode }) => <>{render}</>,
}));

vi.mock("@/components/ui/calendar", () => ({
  Calendar: () => <div data-testid="mock-calendar" />,
}));

vi.mock("@/components/ui/scroll-area", () => ({
  ScrollArea: ({ children, className }: React.PropsWithChildren<{ className?: string }>) => (
    <div className={className}>{children}</div>
  ),
}));

type ProjectFormProps = ComponentProps<typeof ProjectForm>;

const defaultInitialValues: ProjectFormValues = {
  name: "Alpha Project",
  code: "ALPHA",
  status: "ACTIVE",
  bizName: "Acme Corp",
  description: "Existing description",
  startedAt: "2026-03-20T09:00",
  endedAt: "2026-03-21T09:00",
  remark: "Existing remark",
  memberIds: [1],
};

const defaultUsers = [
  {
    id: 1,
    username: "alice",
    email: "alice@example.com",
    status: "ENABLED" as const,
    mfaEnabled: false,
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
  },
  {
    id: 2,
    username: "bob",
    email: "bob@example.com",
    status: "ENABLED" as const,
    mfaEnabled: false,
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
  },
];

function buildProps(overrides: Partial<ProjectFormProps> = {}): ProjectFormProps {
  return {
    action: "/projects/create",
    icon: Plus,
    initialValues: defaultInitialValues,
    mode: "create",
    onCancel: vi.fn(),
    submitLabel: "Create Project",
    users: defaultUsers,
    ...overrides,
  };
}

function buildInitialValues(overrides: Partial<ProjectFormValues> = {}): ProjectFormValues {
  return {
    ...defaultInitialValues,
    ...overrides,
  };
}

function renderProjectForm(overrides: Partial<ProjectFormProps> = {}) {
  const result = render(<ProjectForm {...buildProps(overrides)} />);

  return {
    ...result,
    rerenderWith(nextOverrides: Partial<ProjectFormProps> = {}) {
      result.rerender(<ProjectForm {...buildProps(nextOverrides)} />);
    },
  };
}

function getSelectByLabel(labelText: string): HTMLSelectElement {
  const label = screen.getByText(labelText);
  const field = label.closest('[data-slot="field"]');
  const select = field?.querySelector('select[data-slot="mock-select-trigger"]');
  if (!(select instanceof HTMLSelectElement)) {
    throw new Error(`Select for "${labelText}" not found`);
  }
  return select;
}

async function chooseSelectOption(labelText: string, value: string) {
  await act(async () => {
    fireEvent.change(getSelectByLabel(labelText), {
      target: { value },
    });
  });
}

describe("ProjectForm", () => {
  beforeEach(() => {
    submitMock.mockReset();
  });

  it("syncs seed changes into controlled fields without remounting the form", async () => {
    const firstValues = buildInitialValues({
      name: "Alpha Project",
      code: "ALPHA",
      status: "ACTIVE",
      startedAt: "2026-03-20T09:00",
      endedAt: "",
      memberIds: [1],
    });
    const nextValues = buildInitialValues({
      name: "Bravo Project",
      code: "BRAVO",
      status: "ARCHIVED",
      startedAt: "",
      endedAt: "2026-03-25T10:00",
      memberIds: [2],
    });

    const { rerenderWith } = renderProjectForm({ initialValues: firstValues });

    await waitFor(() => {
      expect(screen.getByPlaceholderText("Internal Sandbox")).toHaveValue("Alpha Project");
      expect(screen.getByPlaceholderText("SANDBOX")).toHaveValue("ALPHA");
      expect(getSelectByLabel("Status *")).toHaveValue("ACTIVE");
      expect(screen.getByRole("checkbox", { name: /alice/i })).toBeChecked();
      expect(screen.getByRole("checkbox", { name: /bob/i })).not.toBeChecked();
      expect(screen.getByRole("button", { name: /clear start date/i })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /clear end date/i })).not.toBeInTheDocument();
    });

    rerenderWith({ initialValues: nextValues });

    await waitFor(() => {
      expect(screen.getByPlaceholderText("Internal Sandbox")).toHaveValue("Bravo Project");
      expect(screen.getByPlaceholderText("SANDBOX")).toHaveValue("BRAVO");
      expect(getSelectByLabel("Status *")).toHaveValue("ARCHIVED");
      expect(screen.getByRole("checkbox", { name: /alice/i })).not.toBeChecked();
      expect(screen.getByRole("checkbox", { name: /bob/i })).toBeChecked();
      expect(screen.queryByRole("button", { name: /clear start date/i })).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /clear end date/i })).toBeInTheDocument();
    });
  });

  it("validates required fields and end date ordering before submit", async () => {
    renderProjectForm({
      initialValues: buildInitialValues({
        startedAt: "2026-03-20T09:00",
        endedAt: "2026-03-19T09:00",
      }),
    });

    fireEvent.change(screen.getByPlaceholderText("Internal Sandbox"), {
      target: { value: "" },
    });
    fireEvent.change(screen.getByPlaceholderText("SANDBOX"), {
      target: { value: "" },
    });

    const form = screen.getByRole("button", { name: "Create Project" }).closest("form");
    expect(form).not.toBeNull();

    await act(async () => {
      fireEvent.submit(form!);
    });

    await waitFor(() => {
      expect(screen.getByText("Project name is required")).toBeInTheDocument();
      expect(screen.getByText("Project code is required")).toBeInTheDocument();
      expect(screen.getByText("End date must be on or after the start date")).toBeInTheDocument();
    });
    expect(submitMock).not.toHaveBeenCalled();
  });

  it("submits FormData that preserves repeated memberIds", async () => {
    renderProjectForm();

    await chooseSelectOption("Status *", "ARCHIVED");
    fireEvent.change(screen.getByPlaceholderText("Search users..."), {
      target: { value: "bob" },
    });
    fireEvent.click(screen.getByRole("checkbox", { name: /bob/i }));

    const form = screen.getByRole("button", { name: "Create Project" }).closest("form");
    expect(form).not.toBeNull();

    await act(async () => {
      fireEvent.submit(form!);
    });

    await waitFor(() => {
      expect(submitMock).toHaveBeenCalledTimes(1);
    });

    const [formData, options] = submitMock.mock.calls[0] as [
      FormData,
      { action?: string; method?: string },
    ];
    expect(options).toMatchObject({
      action: "/projects/create",
      method: "post",
    });
    expect(Array.from(formData.entries())).toEqual([
      ["name", "Alpha Project"],
      ["code", "ALPHA"],
      ["status", "ARCHIVED"],
      ["bizName", "Acme Corp"],
      ["description", "Existing description"],
      ["startedAt", "2026-03-20T09:00"],
      ["endedAt", "2026-03-21T09:00"],
      ["remark", "Existing remark"],
      ["memberIds", "1"],
      ["memberIds", "2"],
    ]);
  });
});
