import type { PluginRuntimeStatus } from "@/types/plugin";

import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import { useShellRouteFetcher } from "@/hooks/use-shell-route-fetcher";
import { createShellRouteRequestId } from "@/lib/shell-route";

interface PluginStatusContextValue {
  statuses: Record<string, PluginRuntimeStatus>;
  loading: boolean;
  refreshAll: () => Promise<void>;
  refreshOne: (pluginId: string) => Promise<void>;
  updatePlugin: (pluginId: string) => Promise<void>;
  updateAll: () => Promise<void>;
  updatingIds: Set<string>;
}

const PluginStatusContext = createContext<PluginStatusContextValue | null>(null);

export function usePluginStatusContext() {
  const ctx = useContext(PluginStatusContext);
  if (!ctx) {
    throw new Error("usePluginStatusContext must be used within PluginStatusProvider");
  }
  return ctx;
}

export function usePluginStatusContextOptional() {
  return useContext(PluginStatusContext);
}

interface PluginStatusProviderProps {
  shellId: number;
  children: ReactNode;
}

export function PluginStatusProvider({ shellId, children }: PluginStatusProviderProps) {
  const [statuses, setStatuses] = useState<Record<string, PluginRuntimeStatus>>({});
  const [loading, setLoading] = useState(true);
  const [updatingIds, setUpdatingIds] = useState<Set<string>>(new Set());

  const statusesPath = `/shells/${shellId}/plugin-statuses`;
  const { load: loadStatuses } = useShellRouteFetcher<Record<string, PluginRuntimeStatus>>();
  const { load: loadOneStatus } = useShellRouteFetcher<PluginRuntimeStatus>();
  const { submit: submitUpdate } = useShellRouteFetcher<Record<string, PluginRuntimeStatus>>();

  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const refreshAll = useCallback(async () => {
    try {
      setLoading(true);
      const requestId = createShellRouteRequestId();
      const url = new URL(statusesPath, window.location.origin);
      url.searchParams.set("requestId", requestId);
      const data = await loadStatuses(`${url.pathname}${url.search}`, requestId);
      if (mountedRef.current) {
        setStatuses(data);
      }
    } catch {
      // keep existing statuses on error
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, [loadStatuses, statusesPath]);

  const refreshOne = useCallback(
    async (pluginId: string) => {
      try {
        const requestId = createShellRouteRequestId();
        const url = new URL(`/shells/${shellId}/extensions/plugin-status`, window.location.origin);
        url.searchParams.set("pluginId", pluginId);
        url.searchParams.set("requestId", requestId);
        const data = await loadOneStatus(`${url.pathname}${url.search}`, requestId);
        if (mountedRef.current) {
          setStatuses((prev) => ({ ...prev, [pluginId]: data }));
        }
      } catch {
        // ignore single refresh failure
      }
    },
    [loadOneStatus, shellId],
  );

  const updatePluginFn = useCallback(
    async (pluginId: string) => {
      setUpdatingIds((prev) => new Set(prev).add(pluginId));
      try {
        const requestId = createShellRouteRequestId();
        const formData = new FormData();
        formData.set("pluginIds", pluginId);
        formData.set("requestId", requestId);
        const result = await submitUpdate(
          formData,
          { method: "post", action: statusesPath },
          requestId,
        );
        if (mountedRef.current && result[pluginId] && !("error" in result[pluginId])) {
          setStatuses((prev) => ({
            ...prev,
            [pluginId]: result[pluginId] as unknown as PluginRuntimeStatus,
          }));
        }
      } finally {
        if (mountedRef.current) {
          setUpdatingIds((prev) => {
            const next = new Set(prev);
            next.delete(pluginId);
            return next;
          });
        }
      }
    },
    [submitUpdate, statusesPath],
  );

  const updateAll = useCallback(async () => {
    const outdated = Object.values(statuses).filter((s) => s.needsUpdate || !s.loaded);
    if (outdated.length === 0) return;

    const ids = outdated.map((s) => s.pluginId);
    setUpdatingIds(new Set(ids));
    try {
      const requestId = createShellRouteRequestId();
      const formData = new FormData();
      formData.set("pluginIds", ids.join(","));
      formData.set("requestId", requestId);
      const result = await submitUpdate(
        formData,
        { method: "post", action: statusesPath },
        requestId,
      );
      if (mountedRef.current) {
        setStatuses((prev) => {
          const next = { ...prev };
          for (const [pid, status] of Object.entries(result)) {
            if (status && !("error" in (status as any))) {
              next[pid] = status as unknown as PluginRuntimeStatus;
            }
          }
          return next;
        });
      }
    } finally {
      if (mountedRef.current) {
        setUpdatingIds(new Set());
      }
    }
  }, [statuses, submitUpdate, statusesPath]);

  useEffect(() => {
    refreshAll();
  }, [refreshAll]);

  const value = useMemo(
    () => ({
      statuses,
      loading,
      refreshAll,
      refreshOne,
      updatePlugin: updatePluginFn,
      updateAll,
      updatingIds,
    }),
    [statuses, loading, refreshAll, refreshOne, updatePluginFn, updateAll, updatingIds],
  );

  return <PluginStatusContext.Provider value={value}>{children}</PluginStatusContext.Provider>;
}
