import type { ChartConfig } from "@/components/ui/chart";

import { useMemo } from "react";
import { Pie, PieChart, Sector } from "recharts";

import { ChartContainer } from "@/components/ui/chart";

export type MemoryChartsProps = {
  isJava: boolean;
  heapUsed: number;
  heapFree: number;
  heapUsagePercent: string;
  nonHeapUsed: number;
  nonHeapFree: number;
  nonHeapUsagePercent: string;
};

function pieSectorShape(props: any) {
  return <Sector {...props} fill={props.payload?.fill ?? props.fill} />;
}

const CHART_SIZE = 96;

export default function MemoryCharts({
  isJava,
  heapUsed,
  heapFree,
  heapUsagePercent,
  nonHeapUsed,
  nonHeapFree,
  nonHeapUsagePercent,
}: MemoryChartsProps) {
  const heapConfig = useMemo(
    () =>
      ({
        used: {
          label: "Used",
          color: isJava ? "#3b82f6" : "#22c55e",
        },
        free: {
          label: "Free",
          theme: {
            light: "#e5e7eb",
            dark: "#27272a",
          },
        },
      }) satisfies ChartConfig,
    [isJava],
  );

  const nonHeapConfig = useMemo(
    () =>
      ({
        used: {
          label: "Used",
          color: "#a855f7",
        },
        free: {
          label: "Free",
          theme: {
            light: "#e5e7eb",
            dark: "#27272a",
          },
        },
      }) satisfies ChartConfig,
    [],
  );

  const heapData = useMemo(
    () => [
      { name: "used", value: heapUsed, fill: "var(--color-used)" },
      { name: "free", value: heapFree, fill: "var(--color-free)" },
    ],
    [heapFree, heapUsed],
  );

  const nonHeapData = useMemo(
    () => [
      { name: "used", value: nonHeapUsed, fill: "var(--color-used)" },
      { name: "free", value: nonHeapFree, fill: "var(--color-free)" },
    ],
    [nonHeapFree, nonHeapUsed],
  );

  if (isJava) {
    return (
      <div className="flex shrink-0 flex-col flex-wrap items-center justify-center gap-4">
        <div className="w-24">
          <div className="relative h-24 w-24">
            <ChartContainer
              config={heapConfig}
              responsive={false}
              className="aspect-square h-full w-full"
            >
              <PieChart width={CHART_SIZE} height={CHART_SIZE}>
                <Pie
                  data={heapData}
                  innerRadius={30}
                  outerRadius={45}
                  paddingAngle={0}
                  dataKey="value"
                  stroke="none"
                  shape={pieSectorShape}
                />
              </PieChart>
            </ChartContainer>
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <span className="text-xs font-bold">{heapUsagePercent}%</span>
            </div>
          </div>
          <p className="mt-1 text-center text-[10px] text-zinc-500">Heap</p>
        </div>

        <div className="w-24">
          <div className="relative h-24 w-24">
            <ChartContainer
              config={nonHeapConfig}
              responsive={false}
              className="aspect-square h-full w-full"
            >
              <PieChart width={CHART_SIZE} height={CHART_SIZE}>
                <Pie
                  data={nonHeapData}
                  innerRadius={30}
                  outerRadius={45}
                  paddingAngle={0}
                  dataKey="value"
                  stroke="none"
                  shape={pieSectorShape}
                />
              </PieChart>
            </ChartContainer>
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <span className="text-xs font-bold">{nonHeapUsagePercent}%</span>
            </div>
          </div>
          <p className="mt-1 text-center text-[10px] text-zinc-500">Non-Heap</p>
        </div>
      </div>
    );
  }

  return (
    <div className="relative h-24 w-24 flex-shrink-0">
      <ChartContainer
        config={heapConfig}
        responsive={false}
        className="aspect-square h-full w-full"
      >
        <PieChart width={CHART_SIZE} height={CHART_SIZE}>
          <Pie
            data={heapData}
            innerRadius={30}
            outerRadius={45}
            paddingAngle={0}
            dataKey="value"
            stroke="none"
            shape={pieSectorShape}
          />
        </PieChart>
      </ChartContainer>
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
        <span className="text-xs font-bold">{heapUsagePercent}%</span>
      </div>
    </div>
  );
}
