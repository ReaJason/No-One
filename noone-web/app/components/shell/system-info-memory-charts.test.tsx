import { render, screen } from "@testing-library/react";

import MemoryCharts from "@/components/shell/system-info-memory-charts";

const defaultProps = {
  isJava: true,
  heapUsed: 256,
  heapFree: 768,
  heapUsagePercent: "25.0",
  nonHeapUsed: 128,
  nonHeapFree: 384,
  nonHeapUsagePercent: "25.0",
};

describe("MemoryCharts", () => {
  it("renders accessible fixed-size SVG donuts with the expected usage ratios", () => {
    render(<MemoryCharts {...defaultProps} />);

    expect(screen.getByRole("img", { name: "Heap memory: 25.0% used" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Non-Heap memory: 25.0% used" })).toBeInTheDocument();
    expect(screen.getByTestId("heap-used")).toHaveAttribute("stroke-dasharray", "25 100");
    expect(screen.getByTestId("non-heap-used")).toHaveAttribute("stroke-dasharray", "25 100");
  });

  it("renders only the heap chart for non-Java shells and handles empty memory data", () => {
    render(
      <MemoryCharts
        {...defaultProps}
        isJava={false}
        heapUsed={0}
        heapFree={0}
        heapUsagePercent="0.0"
      />,
    );

    expect(screen.getByRole("img", { name: "Heap memory: 0.0% used" })).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: /Non-Heap/ })).not.toBeInTheDocument();
    expect(screen.getByTestId("heap-used")).toHaveAttribute("stroke-dasharray", "0 100");
  });
});
