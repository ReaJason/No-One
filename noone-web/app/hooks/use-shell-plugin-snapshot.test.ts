import { act, renderHook, waitFor } from "@testing-library/react";

import { useShellPluginSnapshot } from "@/hooks/use-shell-plugin-snapshot";

const { dispatchPluginMock, getLatestShellOperationMock } = vi.hoisted(() => ({
  dispatchPluginMock: vi.fn(),
  getLatestShellOperationMock: vi.fn(),
}));

vi.mock("@/api/shell-api", () => ({
  dispatchPlugin: dispatchPluginMock,
}));

vi.mock("@/api/shell-operation-log-api", () => ({
  getLatestShellOperation: getLatestShellOperationMock,
}));

const options = {
  shellId: 1,
  pluginId: "system-info",
  errorPrefix: "Failed to load system info",
};

describe("useShellPluginSnapshot", () => {
  beforeEach(() => {
    dispatchPluginMock.mockReset();
    getLatestShellOperationMock.mockReset();
  });

  it("uses cached data without dispatching the plugin", async () => {
    getLatestShellOperationMock.mockResolvedValue({ result: { data: { os: "linux" } } });

    const { result } = renderHook(() => useShellPluginSnapshot(options));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual({ data: { os: "linux" } });
    expect(dispatchPluginMock).not.toHaveBeenCalled();
  });

  it("keeps existing data visible while a forced refresh is pending", async () => {
    getLatestShellOperationMock.mockResolvedValue({ result: { data: { version: 1 } } });
    let resolveRefresh!: (value: unknown) => void;
    dispatchPluginMock.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRefresh = resolve;
        }),
    );
    const { result } = renderHook(() => useShellPluginSnapshot(options));
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      void result.current.refresh();
    });

    await waitFor(() => expect(result.current.loading).toBe(true));
    expect(result.current.data).toEqual({ data: { version: 1 } });
    expect(getLatestShellOperationMock).toHaveBeenCalledTimes(1);

    act(() => {
      resolveRefresh({ data: { version: 2 } });
    });
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual({ data: { version: 2 } });
  });

  it("aborts the pending dispatch when the page unmounts", async () => {
    getLatestShellOperationMock.mockResolvedValue(null);
    let signal: AbortSignal | undefined;
    dispatchPluginMock.mockImplementation(
      (_dispatch, _authFetch, requestOptions: { signal: AbortSignal }) => {
        signal = requestOptions.signal;
        return new Promise(() => {});
      },
    );
    const { unmount } = renderHook(() => useShellPluginSnapshot(options));

    await waitFor(() => expect(signal).toBeDefined());
    unmount();

    expect(signal?.aborted).toBe(true);
  });
});
