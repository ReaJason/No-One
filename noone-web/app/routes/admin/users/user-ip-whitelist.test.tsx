import { render, screen } from "@testing-library/react";
import { MemoryRouter, UNSAFE_ErrorResponseImpl } from "react-router";

import { createUserIpWhitelistColumns } from "@/components/user/user-ip-whitelist-columns";
import UserIpWhitelist, { ErrorBoundary } from "@/routes/admin/users/user-ip-whitelist";

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

describe("UserIpWhitelist route", () => {
  it("renders the whitelist page heading", () => {
    useLoaderDataMock.mockReturnValue({
      user: Promise.resolve({ id: 42, username: "alice", email: "alice@example.com" }),
      entriesPromise: Promise.resolve([]),
    });

    render(
      <MemoryRouter>
        <UserIpWhitelist />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Login IP Whitelist" })).toBeInTheDocument();
  });

  it("defines the whitelist table columns", () => {
    expect(createUserIpWhitelistColumns(42).map((column) => column.id)).toEqual(
      expect.arrayContaining(["ipAddress", "createdAt", "actions"]),
    );
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
