import { useEffect, useRef } from "react";

/**
 * Runs `sync` only when the seed's values actually change.
 *
 * Route loaders allocate fresh objects on every revalidation. Syncing form
 * state on object identity therefore wipes in-progress edits whenever an
 * action - a failed submit, a connection test, or any other fetcher - triggers
 * revalidation. Comparing serialized values keeps genuine seed changes (for
 * example switching to another record) working while ignoring identity churn.
 */
export function useSeedSync<T>(seed: T, sync: (seed: T) => void) {
  const seedKey = JSON.stringify(seed);
  const syncedKeyRef = useRef<string | null>(null);

  useEffect(() => {
    if (syncedKeyRef.current === seedKey) {
      return;
    }

    syncedKeyRef.current = seedKey;
    sync(seed);
  }, [seed, seedKey, sync]);
}
