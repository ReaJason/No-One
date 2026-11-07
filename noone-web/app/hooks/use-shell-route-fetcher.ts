import type { ShellRouteResult } from "@/lib/shell-route";

import { useCallback, useEffect, useRef } from "react";
import { useFetcher, type FetcherSubmitOptions } from "react-router";

interface PendingRequest<T> {
  requestId: string;
  resolve: (value: T) => void;
  reject: (reason?: unknown) => void;
}

export function useShellRouteFetcher<T>() {
  const fetcher = useFetcher<ShellRouteResult<T>>();
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  const pendingRef = useRef<PendingRequest<T> | null>(null);
  const activeRef = useRef(false);

  useEffect(() => {
    return () => {
      if (pendingRef.current) {
        pendingRef.current.reject(new Error("Request cancelled"));
        pendingRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (fetcher.state !== "idle") {
      if (pendingRef.current) {
        activeRef.current = true;
      }
      return;
    }

    if (!pendingRef.current) {
      return;
    }

    const pending = pendingRef.current;
    const data = fetcher.data;
    if (!data) {
      return;
    }

    if (data.requestId !== pending.requestId) {
      // Some error responses used to omit requestId. Only accept such a
      // response after the fetcher has actually run for the pending request,
      // otherwise it is stale data left over from the previous request.
      if (data.requestId != null || !activeRef.current) {
        return;
      }
    }

    pendingRef.current = null;
    activeRef.current = false;
    if (!data.ok) {
      pending.reject(new Error(data.error));
      return;
    }

    pending.resolve(data.data);
  }, [fetcher.data, fetcher.state]);

  const submit = useCallback(
    (formData: FormData, options: FetcherSubmitOptions, requestId: string) =>
      new Promise<T>((resolve, reject) => {
        if (pendingRef.current) {
          reject(new Error("Another request is already running"));
          return;
        }

        activeRef.current = false;
        pendingRef.current = { requestId, resolve, reject };
        fetcherRef.current.submit(formData, options);
      }),
    [],
  );

  const load = useCallback(
    (href: string, requestId: string) =>
      new Promise<T>((resolve, reject) => {
        if (pendingRef.current) {
          reject(new Error("Another request is already running"));
          return;
        }

        activeRef.current = false;
        pendingRef.current = { requestId, resolve, reject };
        fetcherRef.current.load(href);
      }),
    [],
  );

  return {
    fetcher,
    submit,
    load,
  };
}
