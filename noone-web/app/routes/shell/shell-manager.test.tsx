import { render, screen } from "@testing-library/react";

import { ShellManagerSkeleton, ShellSectionSkeleton } from "@/components/shell/shell-route-loading";
import {
  getActiveShellSectionPath,
  getPendingShellSectionPath,
  loader,
} from "@/routes/shell/shell-manager";

const createAuthFetchMock = vi.fn();
const getShellConnectionByIdMock = vi.fn();
const getPluginsMock = vi.fn();

vi.mock("@/api/api.server", () => ({
  createAuthFetch: (...args: unknown[]) => createAuthFetchMock(...args),
}));

vi.mock("@/api/shell-connection-api", () => ({
  getShellConnectionById: (...args: unknown[]) => getShellConnectionByIdMock(...args),
}));

vi.mock("@/api/plugin-api", () => ({
  getPlugins: (...args: unknown[]) => getPluginsMock(...args),
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
    getPluginsMock.mockReset();
    getPluginsMock.mockResolvedValue({ content: [] });
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
    getPluginsMock.mockResolvedValue({
      content: [{ id: "system-info" }, { id: "command-execute" }],
    });

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
      standardPluginIds: Promise<string[]>;
    };

    expect(createAuthFetchMock).toHaveBeenCalled();
    expect(getShellConnectionByIdMock).toHaveBeenCalledWith("42", authFetch);
    expect(result.shell).toBeInstanceOf(Promise);
    expect(result.standardPluginIds).toBeInstanceOf(Promise);

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
    await expect(result.standardPluginIds).resolves.toEqual(["system-info", "command-execute"]);
  });

  it("rejects the shell promise with a 404 response when the shell is missing", async () => {
    createAuthFetchMock.mockReturnValue(vi.fn());
    getShellConnectionByIdMock.mockResolvedValue(null);

    const result = (await loader({
      context: {} as never,
      params: { shellId: "404" },
      request: new Request("http://localhost/shells/404/info"),
      unstable_pattern: "/shells/:shellId",
    } as never)) as { shell: Promise<unknown>; standardPluginIds: Promise<unknown> };

    result.standardPluginIds.catch(() => {});

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

describe("getPendingShellSectionPath", () => {
  it("returns the next child route when navigating within the same shell", () => {
    expect(
      getPendingShellSectionPath({
        currentPathname: "/shells/5/info",
        nextPathname: "/shells/5/files",
        shellId: 5,
      }),
    ).toBe("/shells/5/files");
  });

  it("returns null when the navigation stays on the same child route", () => {
    expect(
      getPendingShellSectionPath({
        currentPathname: "/shells/5/info",
        nextPathname: "/shells/5/info",
        shellId: 5,
      }),
    ).toBeNull();
  });

  it("returns null when the navigation leaves the current shell manager", () => {
    expect(
      getPendingShellSectionPath({
        currentPathname: "/shells/5/info",
        nextPathname: "/shells/9/files",
        shellId: 5,
      }),
    ).toBeNull();
  });
});

describe("getActiveShellSectionPath", () => {
  it("prefers the pending child route within the same shell", () => {
    expect(
      getActiveShellSectionPath({
        currentPathname: "/shells/5/info",
        nextPathname: "/shells/5/files",
        shellId: 5,
      }),
    ).toBe("/shells/5/files");
  });

  it("falls back to the current pathname when there is no pending shell child route", () => {
    expect(
      getActiveShellSectionPath({
        currentPathname: "/shells/5/info",
        nextPathname: null,
        shellId: 5,
      }),
    ).toBe("/shells/5/info");
  });

  it("ignores pending navigations that leave the current shell manager", () => {
    expect(
      getActiveShellSectionPath({
        currentPathname: "/shells/5/info",
        nextPathname: "/shells",
        shellId: 5,
      }),
    ).toBe("/shells/5/info");
  });
});
