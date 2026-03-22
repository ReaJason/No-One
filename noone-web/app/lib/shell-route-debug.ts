type ShellNavigationTrace = {
  traceId: string;
  from: string;
  to: string;
  startedAt: number;
};

declare global {
  interface Window {
    __NOONE_SHELL_ROUTE_NAV__?: ShellNavigationTrace | null;
  }
}

export function createShellRouteTraceId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export function getShellRouteRequestKind(url: URL) {
  return url.pathname.endsWith(".data") ? "data" : "document";
}

export function logShellRouteDebug(
  scope: string,
  message: string,
  payload?: Record<string, unknown>,
) {
  if (!import.meta.env.DEV) return;
  if (payload) {
    console.log(`[${scope}] ${message}`, payload);
    return;
  }
  console.log(`[${scope}] ${message}`);
}

function getNow() {
  if (typeof performance !== "undefined") {
    return performance.now();
  }
  return Date.now();
}

export function markShellNavigationTrace({ from, to }: { from: string; to: string }) {
  if (!import.meta.env.DEV || typeof window === "undefined") {
    return null;
  }

  const trace: ShellNavigationTrace = {
    traceId: createShellRouteTraceId(),
    from,
    to,
    startedAt: getNow(),
  };
  window.__NOONE_SHELL_ROUTE_NAV__ = trace;
  return trace;
}

export function readShellNavigationTrace(pathname?: string | null) {
  if (!import.meta.env.DEV || typeof window === "undefined") {
    return null;
  }

  const trace = window.__NOONE_SHELL_ROUTE_NAV__ ?? null;
  if (!trace) {
    return null;
  }
  if (pathname && trace.to !== pathname) {
    return null;
  }
  return trace;
}

export function clearShellNavigationTrace(pathname?: string | null) {
  if (!import.meta.env.DEV || typeof window === "undefined") {
    return;
  }

  const trace = readShellNavigationTrace(pathname);
  if (trace) {
    window.__NOONE_SHELL_ROUTE_NAV__ = null;
  }
}

export function getShellNavigationDurationMs(pathname?: string | null) {
  const trace = readShellNavigationTrace(pathname);
  if (!trace) {
    return null;
  }
  return Math.round(getNow() - trace.startedAt);
}
