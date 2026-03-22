import { render } from "@testing-library/react";

import MemoryCharts from "@/components/shell/system-info-memory-charts";

describe("MemoryCharts", () => {
  it("does not emit responsive container size warnings when rendering fixed-size charts", () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

    render(
      <MemoryCharts
        isJava={true}
        heapUsed={256}
        heapFree={768}
        heapUsagePercent="25.0"
        nonHeapUsed={128}
        nonHeapFree={384}
        nonHeapUsagePercent="25.0"
      />,
    );

    expect(warnSpy).not.toHaveBeenCalledWith(
      expect.stringContaining("The width(-1) and height(-1) of chart should be greater than 0"),
    );

    warnSpy.mockRestore();
  });
});
