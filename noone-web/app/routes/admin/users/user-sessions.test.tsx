import { render, screen } from "@testing-library/react";
import { MemoryRouter, UNSAFE_ErrorResponseImpl } from "react-router";

import UserSessions, { ErrorBoundary } from "@/routes/admin/users/user-sessions";

const useLoaderDataMock = vi.fn();
const reactUseMock = vi.fn();

let resolvedSessionResponse = {
  content: [],
  total: 0,
  page: 1,
  pageSize: 20,
  totalPages: 0,
};

type UseDataTableOptions = {
  columns: unknown;
};

let useDataTableCalls: UseDataTableOptions[] = [];

vi.mock("react", async () => {
  const actual = await import("react");

  return {
    ...actual,
    use: (value: unknown) => reactUseMock(value),
  };
});

vi.mock("react-router", async () => {
  const actual = await import("react-router");
  return {
    ...actual,
    useLoaderData: () => useLoaderDataMock(),
  };
});

vi.mock("@/components/data-table/data-table", () => ({
  DataTable: ({ children }: React.PropsWithChildren) => <div>{children}</div>,
}));

vi.mock("@/components/data-table/data-table-toolbar", () => ({
  DataTableToolbar: () => <div>toolbar</div>,
}));

vi.mock("@/components/data-table/data-table-skeleton", () => ({
  DataTableSkeleton: () => <div>loading</div>,
}));

vi.mock("@/hooks/use-data-table", () => ({
  useDataTable: (options: UseDataTableOptions) => {
    useDataTableCalls.push(options);
    return { table: {} };
  },
}));

describe("UserSessions route", () => {
  beforeEach(() => {
    useLoaderDataMock.mockReset();
    useDataTableCalls = [];
    resolvedSessionResponse = {
      content: [],
      total: 0,
      page: 1,
      pageSize: 20,
      totalPages: 0,
    };
    reactUseMock.mockReset();
    reactUseMock.mockImplementation((value: unknown) => {
      if (value && typeof (value as PromiseLike<unknown>).then === "function") {
        return resolvedSessionResponse;
      }

      return value;
    });
  });

  it("renders the session page heading and revoke-all action", () => {
    useLoaderDataMock.mockReturnValue({
      userId: 42,
      user: Promise.resolve({ id: 42, username: "alice", email: "alice@example.com" }),
      sessionResponse: Promise.resolve(resolvedSessionResponse),
    });

    render(
      <MemoryRouter>
        <UserSessions />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Sessions" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Revoke all sessions" })).toBeInTheDocument();
  });

  it("reuses the same columns definition across rerenders", async () => {
    useLoaderDataMock.mockReturnValue({
      userId: 42,
      user: Promise.resolve({ id: 42, username: "alice", email: "alice@example.com" }),
      sessionResponse: Promise.resolve(resolvedSessionResponse),
    });

    const { rerender } = render(
      <MemoryRouter>
        <UserSessions />
      </MemoryRouter>,
    );

    await screen.findByText("toolbar");

    const firstColumns = useDataTableCalls.at(-1)?.columns;

    rerender(
      <MemoryRouter>
        <UserSessions />
      </MemoryRouter>,
    );

    await screen.findByText("toolbar");

    const secondColumns = useDataTableCalls.at(-1)?.columns;

    expect(secondColumns).toBe(firstColumns);
  });

  it("renders the user-specific 404 state with a return link", () => {
    render(
      <MemoryRouter>
        <ErrorBoundary
          error={new UNSAFE_ErrorResponseImpl(404, "Not Found", "User not found", false)}
          params={{ userId: "missing-user" }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "User not found" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to Users" })).toHaveAttribute(
      "href",
      "/admin/users",
    );
  });
});
