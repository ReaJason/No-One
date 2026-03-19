import { render, screen } from "@testing-library/react";
import { MemoryRouter, UNSAFE_ErrorResponseImpl } from "react-router";

import UserLoginLogs, { ErrorBoundary } from "@/routes/admin/users/user-login-logs";

const useLoaderDataMock = vi.fn();

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
  useDataTable: () => ({ table: {} }),
}));

describe("UserLoginLogs route", () => {
  it("renders the login log page heading", () => {
    useLoaderDataMock.mockReturnValue({
      user: Promise.resolve({ id: 42, username: "alice", email: "alice@example.com" }),
      loginLogResponse: Promise.resolve({
        content: [],
        total: 0,
        page: 1,
        pageSize: 20,
        totalPages: 0,
      }),
    });

    render(
      <MemoryRouter>
        <UserLoginLogs />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Login Logs" })).toBeInTheDocument();
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
