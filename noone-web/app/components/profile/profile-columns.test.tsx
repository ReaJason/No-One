import type { Profile } from "@/types/profile";

import { fireEvent, render, screen, waitFor } from "@testing-library/react";

const { navigateMock, submitMock, toastErrorMock, toastSuccessMock } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  submitMock: vi.fn(),
  toastErrorMock: vi.fn(),
  toastSuccessMock: vi.fn(),
}));

type MockFetcherState = "idle" | "submitting" | "loading";

type MockFetcher = {
  Form: typeof MockFetcherForm;
  data?: { success?: boolean; errors?: Record<string, string> };
  state: MockFetcherState;
};

let mockFetcher: MockFetcher;

function MockFetcherForm({
  action,
  children,
  method,
  ...props
}: React.ComponentPropsWithoutRef<"form">) {
  return (
    <form
      {...props}
      onSubmit={(event) => {
        event.preventDefault();
        submitMock(new FormData(event.currentTarget), { action, method });
      }}
    >
      {children}
    </form>
  );
}

vi.mock("react-router", async () => {
  const actual = await vi.importActual<typeof import("react-router")>("react-router");

  return {
    ...actual,
    useFetcher: () => mockFetcher,
    useNavigate: () => navigateMock,
  };
});

vi.mock("sonner", () => ({
  toast: {
    error: toastErrorMock,
    success: toastSuccessMock,
  },
}));

vi.mock("@/components/ui/dropdown-menu", () => ({
  DropdownMenu: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  DropdownMenuContent: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  DropdownMenuGroup: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  DropdownMenuItem: ({
    children,
    className,
    disabled,
    onClick,
  }: React.PropsWithChildren<{
    className?: string;
    disabled?: boolean;
    onClick?: () => void;
  }>) => (
    <button type="button" className={className} disabled={disabled} onClick={onClick}>
      {children}
    </button>
  ),
  DropdownMenuLabel: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  DropdownMenuSeparator: () => <hr />,
  DropdownMenuTrigger: ({
    render,
  }: {
    render: React.ReactElement<React.ComponentPropsWithoutRef<"button">>;
  }) => render,
}));

vi.mock("@/components/ui/alert-dialog", () => ({
  AlertDialog: ({
    children,
    open,
  }: React.PropsWithChildren<{
    onOpenChange?: (open: boolean) => void;
    open?: boolean;
  }>) => <div>{open ? children : null}</div>,
  AlertDialogAction: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  AlertDialogCancel: ({
    children,
    disabled,
    onClick,
  }: React.PropsWithChildren<{
    disabled?: boolean;
    onClick?: () => void;
  }>) => (
    <button type="button" disabled={disabled} onClick={onClick}>
      {children}
    </button>
  ),
  AlertDialogContent: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  AlertDialogDescription: ({ children }: React.PropsWithChildren) => <p>{children}</p>,
  AlertDialogFooter: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  AlertDialogHeader: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
  AlertDialogTitle: ({ children }: React.PropsWithChildren) => <h2>{children}</h2>,
}));

import { ProfileActionsCell } from "@/components/profile/profile-columns";

const profile: Profile = {
  id: "profile-1",
  name: "Alpha",
  protocolType: "HTTP",
  protocolConfig: { type: "HTTP" },
  requestTransformations: [],
  responseTransformations: [],
  createdAt: "2024-01-01T00:00:00Z",
  updatedAt: "2024-01-02T00:00:00Z",
};

function renderCell() {
  return render(<ProfileActionsCell profile={{ ...profile }} />);
}

describe("ProfileActionsCell", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    submitMock.mockReset();
    toastSuccessMock.mockReset();
    toastErrorMock.mockReset();
    mockFetcher = {
      Form: MockFetcherForm,
      data: undefined,
      state: "idle",
    };
  });

  it("opens the delete dialog and submits the existing delete payload", async () => {
    renderCell();

    fireEvent.click(screen.getByRole("button", { name: /delete/i }));

    expect(
      screen.getByRole("heading", { name: /are you sure you want to delete this profile/i }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    expect(submitMock).toHaveBeenCalledTimes(1);

    const [formData, options] = submitMock.mock.calls[0] as [
      FormData,
      { action?: string; method?: string },
    ];
    expect(formData.get("intent")).toBe("delete");
    expect(formData.get("profileId")).toBe("profile-1");
    expect(options).toEqual({ action: "/profiles", method: "post" });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Confirm" })).toBeInTheDocument();
    });
  });

  it("closes the dialog and shows a success toast after a successful delete", async () => {
    const { rerender } = renderCell();

    fireEvent.click(screen.getByRole("button", { name: /delete/i }));

    mockFetcher = {
      ...mockFetcher,
      state: "submitting",
    };
    rerender(<ProfileActionsCell profile={{ ...profile }} />);

    mockFetcher = {
      ...mockFetcher,
      state: "loading",
    };
    rerender(<ProfileActionsCell profile={{ ...profile }} />);

    mockFetcher = {
      ...mockFetcher,
      data: { success: true },
      state: "idle",
    };
    rerender(<ProfileActionsCell profile={{ ...profile }} />);

    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith("Profile deleted");
      expect(
        screen.queryByRole("heading", {
          name: /are you sure you want to delete this profile/i,
        }),
      ).not.toBeInTheDocument();
    });
  });

  it("keeps the dialog open and shows the delete error when the action fails", async () => {
    const { rerender } = renderCell();

    fireEvent.click(screen.getByRole("button", { name: /delete/i }));

    mockFetcher = {
      ...mockFetcher,
      state: "submitting",
    };
    rerender(<ProfileActionsCell profile={{ ...profile }} />);

    mockFetcher = {
      ...mockFetcher,
      data: { errors: { general: "Failed to delete profile" } },
      state: "idle",
    };
    rerender(<ProfileActionsCell profile={{ ...profile }} />);

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith("Failed to delete profile");
      expect(screen.getByText("Failed to delete profile")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: /are you sure you want to delete this profile/i }),
      ).toBeInTheDocument();
    });
  });
});
