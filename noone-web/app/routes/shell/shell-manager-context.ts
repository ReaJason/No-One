import type { ShellConnection } from "@/types/shell-connection";

import { useOutletContext } from "react-router";

export interface ShellManagerContext {
  shell: ShellConnection;
}

export function useShellManagerContext() {
  return useOutletContext<ShellManagerContext>();
}
