export type MemoryChartsProps = {
  isJava: boolean;
  heapUsed: number;
  heapFree: number;
  heapUsagePercent: string;
  nonHeapUsed: number;
  nonHeapFree: number;
  nonHeapUsagePercent: string;
};

type MemoryDonutProps = {
  label: string;
  used: number;
  free: number;
  usagePercent: string;
  color: string;
  testId: string;
};

function MemoryDonut({
  label,
  used,
  free,
  usagePercent,
  color,
  testId,
}: Readonly<MemoryDonutProps>) {
  const total = used + free;
  const usedRatio = total > 0 ? Math.max(0, Math.min(1, used / total)) : 0;
  const accessibleLabel = `${label} memory: ${usagePercent}% used`;

  return (
    <div className="relative h-24 w-24">
      <svg
        viewBox="0 0 96 96"
        className="h-full w-full -rotate-90"
        role="img"
        aria-label={accessibleLabel}
      >
        <title>{accessibleLabel}</title>
        <circle
          cx="48"
          cy="48"
          r="37.5"
          fill="none"
          strokeWidth="15"
          stroke="currentColor"
          className="text-zinc-200 dark:text-zinc-800"
        />
        <circle
          data-testid={testId}
          cx="48"
          cy="48"
          r="37.5"
          pathLength="100"
          fill="none"
          strokeWidth="15"
          stroke="currentColor"
          strokeDasharray={`${usedRatio * 100} 100`}
          style={{ color }}
        />
      </svg>
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
        <span className="text-xs font-bold">{usagePercent}%</span>
      </div>
    </div>
  );
}

export default function MemoryCharts({
  isJava,
  heapUsed,
  heapFree,
  heapUsagePercent,
  nonHeapUsed,
  nonHeapFree,
  nonHeapUsagePercent,
}: Readonly<MemoryChartsProps>) {
  const heapChart = (
    <MemoryDonut
      label="Heap"
      used={heapUsed}
      free={heapFree}
      usagePercent={heapUsagePercent}
      color={isJava ? "#3b82f6" : "#22c55e"}
      testId="heap-used"
    />
  );

  if (!isJava) {
    return <div className="relative h-24 w-24 flex-shrink-0">{heapChart}</div>;
  }

  return (
    <div className="flex shrink-0 flex-col flex-wrap items-center justify-center gap-4">
      <div className="w-24">
        {heapChart}
        <p className="mt-1 text-center text-[10px] text-zinc-500">Heap</p>
      </div>
      <div className="w-24">
        <MemoryDonut
          label="Non-Heap"
          used={nonHeapUsed}
          free={nonHeapFree}
          usagePercent={nonHeapUsagePercent}
          color="#a855f7"
          testId="non-heap-used"
        />
        <p className="mt-1 text-center text-[10px] text-zinc-500">Non-Heap</p>
      </div>
    </div>
  );
}
