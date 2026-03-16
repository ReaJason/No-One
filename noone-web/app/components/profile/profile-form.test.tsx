import type { ProfileFormSeed } from "@/routes/profile/profile-form.shared";
import type { ComponentProps } from "react";

import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Plus } from "lucide-react";

vi.mock("react-router", async () => {
  const React = await import("react");
  const actual = await vi.importActual<typeof import("react-router")>("react-router");

  return {
    ...actual,
    Form: React.forwardRef<HTMLFormElement, React.ComponentPropsWithoutRef<"form">>(
      function MockForm(props, ref) {
        return <form ref={ref} {...props} />;
      },
    ),
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
    ...props
  }: React.ComponentPropsWithoutRef<"select"> & { className?: string }) {
    const context = React.useContext(SelectContext);

    return (
      <select
        {...props}
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

import { ProfileForm } from "@/components/profile/profile-form";
import { DEFAULT_REQUEST_TEMPLATES, DEFAULT_RESPONSE_TEMPLATES } from "@/types/profile";

type ProfileFormProps = ComponentProps<typeof ProfileForm>;

const defaultInitialValues: ProfileFormSeed = {
  name: "Alpha Profile",
  password: "",
  protocolType: "HTTP",
  identifierLocation: "HEADER",
  identifierOperator: "EQUALS",
  identifierName: "X-Profile",
  identifierValue: "alpha",
  requestMethod: "POST",
  requestTemplate: DEFAULT_REQUEST_TEMPLATES.JSON,
  responseTemplate: DEFAULT_RESPONSE_TEMPLATES.JSON,
  requestBodyType: "JSON",
  responseBodyType: "JSON",
  responseStatusCode: "200",
  requestHeaders: '{"Content-Type":"application/json"}',
  responseHeaders: '{"Cache-Control":"no-cache"}',
  messageTemplate: "",
  wsResponseTemplate: "",
  subprotocol: "",
  messageFormat: "TEXT",
  handshakeHeaders: "",
  requestCompression: "None",
  requestEncryption: "None",
  requestEncoding: "None",
  responseCompression: "None",
  responseEncryption: "None",
  responseEncoding: "None",
};

function buildProps(overrides: Partial<ProfileFormProps> = {}): ProfileFormProps {
  return {
    icon: Plus,
    initialValues: defaultInitialValues,
    mode: "create",
    onCancel: vi.fn(),
    submitLabel: "Create Profile",
    ...overrides,
  };
}

function buildInitialValues(overrides: Partial<ProfileFormSeed> = {}): ProfileFormSeed {
  return {
    ...defaultInitialValues,
    ...overrides,
  };
}

function renderProfileForm(overrides: Partial<ProfileFormProps> = {}) {
  const result = render(<ProfileForm {...buildProps(overrides)} />);

  return {
    ...result,
    rerenderWith(nextOverrides: Partial<ProfileFormProps> = {}) {
      result.rerender(<ProfileForm {...buildProps(nextOverrides)} />);
    },
  };
}

function getHiddenInput(container: HTMLElement, name: string) {
  const input = container.querySelector(`input[type="hidden"][name="${name}"]`);
  if (!(input instanceof HTMLInputElement)) {
    throw new Error(`Hidden input "${name}" not found`);
  }
  return input;
}

function getSelectTrigger(container: HTMLElement, hiddenInputName: string) {
  const hiddenInput = getHiddenInput(container, hiddenInputName);
  const trigger = hiddenInput.parentElement?.querySelector(
    'select[data-slot="mock-select-trigger"]',
  );
  if (!(trigger instanceof HTMLSelectElement)) {
    throw new Error(`Select trigger for "${hiddenInputName}" not found`);
  }
  return trigger;
}

async function chooseSelectOption(container: HTMLElement, hiddenInputName: string, label: string) {
  await act(async () => {
    fireEvent.change(getSelectTrigger(container, hiddenInputName), {
      target: { value: label === "WebSocket" ? "WEBSOCKET" : label },
    });
  });
}

describe("ProfileForm", () => {
  it("syncs seed changes into controlled fields without remounting the form", async () => {
    const firstValues = buildInitialValues({
      name: "Alpha Profile",
      protocolType: "HTTP",
      requestMethod: "POST",
      requestBodyType: "JSON",
      requestTemplate: DEFAULT_REQUEST_TEMPLATES.JSON,
      responseTemplate: DEFAULT_RESPONSE_TEMPLATES.JSON,
    });
    const nextValues = buildInitialValues({
      name: "Bravo Profile",
      protocolType: "WEBSOCKET",
      identifierLocation: "MESSAGE_FRAME",
      messageFormat: "BINARY",
      subprotocol: "graphql-ws",
    });

    const { container, rerenderWith } = renderProfileForm({ initialValues: firstValues });

    expect(screen.getByPlaceholderText("Profile unique name")).toHaveValue("Alpha Profile");
    expect(getHiddenInput(container, "protocolType")).toHaveValue("HTTP");
    expect(screen.getByLabelText("Request Template")).toHaveValue(DEFAULT_REQUEST_TEMPLATES.JSON);
    expect(screen.getByText("HTTP Config")).toBeInTheDocument();

    rerenderWith({ initialValues: nextValues });

    await waitFor(() => {
      expect(screen.getByPlaceholderText("Profile unique name")).toHaveValue("Bravo Profile");
      expect(getHiddenInput(container, "protocolType")).toHaveValue("WEBSOCKET");
      expect(getHiddenInput(container, "messageFormat")).toHaveValue("BINARY");
      expect(screen.getByText("WebSocket Config")).toBeInTheDocument();
      expect(screen.getByLabelText("Subprotocol")).toHaveValue("graphql-ws");
    });
  });

  it("replaces request and response templates when the existing value is blank or still the previous default", async () => {
    const initialValues = buildInitialValues({
      requestBodyType: "JSON",
      requestTemplate: DEFAULT_REQUEST_TEMPLATES.JSON,
      responseBodyType: "JSON",
      responseTemplate: "",
    });
    const { container } = renderProfileForm({ initialValues });

    await chooseSelectOption(container, "requestBodyType", "XML");

    await waitFor(() => {
      expect(getHiddenInput(container, "requestBodyType")).toHaveValue("XML");
      expect(screen.getByLabelText("Request Template")).toHaveValue(DEFAULT_REQUEST_TEMPLATES.XML);
    });

    await chooseSelectOption(container, "responseBodyType", "XML");

    await waitFor(() => {
      expect(getHiddenInput(container, "responseBodyType")).toHaveValue("XML");
      expect(screen.getByLabelText("Response Template")).toHaveValue(
        DEFAULT_RESPONSE_TEMPLATES.XML,
      );
    });
  });

  it("preserves custom request and response templates when body types change", async () => {
    const initialValues = buildInitialValues({
      requestTemplate: "custom-request-template",
      responseTemplate: "custom-response-template",
    });
    const { container } = renderProfileForm({ initialValues });

    fireEvent.change(screen.getByLabelText("Request Template"), {
      target: { value: "custom-request-template" },
    });
    fireEvent.change(screen.getByLabelText("Response Template"), {
      target: { value: "custom-response-template" },
    });

    await chooseSelectOption(container, "requestBodyType", "XML");
    await chooseSelectOption(container, "responseBodyType", "XML");

    await waitFor(() => {
      expect(getHiddenInput(container, "requestBodyType")).toHaveValue("XML");
      expect(getHiddenInput(container, "responseBodyType")).toHaveValue("XML");
      expect(screen.getByLabelText("Request Template")).toHaveValue("custom-request-template");
      expect(screen.getByLabelText("Response Template")).toHaveValue("custom-response-template");
    });
  });
});
