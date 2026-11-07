import type { PluginRuntimeStatus } from "@/types/plugin";

import { render, screen, waitFor } from "@testing-library/react";

import PluginRuntimeStatusCard from "@/components/shell/plugin-runtime-status";
import {
  type PluginStatusContextValue,
  PluginStatusContext,
} from "@/components/shell/plugin-status-context";

const NOT_LOADED: PluginRuntimeStatus = {
  pluginId: "system-info",
  serverVersion: "0.0.1",
  shellVersion: null,
  loaded: false,
  needsUpdate: false,
};

function renderCard(value: Partial<PluginStatusContextValue>) {
  const contextValue = {
    statuses: {},
    loading: false,
    refreshAll: vi.fn(),
    refreshOne: vi.fn(),
    updatePlugin: vi.fn(),
    updateAll: vi.fn(),
    updatingIds: new Set<string>(),
    ...value,
  } satisfies PluginStatusContextValue;

  return render(
    <PluginStatusContext.Provider value={contextValue}>
      <PluginRuntimeStatusCard pluginId="system-info" pluginName="System Info" />
    </PluginStatusContext.Provider>,
  );
}

describe("PluginRuntimeStatusCard", () => {
  it("loads an unloaded plugin automatically instead of offering install", async () => {
    const updatePlugin = vi.fn().mockResolvedValue(undefined);

    renderCard({ statuses: { "system-info": NOT_LOADED }, updatePlugin });

    await waitFor(() => expect(updatePlugin).toHaveBeenCalledTimes(1));
    expect(updatePlugin).toHaveBeenCalledWith("system-info");
    expect(screen.queryByRole("button", { name: /Install Plugin/i })).not.toBeInTheDocument();
    expect(screen.getByText("Installing...")).toBeInTheDocument();
  });

  it("does not auto-load a plugin that is already loaded", () => {
    const updatePlugin = vi.fn();

    renderCard({
      statuses: { "system-info": { ...NOT_LOADED, loaded: true, shellVersion: "0.0.1" } },
      updatePlugin,
    });

    expect(updatePlugin).not.toHaveBeenCalled();
    expect(screen.getByText("Ready")).toBeInTheDocument();
  });
});
