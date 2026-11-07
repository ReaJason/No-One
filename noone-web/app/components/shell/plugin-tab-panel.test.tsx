import type { Plugin } from "@/types/plugin";

import { act, fireEvent, render, screen } from "@testing-library/react";

import PluginTabPanel from "@/components/shell/plugin-tab-panel";

const fetcherMocks = vi.hoisted(() => ({
  load: vi.fn(),
  submit: vi.fn(),
  refreshOne: vi.fn(),
}));

vi.mock("@/hooks/use-shell-route-fetcher", () => ({
  useShellRouteFetcher: () => ({
    load: fetcherMocks.load,
    submit: fetcherMocks.submit,
  }),
}));

vi.mock("@/components/shell/plugin-status-context", () => ({
  usePluginStatusContext: () => ({
    statuses: {},
    updatingIds: new Set<string>(),
    refreshOne: fetcherMocks.refreshOne,
    updatePlugin: vi.fn(),
  }),
}));

const plugin: Plugin = {
  databaseId: 1,
  id: "async-plugin",
  name: "Async plugin",
  version: "1.0.0",
  language: "java",
  type: "builtin",
  runMode: "async",
  actions: { run: { name: "Run" } },
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const state = {
  selectedAction: "run",
  args: {},
  result: null,
  loading: false,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
}

function renderPanel(onStateChange = vi.fn()) {
  return render(
    <PluginTabPanel
      plugin={plugin}
      actionPath="/shells/1/plugin/action"
      taskStatusPath="/shells/1/plugin/task"
      pluginStatusPath="/shells/1/plugin/status"
      state={state}
      onStateChange={onStateChange}
    />,
  );
}

async function startTask() {
  fireEvent.click(screen.getByRole("button", { name: "Run" }));
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe("PluginTabPanel polling", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    fetcherMocks.load.mockReset();
    fetcherMocks.submit.mockReset();
    fetcherMocks.refreshOne.mockReset();
    fetcherMocks.submit.mockResolvedValue({ taskId: "task-1", status: "RUNNING" });
    fetcherMocks.refreshOne.mockResolvedValue(undefined);
    fetcherMocks.load.mockResolvedValue({ status: "COMPLETED", result: "done" });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("waits for the current request before scheduling the next poll", async () => {
    const firstPoll = deferred<{ status: string }>();
    fetcherMocks.load.mockImplementationOnce(() => firstPoll.promise);
    const { unmount } = renderPanel();

    await startTask();
    expect(fetcherMocks.load).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(10_000);
      await Promise.resolve();
    });
    expect(fetcherMocks.load).toHaveBeenCalledTimes(1);

    await act(async () => {
      firstPoll.resolve({ status: "RUNNING" });
      await firstPoll.promise;
    });
    expect(fetcherMocks.load).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(1499);
      await Promise.resolve();
    });
    expect(fetcherMocks.load).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(1);
      await Promise.resolve();
    });
    expect(fetcherMocks.load).toHaveBeenCalledTimes(2);
    unmount();
  });

  it("backs off without requesting task status while the document is hidden", async () => {
    let hidden = true;
    const originalHidden = Object.getOwnPropertyDescriptor(document, "hidden");
    Object.defineProperty(document, "hidden", {
      configurable: true,
      get: () => hidden,
    });
    const { unmount } = renderPanel();

    try {
      await startTask();
      expect(fetcherMocks.load).not.toHaveBeenCalled();

      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
      });
      expect(fetcherMocks.load).not.toHaveBeenCalled();

      hidden = false;
      await act(async () => {
        vi.advanceTimersByTime(5000);
        await Promise.resolve();
      });
      expect(fetcherMocks.load).toHaveBeenCalledTimes(1);
    } finally {
      unmount();
      if (originalHidden) {
        Object.defineProperty(document, "hidden", originalHidden);
      }
    }
  });
});
