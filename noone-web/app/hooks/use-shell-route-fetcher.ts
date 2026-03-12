import { useCallback, useEffect, useRef } from "react";
import { useFetcher, type FetcherSubmitOptions } from "react-router";
import type { ShellRouteResult } from "@/lib/shell-route";

interface PendingRequest<T> {
  requestId: string;
  resolve: (value: T) => void;
  reject: (reason?: unknown) => void;
}

export function useShellRouteFetcher<T>() {
  const fetcher = useFetcher<ShellRouteResult<T>>();
  const pendingRef = useRef<PendingRequest<T> | null>(null);

  useEffect(() => {
    return () => {
      if (pendingRef.current) {
        pendingRef.current.reject(new Error("Request cancelled"));
        pendingRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (fetcher.state !== "idle" || !pendingRef.current) {
      return;
    }

    const pending = pendingRef.current;
    const data = fetcher.data;
    if (!data || data.requestId !== pending.requestId) {
      return;
    }

    pendingRef.current = null;
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

        pendingRef.current = { requestId, resolve, reject };
        fetcher.submit(formData, options);
      }),
    [fetcher],
  );

  const load = useCallback(
    (href: string, requestId: string) =>
      new Promise<T>((resolve, reject) => {
        if (pendingRef.current) {
          reject(new Error("Another request is already running"));
          return;
        }

        pendingRef.current = { requestId, resolve, reject };
        fetcher.load(href);
      }),
    [fetcher],
  );

  return {
    fetcher,
    submit,
    load,
  };
}
