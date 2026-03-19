import { render, screen } from "@testing-library/react";

import LoginPage from "@/routes/auth/login";

const useActionDataMock = vi.fn();
const useLoaderDataMock = vi.fn();
const useNavigationMock = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual<typeof import("react-router")>("react-router");

  return {
    ...actual,
    Form: ({ children, ...props }: React.ComponentPropsWithoutRef<"form">) => (
      <form {...props}>{children}</form>
    ),
    useActionData: () => useActionDataMock(),
    useLoaderData: () => useLoaderDataMock(),
    useNavigation: () => useNavigationMock(),
  };
});

describe("LoginPage", () => {
  beforeEach(() => {
    useActionDataMock.mockReset();
    useLoaderDataMock.mockReset();
    useNavigationMock.mockReset();

    useActionDataMock.mockReturnValue(undefined);
    useLoaderDataMock.mockReturnValue({ returnTo: "/" });
    useNavigationMock.mockReturnValue({ state: "idle" });
  });

  it("autofocuses the username input", () => {
    render(<LoginPage />);

    expect(screen.getByLabelText(/username or email/i)).toHaveFocus();
  });
});
