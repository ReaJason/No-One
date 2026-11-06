import { useCallback, useEffect, useRef, useState } from "react";

import { createAuthFetch } from "@/api/api";
import { dispatchPlugin } from "@/api/shell-api";
import { getLatestShellOperation } from "@/api/shell-operation-log-api";

const DISPATCH_TIMEOUT_MS = 20_000;

interface UseShellPluginSnapshotOptions {
  shellId: number;
  pluginId: string;
  errorPrefix: string;
}

export function useShellPluginSnapshot<T = unknown>({
  shellId,
  pluginId,
  errorPrefix,
}: UseShellPluginSnapshotOptions) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const requestSequenceRef = useRef(0);
  const abortControllerRef = useRef<AbortController | null>(null);

  const load = useCallback(
    async (forceRefresh: boolean) => {
      const requestSequence = ++requestSequenceRef.current;
      abortControllerRef.current?.abort();
      const controller = new AbortController();
      abortControllerRef.current = controller;
      setLoading(true);
      setError(null);
      const timeout = window.setTimeout(() => controller.abort(), DISPATCH_TIMEOUT_MS);

      try {
        const authFetch = createAuthFetch(new Request(window.location.href));
        const cachedResult = forceRefresh
          ? null
          : await getLatestShellOperation(shellId, pluginId, authFetch).catch((cacheError) => {
              if (controller.signal.aborted) {
                throw cacheError;
              }
              return null;
            });
        const result = cachedResult?.result
          ? cachedResult.result
          : await dispatchPlugin({ id: shellId, pluginId }, authFetch, {
              signal: controller.signal,
            });

        if (requestSequence === requestSequenceRef.current) {
          setData(result as T);
        }
      } catch (requestError: any) {
        if (requestSequence === requestSequenceRef.current) {
          const message = controller.signal.aborted
            ? "Request timed out — the remote shell may be unresponsive"
            : requestError?.message || "Unknown error";
          setError(`${errorPrefix}: ${message}`);
        }
      } finally {
        window.clearTimeout(timeout);
        if (requestSequence === requestSequenceRef.current) {
          abortControllerRef.current = null;
          setLoading(false);
        }
      }
    },
    [errorPrefix, pluginId, shellId],
  );

  useEffect(() => {
    void load(false);
    return () => {
      requestSequenceRef.current += 1;
      abortControllerRef.current?.abort();
    };
  }, [load]);

  const refresh = useCallback(() => load(true), [load]);

  return { data, error, loading, refresh };
}
