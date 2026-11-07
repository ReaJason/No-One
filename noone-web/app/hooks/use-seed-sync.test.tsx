import { renderHook } from "@testing-library/react";

import { useSeedSync } from "@/hooks/use-seed-sync";

describe("useSeedSync", () => {
  it("syncs once on mount and ignores new objects carrying identical values", () => {
    const sync = vi.fn();
    const { rerender } = renderHook(({ seed }) => useSeedSync(seed, sync), {
      initialProps: { seed: { name: "Alpha", ids: [1, 2] } },
    });

    expect(sync).toHaveBeenCalledTimes(1);

    rerender({ seed: { name: "Alpha", ids: [1, 2] } });
    expect(sync).toHaveBeenCalledTimes(1);

    rerender({ seed: { name: "Bravo", ids: [1, 2] } });
    expect(sync).toHaveBeenCalledTimes(2);
    expect(sync).toHaveBeenLastCalledWith({ name: "Bravo", ids: [1, 2] });
  });

  it("syncs again when only nested values change", () => {
    const sync = vi.fn();
    const { rerender } = renderHook(({ seed }) => useSeedSync(seed, sync), {
      initialProps: { seed: { name: "Alpha", ids: [1, 2] } },
    });

    rerender({ seed: { name: "Alpha", ids: [1, 2, 3] } });

    expect(sync).toHaveBeenCalledTimes(2);
  });
});
