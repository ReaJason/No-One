import { render, screen } from "@testing-library/react";

import { ShellManagerSkeleton, ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import { loader } from "@/routes/shell/shell-manager";

const createAuthFetchMock = vi.fn();
const getShellConnectionByIdMock = vi.fn();

vi.mock("@/api/api.server", () => ({
  createAuthFetch: (...args: unknown[]) => createAuthFetchMock(...args),
}));

vi.mock("@/api/shell-connection-api", () => ({
  getShellConnectionById: (...args: unknown[]) => getShellConnectionByIdMock(...args),
}));

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("shell manager loader", () => {
  beforeEach(() => {
    createAuthFetchMock.mockReset();
    getShellConnectionByIdMock.mockReset();
  });

  it("returns a promise-backed shell payload so the route can suspend", async () => {
    const deferred = createDeferred<{
      id: number;
      url: string;
      status: "CONNECTED";
      language: string;
    }>();
    const authFetch = vi.fn();

    createAuthFetchMock.mockReturnValue(authFetch);
    getShellConnectionByIdMock.mockReturnValue(deferred.promise);

    const result = (await loader({
      context: {} as never,
      params: { shellId: "42" },
      request: new Request("http://localhost/shells/42/info"),
      unstable_pattern: "/shells/:shellId",
    } as never)) as {
      shell: Promise<{
        id: number;
        url: string;
        status: "CONNECTED";
        language: string;
      }>;
    };

    expect(createAuthFetchMock).toHaveBeenCalled();
    expect(getShellConnectionByIdMock).toHaveBeenCalledWith("42", authFetch);
    expect(result.shell).toBeInstanceOf(Promise);

    deferred.resolve({
      id: 42,
      url: "https://shell.example",
      status: "CONNECTED",
      language: "java",
    });

    await expect(result.shell).resolves.toMatchObject({
      id: 42,
      url: "https://shell.example",
    });
  });

  it("rejects the shell promise with a 404 response when the shell is missing", async () => {
    createAuthFetchMock.mockReturnValue(vi.fn());
    getShellConnectionByIdMock.mockResolvedValue(null);

    const result = (await loader({
      context: {} as never,
      params: { shellId: "404" },
      request: new Request("http://localhost/shells/404/info"),
      unstable_pattern: "/shells/:shellId",
    } as never)) as { shell: Promise<unknown> };

    await expect(result.shell).rejects.toMatchObject({
      status: 404,
      statusText: "",
    });
  });
});

describe("shell loading fallbacks", () => {
  it("renders an accessible manager skeleton", () => {
    render(<ShellManagerSkeleton />);

    expect(screen.getByRole("status", { name: /loading shell manager/i })).toBeInTheDocument();
  });

  it("renders an accessible section skeleton", () => {
    render(<ShellSectionSkeleton label="Loading shell section" variant="command" />);

    expect(screen.getByRole("status", { name: /loading shell section/i })).toBeInTheDocument();
  });
});
